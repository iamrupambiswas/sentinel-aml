package com.sentinel.aml.ingestion_service.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.common.event.TransactionIngestedEvent;
import com.sentinel.aml.ingestion_service.entity.OutboxEvent;
import com.sentinel.aml.ingestion_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Relays outbox rows to ActiveMQ. Delivery is at-least-once: if the app dies after sending but
 * before the status update commits, the row is re-sent next tick. Consumers are idempotent on
 * eventId, so that is safe.
 * <p>
 * Poll interval matters for the < 1 s end-to-end target: a 2 s poll alone would blow the budget.
 * NOTE (Phase 8, multiple pods): two pollers would publish the same rows; switch the query to
 * SELECT ... FOR UPDATE SKIP LOCKED or use ShedLock before scaling ingestion horizontally.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxEventRepository outboxRepository;
    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Value("${sentinel.outbox.page-size:500}")
    private int pageSize;

    @Scheduled(fixedDelayString = "${sentinel.outbox.poll-interval-ms:200}")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pending = outboxRepository.findPage("PENDING", PageRequest.of(0, pageSize));
        if (pending.isEmpty()) {
            return;
        }

        int published = 0;
        for (OutboxEvent event : pending) {
            try {
                TransactionIngestedEvent payload = objectMapper.readValue(event.getPayload(), TransactionIngestedEvent.class);
                jmsTemplate.convertAndSend(MessagingConstants.TRANSACTION_QUEUE, payload);
                event.setStatus("PUBLISHED");
                event.setPublishedAt(LocalDateTime.now(clock));
                published++;
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // A row that can never be parsed would block nothing (we continue) but would be retried forever.
                log.error("Outbox event {} has an unreadable payload; marking FAILED", event.getId(), e);
                event.setStatus("FAILED");
            } catch (Exception e) {
                log.error("Broker publish failed for outbox event {}; stopping this tick and retrying later", event.getId(), e);
                break; // broker is probably down - keep ordering, try again next tick
            }
        }
        log.debug("Published {}/{} outbox events", published, pending.size());
    }
}
