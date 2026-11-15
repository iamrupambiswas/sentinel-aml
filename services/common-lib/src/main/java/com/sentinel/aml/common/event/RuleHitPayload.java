package com.sentinel.aml.common.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * One rule that fired for one account.
 *
 * @param windowBucket   stable id of the time window the hit belongs to; alert-case-service
 *                       builds the de-dup key as {@code accountId:ruleCode:windowBucket} (P14)
 * @param evidenceTxnIds the transactions that make up the pattern (P12)
 */
public record RuleHitPayload(
        String ruleCode,
        BigDecimal weight,
        String explanation,
        List<String> evidenceTxnIds,
        String windowBucket
) {}
