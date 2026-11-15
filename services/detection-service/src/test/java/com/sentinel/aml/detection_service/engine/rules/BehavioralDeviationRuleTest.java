package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.RuleConfig;
import com.sentinel.aml.detection_service.support.InMemorySlidingWindowStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static com.sentinel.aml.detection_service.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class BehavioralDeviationRuleTest {

    private InMemorySlidingWindowStore store;
    private BehavioralDeviationRule rule;
    private final RuleConfig cfg = config("BEHAVIORAL_DEVIATION", "55", "3", 1440, Map.of("minDailyInr", 50000));

    @BeforeEach
    void setUp() {
        store = new InMemorySlidingWindowStore();
        rule = new BehavioralDeviationRule(store);
    }

    @Test
    void blueprint_scenario_student_15k_average_moves_2_lakh() {
        store.putBaseline(ACCT, new BigDecimal("15000"), Duration.ofDays(1));
        var hit = rule.evaluate(debit("200000", T0), cfg);
        assertThat(hit).isPresent();
        assertThat(hit.get().explanation()).contains("13.3x").contains("₹15,000");
    }

    @Test
    void no_baseline_means_no_hit_but_day_is_still_recorded() {
        assertThat(rule.evaluate(debit("200000", T0), cfg)).isEmpty();
        assertThat(store.dailyTotals(ACCT, T0.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                T0.atZone(java.time.ZoneOffset.UTC).toLocalDate())).hasSize(1);
    }

    @Test
    void exactly_3x_does_not_fire_strictly_greater_required() {
        store.putBaseline(ACCT, new BigDecimal("20000"), Duration.ofDays(1));
        assertThat(rule.evaluate(debit("60000", T0), cfg)).isEmpty();
        assertThat(rule.evaluate(debit("1", plus(Duration.ofMinutes(1))), cfg)).isPresent();
    }

    @Test
    void same_day_transactions_accumulate() {
        store.putBaseline(ACCT, new BigDecimal("30000"), Duration.ofDays(1));
        assertThat(rule.evaluate(debit("50000", T0), cfg)).isEmpty();
        assertThat(rule.evaluate(credit("50000", plus(Duration.ofHours(2))), cfg)).isPresent();
    }

    @Test
    void below_min_daily_value_is_ignored_even_if_ratio_is_high() {
        store.putBaseline(ACCT, new BigDecimal("1000"), Duration.ofDays(1));
        assertThat(rule.evaluate(debit("40000", T0), cfg)).isEmpty();
    }
}
