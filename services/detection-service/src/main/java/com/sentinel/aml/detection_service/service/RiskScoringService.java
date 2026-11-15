package com.sentinel.aml.detection_service.service;

import com.sentinel.aml.detection_service.engine.RuleHit;

import java.math.BigDecimal;
import java.util.List;

public interface RiskScoringService {
    /** Blueprint Rule 7: score = min(100, (1 - prod(1 - w_i/100)) * 100 * m_customer). */
    BigDecimal calculateAggregateScore(List<RuleHit> hits, BigDecimal customerRiskMultiplier);
}
