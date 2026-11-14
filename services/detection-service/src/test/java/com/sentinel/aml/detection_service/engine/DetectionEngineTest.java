package com.sentinel.aml.detection_service.engine;

import com.sentinel.aml.detection_service.engine.rules.CtrThresholdRule;
import com.sentinel.aml.detection_service.engine.rules.StructuringRule;
import com.sentinel.aml.detection_service.service.CustomerRiskLookup;
import com.sentinel.aml.detection_service.service.RuleCacheService;
import com.sentinel.aml.detection_service.service.impl.RiskScoringServiceImpl;
import com.sentinel.aml.detection_service.support.InMemorySlidingWindowStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.sentinel.aml.detection_service.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DetectionEngineTest {

    private DetectionEngine engine;
    private final RuleCacheService ruleCache = mock(RuleCacheService.class);

    @BeforeEach
    void setUp() {
        InMemorySlidingWindowStore store = new InMemorySlidingWindowStore();
        CustomerRiskLookup risk = mock(CustomerRiskLookup.class);
        when(risk.multiplierFor(anyString())).thenReturn(BigDecimal.ONE);
        when(ruleCache.getActiveRules()).thenReturn(Map.of(
                "CTR_THRESHOLD", config("CTR_THRESHOLD", "30", "830000", 0, Map.of()),
                "STRUCTURING", config("STRUCTURING", "70", "3", 1440, Map.of("minAmountInr", 751500, "maxAmountInr", 834999))));
        engine = new DetectionEngine(List.of(new CtrThresholdRule(), new StructuringRule(store)), ruleCache,
                new RiskScoringServiceImpl(), risk, store, Clock.fixed(T0, ZoneOffset.UTC), List.of("EXT-"));
    }

    @Test
    void deposits_from_different_external_senders_are_structuring_on_the_receiving_account() {
        // This is exactly docs/testData/sentinel_demo_data.csv: EXT-* -> ACC-STRUCT-01
        engine.evaluate(event("EXT-a", "ACC-STRUCT-01", "791000", T0));
        engine.evaluate(event("EXT-b", "ACC-STRUCT-01", "819000", plus(Duration.ofHours(1))));
        var out = engine.evaluate(event("EXT-c", "ACC-STRUCT-01", "815000", plus(Duration.ofHours(2))));
        assertThat(out).singleElement().satisfies(d -> {
            assertThat(d.accountId()).isEqualTo("ACC-STRUCT-01");
            assertThat(d.hits()).extracting(h -> h.ruleCode()).containsExactly("STRUCTURING");
            assertThat(d.riskScore()).isEqualByComparingTo("70");
        });
    }

    @Test
    void external_accounts_are_not_monitored() {
        assertThat(engine.evaluate(event("EXT-a", "EXT-b", "5000000", T0))).isEmpty();
    }

    @Test
    void internal_transfer_is_evaluated_for_both_sides() {
        var out = engine.evaluate(event("ACC-1", "ACC-2", "900000", T0));
        assertThat(out).extracting(d -> d.accountId()).containsExactlyInAnyOrder("ACC-1", "ACC-2");
    }

    @Test
    void disabled_rule_does_not_run() {
        when(ruleCache.getActiveRules()).thenReturn(Map.of());
        assertThat(engine.evaluate(event("EXT-a", "ACC-1", "5000000", T0))).isEmpty();
    }

    @Test
    void multiple_hits_stack_and_heaviest_rule_comes_first() {
        engine.evaluate(event("EXT-a", "ACC-9", "800000", T0));
        engine.evaluate(event("EXT-b", "ACC-9", "800000", plus(Duration.ofHours(1))));
        var out = engine.evaluate(event("EXT-c", "ACC-9", "830000", plus(Duration.ofHours(2)))); // CTR + STRUCTURING
        var d = out.get(0);
        assertThat(d.hits()).extracting(h -> h.ruleCode()).containsExactly("STRUCTURING", "CTR_THRESHOLD");
        assertThat(d.riskScore()).isEqualByComparingTo("79.00"); // 1 - 0.3*0.7
    }
}
