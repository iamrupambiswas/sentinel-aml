package com.sentinel.aml.detection_service.service.impl;

import com.sentinel.aml.detection_service.engine.RuleHit;
import com.sentinel.aml.detection_service.service.RiskScoringService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RiskScoringServiceImpl implements RiskScoringService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public BigDecimal calculateAggregateScore(List<RuleHit> hits, BigDecimal customerRiskMultiplier) {
        if (hits == null || hits.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        double nonMatch = 1.0;
        for (RuleHit hit : hits) {
            nonMatch *= (1.0 - hit.weight().doubleValue() / 100.0);
        }
        BigDecimal score = BigDecimal.valueOf((1.0 - nonMatch) * 100.0).multiply(customerRiskMultiplier);
        return score.min(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }
}
