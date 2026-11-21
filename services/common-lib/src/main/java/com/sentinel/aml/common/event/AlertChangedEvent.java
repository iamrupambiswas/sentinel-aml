package com.sentinel.aml.common.event;

import java.time.Instant;

/**
 * Broadcast on the {@code sentinel.alerts.changed} topic after an alert change is committed (Phase 7).
 * Every alert-case pod forwards it to the browsers connected to it over SSE, so the analyst queue
 * updates live. It only says WHAT changed; the console re-reads the alert through the API, so access
 * rules and the latest data always come from the normal endpoints.
 *
 * @param change    CREATED (first hit), UPDATED (another hit merged in) or STATUS_CHANGED (analyst action)
 * @param status    the alert's status after the change
 * @param changedAt when the change was committed
 */
public record AlertChangedEvent(
        String alertId,
        String accountId,
        String ruleCode,
        Change change,
        String status,
        Instant changedAt
) {
    public enum Change { CREATED, UPDATED, STATUS_CHANGED }
}
