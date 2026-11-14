package com.sentinel.aml.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Published by detection-service to {@code sentinel.detection.hits} whenever at least one rule fires
 * for an account. alert-case-service consumes it, de-duplicates and creates/updates the alert.
 */
public record RuleHitsDetectedEvent(
        String eventId,
        String transactionId,
        String accountId,
        List<RuleHitPayload> hits,
        BigDecimal riskScore,
        String explanation,
        Instant txnTimestamp,
        Instant detectedAt
) {}
