package com.sentinel.aml.ingestion_service.batch;

import com.sentinel.aml.ingestion_service.dto.ProcessedTransaction;
import com.sentinel.aml.ingestion_service.dto.TransactionCsvDto;
import com.sentinel.aml.ingestion_service.exception.InvalidTransactionException;
import com.sentinel.aml.ingestion_service.support.TransactionFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Validates and enriches one CSV row. Any problem is THROWN (never swallowed): the step's
 * skip policy then routes the row to {@link CsvSkipListener}, which records it with its line
 * number in ingestion_error (P4: never silently dropped).
 */
@Component
@StepScope
@RequiredArgsConstructor
public class TransactionItemProcessor implements ItemProcessor<TransactionCsvDto, ProcessedTransaction> {

    private static final Pattern CURRENCY = Pattern.compile("^[A-Za-z]{3}$");
    private static final Pattern COUNTRY = Pattern.compile("^[A-Za-z]{2,3}$");

    private final TransactionFactory transactionFactory;

    @Value("#{jobParameters['batchId']}")
    private String batchId;

    @Override
    public ProcessedTransaction process(TransactionCsvDto item) {
        require(item.getSourceAccountId(), "sourceAccountId");
        require(item.getDestinationAccountId(), "destinationAccountId");
        require(item.getAmount(), "amount");
        require(item.getCurrency(), "currency");
        require(item.getType(), "type");

        BigDecimal amount;
        try {
            amount = new BigDecimal(item.getAmount().trim());
        } catch (NumberFormatException e) {
            throw new InvalidTransactionException("Amount is not a number: '" + item.getAmount() + "'");
        }
        if (!CURRENCY.matcher(item.getCurrency().trim()).matches()) {
            throw new InvalidTransactionException("Currency must be a 3-letter ISO code: '" + item.getCurrency() + "'");
        }
        String country = item.getCounterpartyCountry();
        if (country != null && !country.isBlank() && !COUNTRY.matcher(country.trim()).matches()) {
            throw new InvalidTransactionException("Counterparty country must be an ISO code: '" + country + "'");
        }

        return transactionFactory.build(new TransactionFactory.Input(
                // Deterministic external ref: re-running the same batch can't double-insert (unique constraint)
                "BATCH-" + batchId + "-L" + item.getLineNumber(),
                batchId,
                item.getSourceAccountId().trim(),
                item.getDestinationAccountId().trim(),
                amount,
                item.getCurrency().trim(),
                item.getType().trim(),
                item.getCounterpartyName(),
                country == null ? null : country.trim(),
                parseTimestamp(item.getTxnTimestamp())));
    }

    private static void require(String value, String column) {
        if (value == null || value.isBlank()) {
            throw new InvalidTransactionException("Column '" + column + "' is missing or empty");
        }
    }

    /** Accepts ISO instants (2026-11-14T10:15:30Z) or local date-times treated as UTC (2026-11-14T10:15:30). */
    static Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                throw new InvalidTransactionException("txnTimestamp is not ISO-8601: '" + raw + "'");
            }
        }
    }
}
