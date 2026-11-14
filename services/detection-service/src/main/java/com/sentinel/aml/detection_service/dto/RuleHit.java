package com.sentinel.aml.detection_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RuleHit {
    private String ruleCode;
    private double ruleWeight;
    private String explanation;
}