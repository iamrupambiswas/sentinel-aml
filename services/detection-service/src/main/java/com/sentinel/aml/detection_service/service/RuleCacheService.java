package com.sentinel.aml.detection_service.service;

import com.sentinel.aml.detection_service.engine.RuleConfig;

import java.util.Map;

public interface RuleCacheService {
    void refreshRuleCache();

    /** Active rules keyed by rule code. Returned map is an immutable snapshot. */
    Map<String, RuleConfig> getActiveRules();
}
