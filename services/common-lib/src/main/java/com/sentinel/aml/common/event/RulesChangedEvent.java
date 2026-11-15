package com.sentinel.aml.common.event;

import java.time.Instant;

/**
 * Broadcast on the {@code sentinel.rules.changed} topic after a rule change is committed (P13).
 * Every detection-service instance reloads its rule cache when it receives one, so a threshold
 * change takes effect on all pods in seconds instead of after the 5-minute scheduled refresh.
 *
 * @param ruleCode  the rule that changed
 * @param version   the rule's new version number (also the row in aml_rule_version)
 * @param changedBy who made the change (free text until Phase 6 adds real identities)
 * @param changedAt when the change was committed
 */
public record RulesChangedEvent(
        String ruleCode,
        int version,
        String changedBy,
        Instant changedAt
) {}
