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

    // ---- out-of-order evaluation (ADR-005 §11) ----

    @Test
    void debit_evaluated_before_its_credit_fires_when_the_credit_arrives() {
        var credit = credit("5000000", T0);
        var debit = debit("4200000", plus(Duration.ofHours(30)));
        assertThat(rule.evaluate(debit, cfg)).isEmpty(); // no credit seen yet
        var hit = rule.evaluate(credit, cfg);
        assertThat(hit).isPresent();
        assertThat(hit.get().explanation()).contains("84.00%").contains("₹50,00,000");
        assertThat(hit.get().evidenceTxnIds()).containsExactly(credit.transactionId(), debit.transactionId());
    }

    @Test
    void late_credit_and_in_order_debit_report_the_same_window_bucket() {
        var inOrder = new RapidMovementRule(new InMemorySlidingWindowStore());
        inOrder.evaluate(credit("5000000", T0), cfg);
        var expected = inOrder.evaluate(debit("4200000", plus(Duration.ofHours(30))), cfg).orElseThrow();

        rule.evaluate(debit("4200000", plus(Duration.ofHours(30))), cfg);
        var late = rule.evaluate(credit("5000000", T0), cfg).orElseThrow();
        assertThat(late.windowBucket()).isEqualTo(expected.windowBucket());
    }

    @Test
    void money_leaving_before_it_arrives_is_not_rapid_movement() {
        rule.evaluate(debit("900000", T0), cfg);
        assertThat(rule.evaluate(credit("1000000", plus(Duration.ofHours(1))), cfg)).isEmpty();
    }

    @Test
    void late_credit_outside_the_debits_window_does_not_fire() {
        rule.evaluate(debit("900000", plus(Duration.ofHours(49))), cfg);
        assertThat(rule.evaluate(credit("1000000", T0), cfg)).isEmpty();
    }

    @Test
    void late_debit_counts_towards_a_later_debits_window() {
        rule.evaluate(credit("1000000", T0), cfg);
        assertThat(rule.evaluate(debit("450000", plus(Duration.ofHours(4))), cfg)).isEmpty();
        // happened before the 4h debit but evaluated after it: together 85% left within the window
        var hit = rule.evaluate(debit("400000", plus(Duration.ofHours(2))), cfg);
        assertThat(hit).isPresent();
        assertThat(hit.get().evidenceTxnIds()).hasSize(3);
    }
}
