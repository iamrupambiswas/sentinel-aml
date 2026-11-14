package com.sentinel.aml.ingestion_service.service.impl;

import com.sentinel.aml.ingestion_service.dto.IngestionResult;
import com.sentinel.aml.ingestion_service.dto.ProcessedTransaction;
import com.sentinel.aml.ingestion_service.dto.TransactionRequest;
import com.sentinel.aml.ingestion_service.exception.RequestInProgressException;
import com.sentinel.aml.ingestion_service.service.TransactionIngestionService;
import com.sentinel.aml.ingestion_service.support.TransactionFactory;
import com.sentinel.aml.ingestion_service.support.TransactionPersister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

/**
 * Streaming ingestion (P5) with idempotency (X1):
 * <ol>
 *   <li>SET idempotency:txn:{key} PROCESSING NX EX 24h - only one request per key wins.</li>
 *   <li>Save transaction + outbox row in one DB transaction (transactional outbox).</li>
 *   <li>After COMMIT the key's value becomes the transactionId, so a retry gets the original
 *       response back. After ROLLBACK the key is deleted, so the client can safely retry.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionIngestionServiceImpl implements TransactionIngestionService {

    static final String KEY_PREFIX = "idempotency:txn:";
    static final String IN_PROGRESS = "PROCESSING";
    static final Duration KEY_TTL = Duration.ofHours(24);

    private final TransactionFactory transactionFactory;
    private final TransactionPersister transactionPersister;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public IngestionResult ingestTransaction(TransactionRequest request, String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;

        Boolean isNewRequest = redisTemplate.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, KEY_TTL);
        if (Boolean.FALSE.equals(isNewRequest)) {
            String existing = redisTemplate.opsForValue().get(redisKey);
            if (existing == null || IN_PROGRESS.equals(existing)) {
                throw new RequestInProgressException(
                        "A request with this Idempotency-Key is still being processed. Retry shortly.");
            }
            log.info("Idempotent replay for key {} -> transaction {}", idempotencyKey, existing);
            return new IngestionResult(existing, true);
        }

        ProcessedTransaction processed;
        try {
            processed = transactionFactory.build(new TransactionFactory.Input(
                    idempotencyKey, null,
                    request.sourceAccountId(), request.destinationAccountId(),
                    request.amount(), request.currency(), request.type(),
                    request.counterpartyName(), request.counterpartyCountry(), request.txnTimestamp()));
        } catch (RuntimeException e) {
            redisTemplate.delete(redisKey); // validation failed: let the client fix and retry with same key
            throw e;
        }

        String transactionId = processed.getTransaction().getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    redisTemplate.opsForValue().set(redisKey, transactionId, KEY_TTL);
                } else {
                    redisTemplate.delete(redisKey);
                }
            }
        });

        transactionPersister.persistAll(List.of(processed));
        log.info("Transaction {} saved with outbox event {}", transactionId, processed.getOutboxEvent().getId());
        return new IngestionResult(transactionId, false);
    }
}
