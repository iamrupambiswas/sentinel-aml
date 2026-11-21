package com.sentinel.aml.common.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One entry for the tamper-evident audit log (Phase 7b): who did what to which record, and when.
 * Services send it to {@code sentinel.audit.events}; alert-case-service appends it to the hash chain.
 *
 * @param eventId    unique id; a redelivered event with the same id is stored once
 * @param occurredAt when the action was committed (microsecond precision, as stored in MySQL)
 * @param service    the service that performed the action
 * @param actor      Keycloak username, or "system" for automatic actions
 * @param action     what happened, e.g. RULE_CHANGED, PII_REVEALED, CASE_CLOSED
 * @param entityType e.g. RULE, CUSTOMER, ACCOUNT, ALERT, CASE, DEAD_LETTER
 * @param entityId   the record's id
 * @param details    small, non-sensitive facts (from/to values, reason). Never PII values.
 */
public record AuditEvent(
        String eventId,
        Instant occurredAt,
        String service,
        String actor,
        String action,
        String entityType,
        String entityId,
        Map<String, Object> details
) {
    public static AuditEvent of(String service, String actor, String action, String entityType, String entityId,
                                Map<String, ?> details) {
        return new AuditEvent(UUID.randomUUID().toString(), Instant.now().truncatedTo(ChronoUnit.MICROS), service,
                actor, action, entityType, entityId, details == null ? Map.of() : new TreeMap<>(details));
    }
}
