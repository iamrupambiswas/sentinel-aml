package com.sentinel.aml.detection_service.service;

import com.sentinel.aml.detection_service.engine.RuleHit;
import com.sentinel.aml.detection_service.service.impl.RiskScoringServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScoringServiceImplTest {

    private final RiskScoringServiceImpl scoring = new RiskScoringServiceImpl();

    private static RuleHit hit(String weight) {
        return new RuleHit("R", new BigDecimal(weight), "", List.of(), "b");
    }

    @Test
    void blueprint_example_structuring_plus_round_on_medium_customer_is_about_94() {
        // (1 - 0.3 * 0.6) * 100 * 1.15 = 94.3
        assertThat(scoring.calculateAggregateScore(List.of(hit("70"), hit("40")), new BigDecimal("1.15")))
                .isEqualByComparingTo("94.30");
    }

    @Test
    void single_rule_low_risk_equals_its_weight() {
        assertThat(scoring.calculateAggregateScore(List.of(hit("30")), BigDecimal.ONE)).isEqualByComparingTo("30");
    }

    @Test
    void never_exceeds_100() {
        assertThat(scoring.calculateAggregateScore(List.of(hit("90"), hit("75"), hit("70")), new BigDecimal("1.3")))
                .isEqualByComparingTo("100");
    }

    @Test
    void no_hits_scores_zero() {
        assertThat(scoring.calculateAggregateScore(List.of(), BigDecimal.ONE)).isEqualByComparingTo("0");
    }
}
