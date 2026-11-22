package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.window.WindowEntry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

final class RuleSupport {
    private RuleSupport() {}

    static List<String> ids(List<WindowEntry> entries) {
        return entries.stream().map(WindowEntry::transactionId).toList();
    }

    /**
     * Order-independent window check for count rules. {@code around} holds the entries within one window
     * of {@code ts} on both sides, sorted by time. Returns the entries of the fullest window
     * {@code [start, start + window]} that contains {@code ts} (ties: earliest start), which may include
     * entries newer than {@code ts} that were evaluated first. When the newest entry is evaluated last
     * (in-order delivery) this is exactly {@code [ts - window, ts]}, the classic look-back window.
     */
    static List<WindowEntry> fullestWindowContaining(List<WindowEntry> around, long ts, Duration window) {
        long w = window.toMillis();
        List<WindowEntry> best = List.of();
        for (int i = 0; i < around.size(); i++) {
            long start = around.get(i).timestampMillis();
            if (start < ts - w || start > ts) continue; // window [start, start + w] must contain ts
            int j = i;
            while (j < around.size() && around.get(j).timestampMillis() <= start + w) j++;
            if (j - i > best.size()) best = around.subList(i, j);
        }
        return best;
    }

    static BigDecimal sum(List<WindowEntry> entries) {
        return entries.stream().map(WindowEntry::amountInr).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Tumbling bucket index of a timestamp, used as the window part of the de-dup key. */
    static String bucket(long timestampMillis, Duration window) {
        return Long.toString(timestampMillis / Math.max(1, window.toMillis()));
    }

    static String humanDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        return h > 0 ? h + " h " + m + " m" : m + " m";
    }
}
