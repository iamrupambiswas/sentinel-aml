package com.sentinel.aml.detection_service.service;

import com.sentinel.aml.common.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Customer-risk multiplier m for the score (LOW 1.0 / MEDIUM 1.15 / HIGH 1.3).
 * Reads aml:acct-risk:{accountId}, which customer-service keeps in sync (Phase 5): effective rating =
 * the higher of the customer's and the account's rating. Unknown accounts (e.g. other banks) = 1.0.
 */
@Component
@RequiredArgsConstructor
public class CustomerRiskLookup {

    private final StringRedisTemplate redis;

    public BigDecimal multiplierFor(String accountId) {
        String rating = redis.opsForValue().get(RedisKeys.accountRisk(accountId));
        if (rating == null) return BigDecimal.ONE;
        return switch (rating.toUpperCase()) {
            case "HIGH" -> new BigDecimal("1.30");
            case "MEDIUM" -> new BigDecimal("1.15");
            default -> BigDecimal.ONE;
        };
    }
}
