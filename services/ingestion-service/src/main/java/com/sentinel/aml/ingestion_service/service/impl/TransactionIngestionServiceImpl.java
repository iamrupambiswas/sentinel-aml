package com.sentinel.aml.ingestion_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.ingestion_service.dto.TransactionRequest;
import com.sentinel.aml.ingestion_service.entity.OutboxEvent;
import com.sentinel.aml.ingestion_service.entity.Transaction;
import com.sentinel.aml.ingestion_service.repository.OutboxEventRepository;
import com.sentinel.aml.ingestion_service.repository.TransactionRepository;
import com.sentinel.aml.ingestion_service.service.FxRateService;
import com.sentinel.aml.ingestion_service.service.TransactionIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionIngestionServiceImpl implements TransactionIngestionService {

    private final OutboxEventRepository outboxRepository;
    private final TransactionRepository transactionRepository;
    private final FxRateService fxRateService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public String ingestTransaction(TransactionRequest request, String idempotencyKey) {
        String redisKey = "idempotency:txn:" + idempotencyKey;

        // 1. Check Idempotency using Redis SETNX (Set if Not Exists)
        Boolean isNewRequest = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isNewRequest)) {
            log.warn("Duplicate request detected for Idempotency-Key: {}", idempotencyKey);
            throw new IllegalArgumentException("Duplicate transaction request. Idempotency key already processed.");
        }

        String transactionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        // 2. Calculate Normalized INR Amount
        BigDecimal amountInr = fxRateService.normalizeToInr(request.amount(), request.currency());

        // 3. Save exactly as required by the Blueprint DB Schema
        Transaction txn = Transaction.builder()
                .id(transactionId)
                .externalRef(idempotencyKey)
                .sourceAccountId(request.sourceAccountId())
                .destinationAccountId(request.destinationAccountId())
                .amount(request.amount())
                .currency(request.currency())
                .amountInr(amountInr)
                .type(request.type())
                .txnTimestamp(now)
                .build();

        transactionRepository.save(txn);
        log.info("Saved transaction {} to database.", transactionId);

        // 4. Prepare the event payload for the Detection Service
        Map<String, Object> enrichedEvent = new HashMap<>();
        enrichedEvent.put("transactionId", transactionId);
        enrichedEvent.put("timestamp", now.toString());
        enrichedEvent.put("amountInr", amountInr);
        enrichedEvent.put("payload", request);

        try {
            // 5. Save to Outbox instead of direct JMS publish
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .aggregateType("TRANSACTION")
                    .aggregateId(transactionId)
                    .payload(objectMapper.writeValueAsString(enrichedEvent))
                    .status("PENDING")
                    .createdAt(now)
                    .build();

            outboxRepository.save(outboxEvent);
            log.info("Transaction {} securely written to outbox table.", transactionId);

        } catch (JsonProcessingException e) {
            redisTemplate.delete(redisKey); // Release lock if parsing fails
            throw new RuntimeException("Failed to serialize transaction payload", e);
        }

        return transactionId;
    }
}