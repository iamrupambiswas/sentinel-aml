package com.sentinel.aml.ingestion_service.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.ingestion_service.entity.OutboxEvent;
import com.sentinel.aml.ingestion_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxEventRepository outboxRepository;
    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    // Runs continuously every 2 seconds
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Found {} pending outbox events. Publishing to broker...", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Simplified Jackson mapping - bypasses the TypeReference generic parsing
                @SuppressWarnings("unchecked")
                Map<String, Object> payloadMap = objectMapper.readValue(event.getPayload(), Map.class);

                jmsTemplate.convertAndSend(MessagingConstants.TRANSACTION_QUEUE, payloadMap);

                event.setStatus("PUBLISHED");
                outboxRepository.save(event);

                log.info("Successfully published outbox event {} for transaction {}", event.getId(), event.getAggregateId());

            } catch (Exception e) {
                log.error("Failed to publish outbox event {}. Will retry on next poll.", event.getId(), e);
            }
        }
    }
}