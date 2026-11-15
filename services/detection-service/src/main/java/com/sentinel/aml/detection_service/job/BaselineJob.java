package com.sentinel.aml.detection_service.job;

import com.sentinel.aml.detection_service.window.SlidingWindowStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

/**
 * Nightly 90-day baseline for BEHAVIORAL_DEVIATION (P10): average INR value per ACTIVE day over
 * the 90 days before today, for every account seen by detection.
 * NOTE (multiple pods): guard with ShedLock in Phase 8 so only one pod runs it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BaselineJob {

    private static final int BASELINE_DAYS = 90;
    private final SlidingWindowStore store;
    private final Clock clock;

    @Scheduled(cron = "${sentinel.baseline.cron:0 30 0 * * *}", zone = "UTC")
    public void runNightly() {
        recomputeAll();
    }

    /** @return number of accounts that got a baseline */
    public int recomputeAll() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(BASELINE_DAYS);
        LocalDate to = today.minusDays(1);
        int updated = 0;
        for (String accountId : store.activeAccounts()) {
            if (recompute(accountId, from, to)) updated++;
        }
        log.info("Baseline job finished: {} accounts updated ({} .. {})", updated, from, to);
        return updated;
    }

    boolean recompute(String accountId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> totals = store.dailyTotals(accountId, from, to);
        if (totals.isEmpty()) {
            return false;
        }
        BigDecimal sum = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(totals.size()), 2, RoundingMode.HALF_UP);
        store.putBaseline(accountId, avg, Duration.ofDays(3));
        return true;
    }
}
