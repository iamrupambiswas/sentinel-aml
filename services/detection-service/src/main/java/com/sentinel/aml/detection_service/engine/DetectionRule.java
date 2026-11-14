package com.sentinel.aml.detection_service.engine;

import java.util.Optional;

/** Strategy interface: one implementation per AML typology. */
public interface DetectionRule {
    String getRuleCode();

    Optional<RuleHit> evaluate(TransactionContext ctx, RuleConfig config);
}
