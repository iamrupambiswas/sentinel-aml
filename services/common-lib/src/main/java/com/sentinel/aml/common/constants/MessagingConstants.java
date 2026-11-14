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

    // JMS "_type" header values. Logical names, not Java class names, so producer and
    // consumer can live in different packages/services without ClassNotFound errors.
    public static final String TYPE_HEADER = "_type";
    public static final String TYPE_TRANSACTION_INGESTED = "TransactionIngested";
    public static final String TYPE_RULE_HITS_DETECTED = "RuleHitsDetected";
}
