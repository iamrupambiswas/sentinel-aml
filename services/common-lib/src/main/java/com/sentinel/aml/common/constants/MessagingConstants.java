package com.sentinel.aml.common.constants;

public final class MessagingConstants {

    private MessagingConstants() {
        // Private constructor prevents instantiation
    }

    // ActiveMQ Artemis Queues
    public static final String TRANSACTION_QUEUE = "aml.transactions.queue";
    public static final String ALERT_QUEUE = "aml.alerts.queue"; // We will use this in Phase 4
}