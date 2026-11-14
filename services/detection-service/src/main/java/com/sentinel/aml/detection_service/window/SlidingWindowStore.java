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

    /** Atomically add {@code entry} to {@code key}, trim everything older than {@code window} and return what's left. */
    List<WindowEntry> addAndGet(String key, WindowEntry entry, Duration window);

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
