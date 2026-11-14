package com.sentinel.aml.detection_service.rule;

import com.sentinel.aml.detection_service.dto.RuleHit;
import com.sentinel.aml.detection_service.dto.TransactionEvent;
import com.sentinel.aml.detection_service.entity.AmlRule;

import java.util.Optional;

public interface DetectionRule {
    String getRuleCode();
    Optional<RuleHit> evaluate(TransactionEvent event, AmlRule ruleConfig);
}