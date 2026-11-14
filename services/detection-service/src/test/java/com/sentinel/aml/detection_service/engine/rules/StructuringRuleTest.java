package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.RuleConfig;
import com.sentinel.aml.detection_service.support.InMemorySlidingWindowStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static com.sentinel.aml.detection_service.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class StructuringRuleTest {

    private InMemorySlidingWindowStore store;
    private StructuringRule rule;
    private final RuleConfig cfg = config("STRUCTURING", "70", "3", 1440,
            Map.of("minAmountInr", 751500, "maxAmountInr", 834999));

    @BeforeEach
    void setUp() {
        store = new InMemorySlidingWindowStore();
        rule = new StructuringRule(store);
    }

    @Test
    void fires_on_third_deposit_in_band_within_24h() {
        assertThat(rule.evaluate(credit("760000", T0), cfg)).isEmpty();
        assertThat(rule.evaluate(credit("790000", plus(Duration.ofHours(5))), cfg)).isEmpty();
        var hit = rule.evaluate(credit("830000", plus(Duration.ofHours(10))), cfg);
        assertThat(hit).isPresent();
        assertThat(hit.get().evidenceTxnIds()).hasSize(3);
        assertThat(hit.get().explanation()).contains("3 transactions").contains("10 h 0 m");
    }

    @Test
    void blueprint_scenario_four_deposits_in_20_hours() {
        rule.evaluate(credit("760000", T0), cfg);
        rule.evaluate(credit("780000", plus(Duration.ofHours(6))), cfg);
        rule.evaluate(credit("800000", plus(Duration.ofHours(13))), cfg);
        var hit = rule.evaluate(credit("830000", plus(Duration.ofHours(20))), cfg);
        assertThat(hit.orElseThrow().evidenceTxnIds()).hasSize(4);
    }

    @Test
    void does_not_fire_with_only_two_in_band() {
        rule.evaluate(credit("760000", T0), cfg);
        assertThat(rule.evaluate(credit("790000", plus(Duration.ofHours(1))), cfg)).isEmpty();
    }

    @Test
    void amounts_outside_band_are_ignored() {
        rule.evaluate(credit("760000", T0), cfg);
        rule.evaluate(credit("790000", plus(Duration.ofHours(1))), cfg);
        assertThat(rule.evaluate(credit("900000", plus(Duration.ofHours(2))), cfg)).isEmpty(); // above band
        assertThat(rule.evaluate(credit("500000", plus(Duration.ofHours(3))), cfg)).isEmpty(); // below band
    }

    @Test
    void band_edges_are_inclusive() {
        rule.evaluate(credit("751500", T0), cfg);
        rule.evaluate(credit("834999", plus(Duration.ofHours(1))), cfg);
        assertThat(rule.evaluate(credit("800000", plus(Duration.ofHours(2))), cfg)).isPresent();
    }

    @Test
    void boundary_23h59m_is_inside_window() {
        rule.evaluate(credit("760000", T0), cfg);
        rule.evaluate(credit("770000", plus(Duration.ofHours(12))), cfg);
        assertThat(rule.evaluate(credit("780000", plus(Duration.ofHours(23).plusMinutes(59))), cfg)).isPresent();
    }

    @Test
    void first_deposit_drops_out_after_24h() {
        rule.evaluate(credit("760000", T0), cfg);
        rule.evaluate(credit("770000", plus(Duration.ofHours(12))), cfg);
        assertThat(rule.evaluate(credit("780000", plus(Duration.ofHours(24).plusMinutes(1))), cfg)).isEmpty();
    }

    @Test
    void redelivered_transaction_is_not_double_counted() {
        var first = credit("760000", T0);
        rule.evaluate(first, cfg);
        rule.evaluate(first, cfg); // same txn again (JMS redelivery)
        assertThat(rule.evaluate(credit("770000", plus(Duration.ofHours(1))), cfg)).isEmpty();
    }
}
