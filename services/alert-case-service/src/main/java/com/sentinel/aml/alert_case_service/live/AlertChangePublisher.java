package com.sentinel.aml.alert_case_service.live;

import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.common.event.AlertChangedEvent;
import jakarta.jms.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends committed alert changes to the {@code sentinel.alerts.changed} topic (Phase 7 live alerts).
 *
 * AFTER_COMMIT, for the same reason as rule hot-reload: a browser told "new alert" must be able to load
 * it, so the row has to be committed first, and a rolled-back change must never be announced.
 * A topic, not a queue: every alert-case pod has its own SSE clients and each needs a copy.
 */
@Component
@Slf4j
public class AlertChangePublisher {

    private final JmsTemplate topicTemplate;

    public AlertChangePublisher(ConnectionFactory connectionFactory, MessageConverter converter) {
        // Private template, not a bean: a JmsTemplate bean would replace Boot's default (queue) template
        this.topicTemplate = new JmsTemplate(connectionFactory);
        this.topicTemplate.setPubSubDomain(true);
        this.topicTemplate.setMessageConverter(converter);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(AlertChangedEvent event) {
        try {
            topicTemplate.convertAndSend(MessagingConstants.ALERTS_CHANGED_TOPIC, event);
        } catch (RuntimeException e) {
            // The alert is already committed; a lost notification only means the screen updates on next refresh
            log.warn("Could not broadcast alert change {} {}: {}", event.change(), event.alertId(), e.getMessage());
        }
    }
}
