package com.sentinel.aml.detection_service.engine;

import java.util.Optional;
import java.util.Set;

/** Strategy interface: one implementation per AML typology. */
public interface DetectionRule {
    String getRuleCode();

    Optional<RuleHit> evaluate(TransactionContext ctx, RuleConfig config);

    /**
     * Names of the JSON params this rule reads (P13). The rule admin API rejects any other key,
     * so a typo like "minAmountINR" fails loudly instead of being silently ignored.
     */
    default Set<String> supportedParams() {
        return Set.of();
    }
}
