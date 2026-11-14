package com.sentinel.aml.detection_service.rule.impl;

import com.sentinel.aml.detection_service.dto.RuleHit;
import com.sentinel.aml.detection_service.dto.TransactionEvent;
import com.sentinel.aml.detection_service.entity.AmlRule;
import com.sentinel.aml.detection_service.rule.DetectionRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class RapidMovementRule implements DetectionRule {

    private static final String RULE_CODE = "RAPID_MOVEMENT";
    private final StringRedisTemplate redisTemplate;

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionEvent event, AmlRule ruleConfig) {
        String accountId = event.getSourceAccountId();
        String direction = event.getDirection();
        BigDecimal amount = event.getAmount();

        long currentTime = System.currentTimeMillis();
        long windowMillis = ruleConfig.getWindowMinutes() * 60 * 1000L;
        long windowStart = currentTime - windowMillis;

        String creditKey = "aml:win:cr:" + accountId;
        String debitKey = "aml:win:dr:" + accountId;

        // 1. Prune aged-out transactions from both windows
        redisTemplate.opsForZSet().removeRangeByScore(creditKey, 0, windowStart - 1);
        redisTemplate.opsForZSet().removeRangeByScore(debitKey, 0, windowStart - 1);

        // 2. Add current transaction to the corresponding window
        String member = event.transactionId() + ":" + amount.toPlainString();
        if ("CREDIT".equals(direction)) {
            redisTemplate.opsForZSet().add(creditKey, member, currentTime);
        } else if ("DEBIT".equals(direction)) {
            redisTemplate.opsForZSet().add(debitKey, member, currentTime);
        } else {
            return Optional.empty(); // Ignore unknown directions
        }

        // 3. Set TTLs to auto-expire idle windows
        redisTemplate.expire(creditKey, Duration.ofMillis(windowMillis));
        redisTemplate.expire(debitKey, Duration.ofMillis(windowMillis));

        // We only trigger layering alerts when funds are moving OUT (Debit)
        if ("CREDIT".equals(direction)) {
            return Optional.empty();
        }

        // 4. Calculate the total sums for the active window
        BigDecimal totalCredits = calculateWindowSum(creditKey);
        BigDecimal totalDebits = calculateWindowSum(debitKey);

        // 5. Evaluate the ratio if there are baseline credits
        if (totalCredits.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentageOut = totalDebits.divide(totalCredits, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

            if (percentageOut.compareTo(ruleConfig.getThresholdAmount()) >= 0) {
                String explanation = String.format(
                        "Account %s exhibited rapid movement of funds. Within %d minutes, total credits were $%s and total debits reached $%s (%.2f%% moved out).",
                        accountId,
                        ruleConfig.getWindowMinutes(),
                        totalCredits.toPlainString(),
                        totalDebits.toPlainString(),
                        percentageOut.doubleValue()
                );

                return Optional.of(RuleHit.builder()
                        .ruleCode(RULE_CODE)
                        .ruleWeight(ruleConfig.getWeight())
                        .explanation(explanation)
                        .build());
            }
        }

        return Optional.empty();
    }

    private BigDecimal calculateWindowSum(String redisKey) {
        Set<String> activeTransactions = redisTemplate.opsForZSet().range(redisKey, 0, -1);
        BigDecimal total = BigDecimal.ZERO;

        if (activeTransactions != null) {
            for (String tx : activeTransactions) {
                String[] parts = tx.split(":");
                if (parts.length == 2) {
                    total = total.add(new BigDecimal(parts[1]));
                }
            }
        }
        return total;
    }
}