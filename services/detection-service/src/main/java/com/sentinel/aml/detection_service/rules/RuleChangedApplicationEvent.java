package com.sentinel.aml.detection_service.rules;

import java.time.Instant;

/** In-process event raised inside the update transaction; published to the broker only after commit. */
public record RuleChangedApplicationEvent(String ruleCode, int version, String changedBy, Instant changedAt) {}
