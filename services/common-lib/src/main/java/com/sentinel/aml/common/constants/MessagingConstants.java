package com.sentinel.aml.common.constants;

public final class MessagingConstants {

    private MessagingConstants() {
        // Private constructor prevents instantiation
    }

    // ActiveMQ Artemis destinations (names follow the blueprint, section 3)
    /** ingestion-service -> detection-service (queue, point-to-point). */
    public static final String TRANSACTION_QUEUE = "sentinel.txn.ingested";
    /** detection-service -> alert-case-service (queue). */
    public static final String DETECTION_HITS_QUEUE = "sentinel.detection.hits";
    /** rule admin API -> every detection pod (topic, broadcast) - Phase 5 hot-reload. */
    public static final String RULES_CHANGED_TOPIC = "sentinel.rules.changed";
    /** alert-case-service -> every alert-case pod, which pushes it to its open browsers (topic) - Phase 7 live alerts. */
    public static final String ALERTS_CHANGED_TOPIC = "sentinel.alerts.changed";
    /** every service -> alert-case-service, which appends to the hash-chained audit log (queue) - Phase 7b. */
    public static final String AUDIT_QUEUE = "sentinel.audit.events";

    // JMS "_type" header values. Logical names, not Java class names, so producer and
    // consumer can live in different packages/services without ClassNotFound errors.
    public static final String TYPE_HEADER = "_type";
    public static final String TYPE_TRANSACTION_INGESTED = "TransactionIngested";
    public static final String TYPE_RULE_HITS_DETECTED = "RuleHitsDetected";
    public static final String TYPE_RULES_CHANGED = "RulesChanged";
    public static final String TYPE_ALERT_CHANGED = "AlertChanged";
    public static final String TYPE_AUDIT_EVENT = "AuditEvent";

    /**
     * Prefix of the per-queue dead-letter queues (P21). The broker moves a message that failed
     * max-delivery-attempts times to {@code DLQ.<queue>} (see infra/artemis/broker.properties).
     */
    public static final String DLQ_PREFIX = "DLQ.";
    /** Address all dead-letter queues hang off; used to build fully-qualified queue names. */
    public static final String DLQ_ADDRESS = "DLQ";
}
