package com.sentinel.aml.detection_service.window;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Fast detection memory (Redis in production, an in-memory fake in unit tests).
 * Every write is idempotent (same txnId = same member/field), so a redelivered JMS
 * message never double-counts.
 */
public interface SlidingWindowStore {

    /**
     * Atomically add {@code entry} to {@code key} and return every entry within {@code window} of it on
     * BOTH sides, i.e. scores in {@code [ts - window, ts + window]}, sorted by time.
     * <p>
     * Both sides, because one account's transactions may be evaluated out of time order (several
     * consumer threads); the rule then checks every window of length {@code window} that contains the
     * new entry. Old data is trimmed relative to the NEWEST entry minus {@link #retention(Duration)},
     * never inside the new entry's own look-back, so late arrivals still see their window.
     */
    List<WindowEntry> addAndGet(String key, WindowEntry entry, Duration window);

    /** How far behind the newest entry window data is kept: late arrivals up to one window behind are fully evaluated. */
    static Duration retention(Duration window) {
        return window.multipliedBy(2);
    }

    /** Read window contents without modifying them. */
    List<WindowEntry> read(String key, long fromMillis, long toMillis);

    /** Record a transaction in the account's per-day hash (field = txnId, value = amountInr). Returns the day's total. */
    DailyTotal addToDay(String accountId, LocalDate day, String transactionId, BigDecimal amountInr, Duration ttl);

    /** Day totals for one account over a date range (only days with activity are returned). */
    Map<LocalDate, BigDecimal> dailyTotals(String accountId, LocalDate fromInclusive, LocalDate toInclusive);

    BigDecimal getBaseline(String accountId);

    void putBaseline(String accountId, BigDecimal avgDailyInr, Duration ttl);

    void markAccountActive(String accountId);

    java.util.Set<String> activeAccounts();

    record DailyTotal(BigDecimal total, List<String> transactionIds) {}
}
