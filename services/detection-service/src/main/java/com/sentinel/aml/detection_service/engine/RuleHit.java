package com.sentinel.aml.detection_service.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param windowBucket stable id of the window, used later for the de-dup key accountId:ruleCode:windowBucket
 */
public record RuleHit(String ruleCode, BigDecimal weight, String explanation,
                      List<String> evidenceTxnIds, String windowBucket) {}
