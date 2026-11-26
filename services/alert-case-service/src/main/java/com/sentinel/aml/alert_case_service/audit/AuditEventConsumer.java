package com.sentinel.aml.alert_case_service.audit;

import com.sentinel.aml.common.audit.AuditEvent;
import com.sentinel.aml.common.constants.MessagingConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Audit events from the other services (Phase 7b). Transacted session: a failure rolls back and the broker
 * redelivers (then DLQ.sentinel.audit.events). append() ignores an event id it already has, so a replay is safe.
 *
 * Deliberately NOT nested in a @RestController: LoggingAspect wraps everything "within" controllers and needs
 * an HTTP request, which a JMS thread doesn't have.
 */
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private final AuditLog auditLog;

    @JmsListener(destination = MessagingConstants.AUDIT_QUEUE, concurrency = "1-2")
    public void onAuditEvent(AuditEvent event) {
        auditLog.append(event);
    }
}
