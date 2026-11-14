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
