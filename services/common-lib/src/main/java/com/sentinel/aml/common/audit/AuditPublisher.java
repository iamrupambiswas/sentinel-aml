package com.sentinel.aml.common.audit;

import com.sentinel.aml.common.constants.MessagingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Sends audit events to {@code sentinel.audit.events} (Phase 7b). Not a component: services that audit
 * declare it as a bean with their queue JmsTemplate, so common-lib does not force JMS on every service.
 *
 * Inside a transaction the send happens AFTER COMMIT: an action that rolls back is never audited as done.
 */
public class AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final String service;

    public AuditPublisher(JmsTemplate jmsTemplate, String service) {
        this.jmsTemplate = jmsTemplate;
        this.service = service;
    }

    /** Records an action by the current user (or "system" outside a request). */
    public void record(String action, String entityType, String entityId, Map<String, ?> details) {
        publish(AuditEvent.of(service, CurrentUser.name(), action, entityType, entityId, details));
    }

    public void publish(AuditEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(AuditEvent event) {
        try {
            jmsTemplate.convertAndSend(MessagingConstants.AUDIT_QUEUE, event);
        } catch (RuntimeException e) {
            // The action itself is committed. The log line keeps the event recoverable by hand.
            log.error("AUDIT EVENT NOT DELIVERED (re-send manually): {}", event, e);
        }
    }
}
