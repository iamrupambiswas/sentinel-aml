package com.sentinel.aml.ingestion_service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.event.TransactionIngestedEvent;
import com.sentinel.aml.ingestion_service.dto.ProcessedTransaction;
import com.sentinel.aml.ingestion_service.entity.OutboxEvent;
import com.sentinel.aml.ingestion_service.entity.Transaction;
import com.sentinel.aml.ingestion_service.exception.InvalidTransactionException;
import com.sentinel.aml.ingestion_service.service.AccountValidationService;
import com.sentinel.aml.ingestion_service.service.FxRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Single place where a validated transaction becomes (entity + outbox event).
 * Used by BOTH the REST (streaming) path and the CSV (batch) path so they can never drift apart.
 */
@Component
@RequiredArgsConstructor
public class TransactionFactory {

    public static final String STATUS_PENDING = "PENDING";
    public static final String AGGREGATE_TRANSACTION = "TRANSACTION";

    private final FxRateService fxRateService;
    private final AccountValidationService accountValidationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public record Input(String externalRef, String batchId, String sourceAccountId, String destinationAccountId,
                        BigDecimal amount, String currency, String type, String counterpartyName,
                        String counterpartyCountry, Instant txnTimestamp) {}

    public ProcessedTransaction build(Input in) {
        // ---- business validation (Bean Validation already handled shape/format) ----
        if (!accountValidationService.isAccountValid(in.sourceAccountId())) {
            throw new InvalidTransactionException("Unknown source account: " + in.sourceAccountId());
        }
        if (!accountValidationService.isAccountValid(in.destinationAccountId())) {
            throw new InvalidTransactionException("Unknown destination account: " + in.destinationAccountId());
        }
        if (in.sourceAccountId().equals(in.destinationAccountId())) {
            throw new InvalidTransactionException("Source and destination account must differ");
        }
        if (in.amount() == null || in.amount().signum() <= 0) {
            throw new InvalidTransactionException("Amount must be positive");
        }

        Instant now = clock.instant();
        Instant txnTime = in.txnTimestamp() != null ? in.txnTimestamp() : now;
        if (txnTime.isAfter(now.plusSeconds(60))) {
            throw new InvalidTransactionException("Transaction timestamp cannot be in the future");
        }

        String currency = in.currency().toUpperCase();
        BigDecimal amountInr = fxRateService.normalizeToInr(in.amount(), currency); // throws on unknown currency
        String transactionId = UUID.randomUUID().toString();
        String country = blankToNull(in.counterpartyCountry());

        Transaction txn = Transaction.builder()
                .id(transactionId)
                .externalRef(in.externalRef())
                .sourceAccountId(in.sourceAccountId())
                .destinationAccountId(in.destinationAccountId())
                .amount(in.amount())
                .currency(currency)
                .amountInr(amountInr)
                .type(in.type().toUpperCase())
                .counterpartyName(blankToNull(in.counterpartyName()))
                .counterpartyCountry(country == null ? null : country.toUpperCase())
                .batchId(in.batchId())
                .txnTimestamp(LocalDateTime.ofInstant(txnTime, ZoneOffset.UTC))
                .ingestedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                .build();

        TransactionIngestedEvent event = new TransactionIngestedEvent(
                UUID.randomUUID().toString(),
                transactionId,
                txn.getExternalRef(),
                txn.getSourceAccountId(),
                txn.getDestinationAccountId(),
                txn.getAmount(),
                txn.getCurrency(),
                amountInr,
                txn.getType(),
                txn.getCounterpartyName(),
                txn.getCounterpartyCountry(),
                txnTime,
                now);

        OutboxEvent outbox = OutboxEvent.builder()
                .id(event.eventId())
                .aggregateType(AGGREGATE_TRANSACTION)
                .aggregateId(transactionId)
                .payload(toJson(event))
                .status(STATUS_PENDING)
                .createdAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                .build();

        return new ProcessedTransaction(txn, outbox);
    }

    private String toJson(TransactionIngestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TransactionIngestedEvent", e);
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
