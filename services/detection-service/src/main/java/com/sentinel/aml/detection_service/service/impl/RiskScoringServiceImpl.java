package com.sentinel.aml.detection_service.service.impl;

import com.sentinel.aml.detection_service.dto.RuleHit;
import com.sentinel.aml.detection_service.service.RiskScoringService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskScoringServiceImpl implements RiskScoringService {

    @Override
    public double calculateAggregateScore(List<RuleHit> hits, double customerRiskMultiplier) {
        if (hits.isEmpty()) {
            return 0.0;
        }

        double nonMatchProbability = 1.0;
        for (RuleHit hit : hits) {
            nonMatchProbability *= (1.0 - (hit.getRuleWeight() / 100.0));
        }

        double aggregateScore = (1.0 - nonMatchProbability) * 100.0 * customerRiskMultiplier;

        return Math.min(100.0, aggregateScore);
    }
}