package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.RuleConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.sentinel.aml.detection_service.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class CtrThresholdRuleTest {

    private final CtrThresholdRule rule = new CtrThresholdRule();
    private final RuleConfig cfg = config("CTR_THRESHOLD", "30", "830000", 0, Map.of());

    @Test
    void fires_for_12_lakh_cash_deposit() {
        var hit = rule.evaluate(credit("1200000", T0), cfg);
        assertThat(hit).isPresent();
        assertThat(hit.get().explanation()).contains("₹12,00,000").contains("received");
        assertThat(hit.get().weight()).isEqualByComparingTo("30");
    }

    @Test
    void does_not_fire_below_threshold() {
        assertThat(rule.evaluate(credit("829999.99", T0), cfg)).isEmpty();
    }

    @Test
    void fires_exactly_at_threshold() {
        assertThat(rule.evaluate(credit("830000", T0), cfg)).isPresent();
    }

    @Test
    void uses_inr_amount_not_original_currency() {
        // USD 10,000 -> INR 8,35,000: original amount is "small", INR amount is over the threshold
        var e = event(ACCT, "EXT-Y", "10000", "835000", "USD", T0, null, null);
        assertThat(rule.evaluate(ctx(e, com.sentinel.aml.detection_service.engine.TransactionContext.Direction.DEBIT), cfg)).isPresent();
    }

    @Test
    void evidence_is_the_transaction_itself() {
        var c = credit("900000", T0);
        assertThat(rule.evaluate(c, cfg).orElseThrow().evidenceTxnIds()).containsExactly(c.transactionId());
    }
}
