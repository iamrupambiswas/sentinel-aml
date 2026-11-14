package com.sentinel.aml.detection_service.service;

import com.sentinel.aml.detection_service.entity.AmlRule;
import java.util.List;

public interface RuleCacheService {
    void refreshRuleCache();
    List<AmlRule> getActiveRules();
}