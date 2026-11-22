package com.sentinel.aml.detection_service.rules;

import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.common.event.RulesChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Broadcasts a committed rule change to every detection pod (P13).
 *
 * AFTER_COMMIT matters: if the message went out inside the transaction, a fast pod could reload
 * before the commit and read the OLD row. If publishing fails, the change is still committed and
 * every pod picks it up at the next scheduled refresh (sentinel.rules.refresh-ms) - a delay, never
 * a lost change.
 */
@Component
@Slf4j
public class RuleChangePublisher {

    private final JmsTemplate topicTemplate;

    public RuleChangePublisher(@Qualifier("topicJmsTemplate") JmsTemplate topicTemplate) {
        this.topicTemplate = topicTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRuleChanged(RuleChangedApplicationEvent e) {
        try {
            topicTemplate.convertAndSend(MessagingConstants.RULES_CHANGED_TOPIC,
                    new RulesChangedEvent(e.ruleCode(), e.version(), e.changedBy(), e.changedAt()));
            log.info("Broadcast rule change {} v{} on {}", e.ruleCode(), e.version(), MessagingConstants.RULES_CHANGED_TOPIC);
        } catch (Exception ex) {
            log.error("Could not broadcast rule change {} v{}; pods will pick it up at the next scheduled refresh",
                    e.ruleCode(), e.version(), ex);
        }
    }
}
