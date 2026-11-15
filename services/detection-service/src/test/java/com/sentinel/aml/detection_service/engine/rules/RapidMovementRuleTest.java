package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.RuleConfig;
import com.sentinel.aml.detection_service.support.InMemorySlidingWindowStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static com.sentinel.aml.detection_service.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class RapidMovementRuleTest {

    private RapidMovementRule rule;
    private final RuleConfig cfg = config("RAPID_MOVEMENT", "75", "80", 2880, Map.of("minCreditInr", 100000));

    @BeforeEach
    void setUp() {
        rule = new RapidMovementRule(new InMemorySlidingWindowStore());
    }

    @Test
    void blueprint_scenario_50L_in_42L_out_within_30h() {
        assertThat(rule.evaluate(credit("5000000", T0), cfg)).isEmpty();
        var hit = rule.evaluate(debit("4200000", plus(Duration.ofHours(30))), cfg);
        assertThat(hit).isPresent();
        assertThat(hit.get().explanation()).contains("84.00%").contains("₹50,00,000");
        assertThat(hit.get().evidenceTxnIds()).hasSize(2);
    }

    @Test
    void exactly_80_percent_fires() {
        rule.evaluate(credit("1000000", T0), cfg);
        assertThat(rule.evaluate(debit("800000", plus(Duration.ofHours(1))), cfg)).isPresent();
    }

    @Test
    void below_80_percent_does_not_fire() {
        rule.evaluate(credit("1000000", T0), cfg);
        assertThat(rule.evaluate(debit("799999", plus(Duration.ofHours(1))), cfg)).isEmpty();
    }

    @Test
    void multiple_debits_accumulate() {
        rule.evaluate(credit("1000000", T0), cfg);
        assertThat(rule.evaluate(debit("400000", plus(Duration.ofHours(2))), cfg)).isEmpty();
        assertThat(rule.evaluate(debit("450000", plus(Duration.ofHours(4))), cfg)).isPresent();
    }

    @Test
    void credit_older_than_48h_does_not_count() {
        rule.evaluate(credit("1000000", T0), cfg);
        assertThat(rule.evaluate(debit("900000", plus(Duration.ofHours(48).plusMinutes(1))), cfg)).isEmpty();
    }

    @Test
    void credits_alone_never_fire() {
        assertThat(rule.evaluate(credit("5000000", T0), cfg)).isEmpty();
    }

    @Test
    void ignores_tiny_accounts_below_min_credit() {
        rule.evaluate(credit("50000", T0), cfg);
        assertThat(rule.evaluate(debit("50000", plus(Duration.ofHours(1))), cfg)).isEmpty();
    }
}
