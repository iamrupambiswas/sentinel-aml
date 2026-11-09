package com.sentinel.aml.ingestion_service.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.ingestion_service.dto.ProcessedTransaction;
import com.sentinel.aml.ingestion_service.dto.TransactionCsvDto;
import com.sentinel.aml.ingestion_service.entity.IngestionError;
import com.sentinel.aml.ingestion_service.entity.OutboxEvent;
import com.sentinel.aml.ingestion_service.entity.Transaction;
import com.sentinel.aml.ingestion_service.repository.IngestionErrorRepository;
import com.sentinel.aml.ingestion_service.service.FxRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class TransactionItemProcessor implements ItemProcessor<TransactionCsvDto, ProcessedTransaction> {

    private final FxRateService fxRateService;
    private final IngestionErrorRepository errorRepository;
    private final ObjectMapper objectMapper;

    @Value("#{jobParameters['batchId']}")
    private String batchId;

    @Override
    public ProcessedTransaction process(TransactionCsvDto item) {
        try {

            // Inside TransactionItemProcessor.process(TransactionCsvDto item)
            if (item.getAmount() == null || item.getAmount().trim().isEmpty()) {
                throw new IllegalArgumentException("Amount column is missing or empty");
            }

            BigDecimal amount = new BigDecimal(item.getAmount());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }

            BigDecimal amountInr = fxRateService.normalizeToInr(amount, item.getCurrency());
            String transactionId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();

            Transaction txn = Transaction.builder()
                    .id(transactionId)
                    .externalRef("BATCH-" + UUID.randomUUID().toString())
                    .sourceAccountId(item.getSourceAccountId())
                    .destinationAccountId(item.getDestinationAccountId())
                    .amount(amount)
                    .currency(item.getCurrency())
                    .amountInr(amountInr)
                    .type(item.getType())
                    .txnTimestamp(now)
                    .build();

            Map<String, Object> enrichedEvent = new HashMap<>();
            enrichedEvent.put("transactionId", transactionId);
            enrichedEvent.put("timestamp", now.toString());
            enrichedEvent.put("amountInr", amountInr);
            enrichedEvent.put("payload", item);

            OutboxEvent outbox = OutboxEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .aggregateType("TRANSACTION")
                    .aggregateId(transactionId)
                    .payload(objectMapper.writeValueAsString(enrichedEvent))
                    .status("PENDING")
                    .createdAt(now)
                    .build();

            return new ProcessedTransaction(txn, outbox);

        } catch (Exception e) {
            log.error("Error processing row in batch {}: {}", batchId, e.getMessage());

            IngestionError error = IngestionError.builder()
                    .id(UUID.randomUUID().toString())
                    .batchId(batchId)
                    .rawData(item.toString())
                    .errorReason(e.getMessage())
                    .createdAt(LocalDateTime.now())
                    .build();
            errorRepository.save(error);

            return null; // Returning null skips this item in the chunk
        }
    }
}