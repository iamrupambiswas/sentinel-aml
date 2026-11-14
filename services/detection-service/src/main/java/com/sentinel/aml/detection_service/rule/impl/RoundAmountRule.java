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
import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoundAmountRule implements DetectionRule {

    private static final String RULE_CODE = "ROUND_AMOUNT";
    private final StringRedisTemplate redisTemplate;

    // Configured for $1,000 increments
    private static final BigDecimal ROUND_MODULO = new BigDecimal("1000.00");

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionEvent event, AmlRule ruleConfig) {
        BigDecimal amount = event.getAmount();

        // 1. Check if the amount is a multiple of $1,000 and sufficiently large
        if (amount.compareTo(ROUND_MODULO) < 0 || amount.remainder(ROUND_MODULO).compareTo(BigDecimal.ZERO) != 0) {
            return Optional.empty(); // Not a round amount, exit early
        }

        String accountId = event.getSourceAccountId();
        String redisKey = "aml:win:round:" + accountId;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - (ruleConfig.getWindowMinutes() * 60 * 1000L);

        // 2. Prune aged-out round transactions (ZREMRANGEBYSCORE)
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart - 1);

        // 3. Add the current round transaction
        String member = event.transactionId() + ":" + amount.toPlainString();
        redisTemplate.opsForZSet().add(redisKey, member, currentTime);

        // 4. Set TTL
        redisTemplate.expire(redisKey, Duration.ofMinutes(ruleConfig.getWindowMinutes()));

        // 5. Evaluate count threshold
        Long roundTxnCount = redisTemplate.opsForZSet().zCard(redisKey);
        int requiredCount = ruleConfig.getThresholdAmount().intValue();

        if (roundTxnCount != null && roundTxnCount >= requiredCount) {
            String explanation = String.format(
                    "Account %s executed %d round-number transactions (multiples of $%s) within a %d-minute window.",
                    accountId,
                    roundTxnCount,
                    ROUND_MODULO.toPlainString(),
                    ruleConfig.getWindowMinutes()
            );

            return Optional.of(RuleHit.builder()
                    .ruleCode(RULE_CODE)
                    .ruleWeight(ruleConfig.getWeight())
                    .explanation(explanation)
                    .build());
        }

        return Optional.empty();
    }
}