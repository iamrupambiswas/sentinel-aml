package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.RuleConfig;
import com.sentinel.aml.detection_service.support.InMemorySlidingWindowStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import com.sentinel.aml.detection_service.engine.RuleHit;
import com.sentinel.aml.detection_service.engine.TransactionContext;
import java.util.List;
import java.util.ArrayList;
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

    // ---- out-of-order evaluation (ADR-005 §11): several consumer threads, one account ----

    @Test
    void four_deposits_evaluated_newest_first_still_fire() {
        var d1 = credit("760000", T0);
        var d2 = credit("780000", plus(Duration.ofHours(6)));
        var d3 = credit("800000", plus(Duration.ofHours(13)));
        var d4 = credit("830000", plus(Duration.ofHours(20)));
        assertThat(rule.evaluate(d4, cfg)).isEmpty();
        assertThat(rule.evaluate(d3, cfg)).isEmpty();
        assertThat(rule.evaluate(d2, cfg).orElseThrow().evidenceTxnIds()).hasSize(3);
        var last = rule.evaluate(d1, cfg).orElseThrow();
        assertThat(last.evidenceTxnIds()).containsExactly(d1.transactionId(), d2.transactionId(),
                d3.transactionId(), d4.transactionId());
        assertThat(last.explanation()).contains("20 h 0 m");
    }

    @Test
    void every_evaluation_order_gives_the_same_two_hits_as_in_order() {
        List<List<Integer>> perms = permutations(List.of(0, 1, 2, 3));
        for (List<Integer> order : perms) {
            rule = new StructuringRule(new InMemorySlidingWindowStore());
            List<TransactionContext> deposits = List.of(credit("760000", T0), credit("780000", plus(Duration.ofMinutes(1))),
                    credit("800000", plus(Duration.ofMinutes(2))), credit("830000", plus(Duration.ofMinutes(3))));
            List<RuleHit> hits = new ArrayList<>();
            for (int i : order) rule.evaluate(deposits.get(i), cfg).ifPresent(hits::add);
            assertThat(hits).as("order %s", order).hasSize(2);
            assertThat(hits.get(1).evidenceTxnIds()).as("order %s", order).hasSize(4);
        }
    }

    @Test
    void late_deposit_sees_entries_a_newer_arrival_would_have_trimmed() {
        rule.evaluate(credit("760000", T0), cfg);
        rule.evaluate(credit("770000", plus(Duration.ofHours(12))), cfg);
        assertThat(rule.evaluate(credit("775000", plus(Duration.ofHours(37))), cfg)).isEmpty(); // > 24h after T0
        // arrives last, but happened at T0+6h: T0, T0+6h, T0+12h are 3 deposits within 24h
        assertThat(rule.evaluate(credit("780000", plus(Duration.ofHours(6))), cfg)).isPresent();
    }

    @Test
    void out_of_order_deposits_spread_over_more_than_the_window_do_not_fire() {
        var a = credit("760000", T0);
        var b = credit("770000", plus(Duration.ofHours(20)));
        var c = credit("780000", plus(Duration.ofHours(40)));
        assertThat(rule.evaluate(c, cfg)).isEmpty();
        assertThat(rule.evaluate(a, cfg)).isEmpty();
        assertThat(rule.evaluate(b, cfg)).isEmpty(); // any 24h window holds at most 2 of them
    }

    private static List<List<Integer>> permutations(List<Integer> items) {
        if (items.isEmpty()) return List.of(List.of());
        List<List<Integer>> out = new ArrayList<>();
        for (Integer head : items) {
            List<Integer> rest = new ArrayList<>(items);
            rest.remove(head);
            for (List<Integer> tail : permutations(rest)) {
                List<Integer> p = new ArrayList<>();
                p.add(head);
                p.addAll(tail);
                out.add(p);
            }
        }
        return out;
    }
}
