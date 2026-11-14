package com.sentinel.aml.detection_service.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.common.event.RuleHitPayload;
import com.sentinel.aml.common.event.RuleHitsDetectedEvent;
import com.sentinel.aml.common.event.TransactionIngestedEvent;
import com.sentinel.aml.detection_service.engine.DetectionEngine;
import com.sentinel.aml.detection_service.entity.AmlAlert;
import com.sentinel.aml.detection_service.entity.ProcessedEvent;
import com.sentinel.aml.detection_service.repository.AmlAlertRepository;
import com.sentinel.aml.detection_service.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The ONLY listener on sentinel.txn.ingested. (There used to be a second, logging-only
 * listener on the same queue; queues are point-to-point, so it silently stole ~half the traffic.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final AmlAlertRepository amlAlertRepository;
    private final DetectionEngine detectionEngine;
    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @JmsListener(destination = MessagingConstants.TRANSACTION_QUEUE, concurrency = "${sentinel.detection.concurrency:4-8}")
    @Transactional
    public void consumeTransaction(TransactionIngestedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);

        // Idempotent consumer: at-least-once delivery means duplicates WILL arrive
        if (processedEventRepository.existsById(event.transactionId())) {
            log.info("Duplicate delivery ignored for transaction {}", event.transactionId());
            return;
        }

        List<RuleHitsDetectedEvent> detections = detectionEngine.evaluate(event);
        for (RuleHitsDetectedEvent d : detections) {
            amlAlertRepository.save(toEntity(d, event));
            jmsTemplate.convertAndSend(MessagingConstants.DETECTION_HITS_QUEUE, d);
            for (RuleHitPayload h : d.hits()) {
                meterRegistry.counter("sentinel_rule_hits_total", "rule", h.ruleCode()).increment();
            }
            log.warn("AML hit on account {} (txn {}): rules={} score={}", d.accountId(), d.transactionId(),
                    d.hits().stream().map(RuleHitPayload::ruleCode).toList(), d.riskScore());
        }

        processedEventRepository.save(ProcessedEvent.builder()
                .transactionId(event.transactionId())
                .processedAt(LocalDateTime.now(clock))
                .build());

        sample.stop(meterRegistry.timer("sentinel_detection_latency"));
        if (event.ingestedAt() != null) {
            meterRegistry.timer("sentinel_ingest_to_detection_latency")
                    .record(Duration.between(event.ingestedAt(), clock.instant()));
        }
    }

    private AmlAlert toEntity(RuleHitsDetectedEvent d, TransactionIngestedEvent txn) {
        return AmlAlert.builder()
                .id(d.eventId())
                .transactionId(d.transactionId())
                .accountId(d.accountId())
                .ruleTriggered(d.hits().get(0).ruleCode())
                .ruleCodes(d.hits().stream().map(RuleHitPayload::ruleCode).collect(Collectors.joining(",")))
                .alertAmount(txn.amountInr())
                .riskScore(d.riskScore())
                .explanation(d.explanation())
                .evidence(toJson(d.hits()))
                .direction(d.accountId().equals(txn.destinationAccountId()) ? "CREDIT" : "DEBIT")
                .txnTimestamp(LocalDateTime.ofInstant(txn.txnTimestamp(), ZoneOffset.UTC))
                .status("NEW")
                .createdAt(LocalDateTime.ofInstant(d.detectedAt(), ZoneOffset.UTC))
                .build();
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
