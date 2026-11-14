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
public class StructuringRule implements DetectionRule {

    private static final String RULE_CODE = "STRUCTURING";
    private final StringRedisTemplate redisTemplate;

    // Standard structuring boundaries based on a $10,000 reporting threshold
    private static final BigDecimal LOWER_BOUND = new BigDecimal("9000.00");
    private static final BigDecimal UPPER_BOUND = new BigDecimal("9999.99");

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionEvent event, AmlRule ruleConfig) {
        BigDecimal amount = event.getAmount();

        // 1. Check if the current transaction falls within the structuring range
        if (amount.compareTo(LOWER_BOUND) < 0 || amount.compareTo(UPPER_BOUND) > 0) {
            return Optional.empty(); // Not a structuring attempt, exit early
        }

        String accountId = event.getSourceAccountId();
        String redisKey = "aml:win:struct:" + accountId;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - (ruleConfig.getWindowMinutes() * 60 * 1000L);

        // 2. Prune aged-out transactions (ZREMRANGEBYSCORE)
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart - 1);

        // 3. Add the current transaction to the window
        String member = event.transactionId() + ":" + amount.toPlainString();
        redisTemplate.opsForZSet().add(redisKey, member, currentTime);

        // 4. Set TTL to free up RAM if the account goes dormant
        redisTemplate.expire(redisKey, Duration.ofMinutes(ruleConfig.getWindowMinutes()));

        // 5. Evaluate the rule (Count >= 3)
        Long transactionCount = redisTemplate.opsForZSet().zCard(redisKey);

        // The thresholdAmount in the DB for this rule represents the count threshold (e.g., 3.00)
        int requiredCount = ruleConfig.getThresholdAmount().intValue();

        if (transactionCount != null && transactionCount >= requiredCount) {
            String explanation = String.format(
                    "Account %s executed %d transactions between $%s and $%s within a %d-minute window, indicating potential structuring.",
                    accountId,
                    transactionCount,
                    LOWER_BOUND.toPlainString(),
                    UPPER_BOUND.toPlainString(),
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