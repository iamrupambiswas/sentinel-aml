package com.sentinel.aml.detection_service.support;

import com.sentinel.aml.detection_service.window.SlidingWindowStore;
import com.sentinel.aml.detection_service.window.WindowEntry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/** Same semantics as the Redis/Lua implementation, in plain Java, for fast unit tests. */
public class InMemorySlidingWindowStore implements SlidingWindowStore {

    private final Map<String, TreeMap<String, Long>> zsets = new HashMap<>();
    private final Map<String, Map<String, BigDecimal>> days = new HashMap<>();
    private final Map<String, BigDecimal> baselines = new HashMap<>();
    private final Set<String> active = new HashSet<>();

    @Override
    public List<WindowEntry> addAndGet(String key, WindowEntry entry, Duration window) {
        TreeMap<String, Long> z = zsets.computeIfAbsent(key, k -> new TreeMap<>());
        z.put(entry.encode(), entry.timestampMillis());
        long from = entry.timestampMillis() - window.toMillis();
        z.values().removeIf(score -> score < from);
        return read(key, from, entry.timestampMillis());
    }

    @Override
    public List<WindowEntry> read(String key, long fromMillis, long toMillis) {
        return zsets.getOrDefault(key, new TreeMap<>()).entrySet().stream()
                .filter(e -> e.getValue() >= fromMillis && e.getValue() <= toMillis)
                .map(e -> WindowEntry.decode(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(WindowEntry::timestampMillis))
                .toList();
    }

    @Override
    public DailyTotal addToDay(String accountId, LocalDate day, String txnId, BigDecimal amountInr, Duration ttl) {
        Map<String, BigDecimal> d = days.computeIfAbsent(accountId + ":" + day, k -> new TreeMap<>());
        d.put(txnId, amountInr);
        return new DailyTotal(d.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add), new ArrayList<>(d.keySet()));
    }

    @Override
    public Map<LocalDate, BigDecimal> dailyTotals(String accountId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> out = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<String, BigDecimal> m = days.get(accountId + ":" + d);
            if (m != null && !m.isEmpty()) out.put(d, m.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return out;
    }

    @Override public BigDecimal getBaseline(String accountId) { return baselines.get(accountId); }
    @Override public void putBaseline(String accountId, BigDecimal v, Duration ttl) { baselines.put(accountId, v); }
    @Override public void markAccountActive(String accountId) { active.add(accountId); }
    @Override public Set<String> activeAccounts() { return active; }
}
