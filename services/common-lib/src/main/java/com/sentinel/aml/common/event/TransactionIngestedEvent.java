package com.sentinel.aml.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by ingestion-service (via the outbox) to {@code sentinel.txn.ingested}.
 * This is the single contract detection-service consumes; both sides share this class.
 *
 * @param eventId             unique per message (idempotency key for consumers)
 * @param transactionId       ingestion-side transaction id
 * @param sourceAccountId     account the money leaves (DEBIT side)
 * @param destinationAccountId account the money enters (CREDIT side)
 * @param amount              original amount in {@code currency}
 * @param amountInr           normalized amount; all rule thresholds are in INR
 * @param counterpartyName    optional, used by the watchlist rule
 * @param counterpartyCountry optional ISO-3166 alpha-2/3 code, used by the jurisdiction rule
 * @param txnTimestamp        business time of the transaction (rules window on this, not wall-clock)
 */
public record TransactionIngestedEvent(
        String eventId,
        String transactionId,
        String externalRef,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        BigDecimal amountInr,
        String type,
        String counterpartyName,
        String counterpartyCountry,
        Instant txnTimestamp,
        Instant ingestedAt
) {}
