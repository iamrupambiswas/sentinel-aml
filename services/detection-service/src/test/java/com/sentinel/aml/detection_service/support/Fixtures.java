package com.sentinel.aml.detection_service.support;

import com.sentinel.aml.common.event.TransactionIngestedEvent;
import com.sentinel.aml.detection_service.engine.RuleConfig;
import com.sentinel.aml.detection_service.engine.TransactionContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class Fixtures {
    public static final Instant T0 = Instant.parse("2026-11-10T08:00:00Z");
    public static final String ACCT = "ACC-1001";
    private static final AtomicInteger SEQ = new AtomicInteger();

    private Fixtures() {}

    public static TransactionIngestedEvent event(String from, String to, String amountInr, Instant at) {
        return event(from, to, amountInr, amountInr, "INR", at, null, null);
    }

    public static TransactionIngestedEvent event(String from, String to, String amount, String amountInr,
                                                 String currency, Instant at, String cpName, String cpCountry) {
        String id = "TXN-" + SEQ.incrementAndGet();
        return new TransactionIngestedEvent("EVT-" + id, id, id, from, to, new BigDecimal(amount), currency,
                new BigDecimal(amountInr), "WIRE", cpName, cpCountry, at, at);
    }

    /** ACCT receives money. */
    public static TransactionContext credit(String amountInr, Instant at) {
        return new TransactionContext(event("EXT-X", ACCT, amountInr, at), ACCT, TransactionContext.Direction.CREDIT, "EXT-X");
    }

    /** ACCT sends money. */
    public static TransactionContext debit(String amountInr, Instant at) {
        return new TransactionContext(event(ACCT, "EXT-Y", amountInr, at), ACCT, TransactionContext.Direction.DEBIT, "EXT-Y");
    }

    public static TransactionContext ctx(TransactionIngestedEvent e, TransactionContext.Direction d) {
        return new TransactionContext(e, ACCT, d, "EXT-Z");
    }

    public static Instant plus(Duration d) {
        return T0.plus(d);
    }

    public static RuleConfig config(String code, String weight, String threshold, long windowMinutes, Map<String, Object> params) {
        return new RuleConfig(code, new BigDecimal(weight), new BigDecimal(threshold), windowMinutes, 1, params);
    }
}
