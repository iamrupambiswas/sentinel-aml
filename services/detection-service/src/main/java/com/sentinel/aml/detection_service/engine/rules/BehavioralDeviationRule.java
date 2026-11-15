package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.*;
import com.sentinel.aml.detection_service.window.SlidingWindowStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

/**
 * Rule 5 (P10): today's value on the account > N x its 90-day average daily value.
 * The baseline is computed nightly by {@link com.sentinel.aml.detection_service.job.BaselineJob}.
 */
@Component
@RequiredArgsConstructor
public class BehavioralDeviationRule implements DetectionRule {

    public static final String CODE = "BEHAVIORAL_DEVIATION";
    /** Day hashes are kept a bit longer than the 90-day baseline period. */
    public static final Duration DAY_TTL = Duration.ofDays(95);
    private final SlidingWindowStore store;

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public Set<String> supportedParams() {
        return Set.of("minDailyInr");
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionContext ctx, RuleConfig config) {
        LocalDate day = ctx.txnTime().atZone(ZoneOffset.UTC).toLocalDate();
        // Always record the day's activity - the baseline job needs it even if no baseline exists yet.
        SlidingWindowStore.DailyTotal today = store.addToDay(ctx.accountId(), day, ctx.transactionId(), ctx.amountInr(), DAY_TTL);

        BigDecimal baseline = store.getBaseline(ctx.accountId());
        if (baseline == null || baseline.signum() <= 0) {
            return Optional.empty();
        }
        if (today.total().compareTo(config.decimal("minDailyInr", new BigDecimal("50000"))) < 0) {
            return Optional.empty();
        }
        BigDecimal limit = baseline.multiply(config.threshold());
        if (today.total().compareTo(limit) <= 0) {
            return Optional.empty();
        }
        BigDecimal ratio = today.total().divide(baseline, 1, RoundingMode.HALF_UP);
        String explanation = String.format(
                "Account %s moved %s on %s, %sx its 90-day average daily value of %s (limit %sx). Evidence: %s.",
                ctx.accountId(), Inr.format(today.total()), day, ratio.toPlainString(), Inr.format(baseline),
                config.threshold().stripTrailingZeros().toPlainString(), String.join(", ", today.transactionIds()));
        return Optional.of(new RuleHit(CODE, config.weight(), explanation, today.transactionIds(), day.toString()));
    }
}
