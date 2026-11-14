package com.sentinel.aml.detection_service.engine;

import com.sentinel.aml.common.event.TransactionIngestedEvent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A transaction seen from ONE monitored account's side.
 * A transfer A -> B is a DEBIT for A and a CREDIT for B; each internal account is evaluated
 * separately, so e.g. four deposits from different senders into one account are still
 * recognised as structuring on the receiving account.
 */
public record TransactionContext(
        TransactionIngestedEvent event,
        String accountId,
        Direction direction,
        String counterpartyAccountId
) {
    public enum Direction { CREDIT, DEBIT }

    public String transactionId() { return event.transactionId(); }
    public BigDecimal amountInr() { return event.amountInr(); }
    public BigDecimal originalAmount() { return event.amount(); }
    public Instant txnTime() { return event.txnTimestamp(); }
    public long txnTimeMillis() { return event.txnTimestamp().toEpochMilli(); }
}
