package com.sentinel.aml.detection_service.service;

import com.sentinel.aml.detection_service.dto.RuleHit;
import java.util.List;

public interface RiskScoringService {
    double calculateAggregateScore(List<RuleHit> hits, double customerRiskMultiplier);
}