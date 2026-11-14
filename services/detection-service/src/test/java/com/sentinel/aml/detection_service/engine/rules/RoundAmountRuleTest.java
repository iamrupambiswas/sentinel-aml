package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.RuleConfig;
import com.sentinel.aml.detection_service.engine.TransactionContext.Direction;
import com.sentinel.aml.detection_service.support.InMemorySlidingWindowStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static com.sentinel.aml.detection_service.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class RoundAmountRuleTest {

    private RoundAmountRule rule;
    private final RuleConfig cfg = config("ROUND_AMOUNT", "40", "5", 10080,
            Map.of("modulo", 1000, "minAmountInr", 100000, "nearThresholdPct", 2, "ctrThresholdInr", 830000));

    @BeforeEach
    void setUp() {
        rule = new RoundAmountRule(new InMemorySlidingWindowStore());
    }

    @Test
    void blueprint_scenario_six_transfers_of_5_lakh_in_a_week() {
        for (int i = 0; i < 4; i++) {
            assertThat(rule.evaluate(debit("500000", plus(Duration.ofDays(i))), cfg)).isEmpty();
        }
        assertThat(rule.evaluate(debit("500000", plus(Duration.ofDays(4))), cfg)).isPresent();
        var sixth = rule.evaluate(debit("500000", plus(Duration.ofDays(5))), cfg);
        assertThat(sixth.orElseThrow().evidenceTxnIds()).hasSize(6);
    }

    @Test
    void non_round_amounts_are_ignored() {
        for (int i = 0; i < 6; i++) {
            assertThat(rule.evaluate(debit("500123", plus(Duration.ofDays(i))), cfg)).isEmpty();
        }
    }

    @Test
    void small_round_amounts_like_rent_are_ignored() {
        for (int i = 0; i < 6; i++) {
            assertThat(rule.evaluate(debit("25000", plus(Duration.ofDays(i))), cfg)).isEmpty();
        }
    }

    @Test
    void round_check_uses_original_currency() {
        // USD 6,000 is round; its INR value (5,01,000) is irrelevant for roundness
        var e = event(ACCT, "EXT-Y", "6000", "501000", "USD", T0, null, null);
        assertThat(RoundAmountRule.isSuspiciouslyRound(ctx(e, Direction.DEBIT), cfg)).isTrue();
    }

    @Test
    void just_below_ctr_threshold_counts_as_suspicious() {
        // 8,20,123 is within 2% below 8,30,000 even though it isn't a multiple of 1,000
        assertThat(RoundAmountRule.isSuspiciouslyRound(debit("820123", T0), cfg)).isTrue();
        assertThat(RoundAmountRule.isSuspiciouslyRound(debit("810123", T0), cfg)).isFalse();
    }

    @Test
    void transactions_older_than_7_days_drop_out() {
        for (int i = 0; i < 4; i++) rule.evaluate(debit("500000", plus(Duration.ofDays(i))), cfg);
        assertThat(rule.evaluate(debit("500000", plus(Duration.ofDays(7).plusMinutes(1))), cfg)).isEmpty();
    }
}
