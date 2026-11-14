package com.sentinel.aml.detection_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Customer-risk multiplier m for the score (LOW 1.0 / MEDIUM 1.15 / HIGH 1.3).
 * Reads aml:acct-risk:{accountId}. Nothing writes that key yet: Phase 5 task is to have
 * customer-service publish account -> risk rating into Redis. Until then everything is LOW.
 */
@Component
@RequiredArgsConstructor
public class CustomerRiskLookup {

    private final StringRedisTemplate redis;

    public BigDecimal multiplierFor(String accountId) {
        String rating = redis.opsForValue().get("aml:acct-risk:" + accountId);
        if (rating == null) return BigDecimal.ONE;
        return switch (rating.toUpperCase()) {
            case "HIGH" -> new BigDecimal("1.30");
            case "MEDIUM" -> new BigDecimal("1.15");
            default -> BigDecimal.ONE;
        };
    }
}
