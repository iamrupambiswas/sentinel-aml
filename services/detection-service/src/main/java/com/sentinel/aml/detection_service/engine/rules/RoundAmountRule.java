package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.*;
import com.sentinel.aml.detection_service.window.SlidingWindowStore;
import com.sentinel.aml.detection_service.window.WindowEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Rule 6 (P11): >= N "suspiciously round" transactions in the window. Round means an exact
 * multiple of {@code modulo} in the ORIGINAL currency, or within {@code nearThresholdPct}% below
 * the CTR threshold. Small amounts (< minAmountInr) are ignored - rent and EMIs are round too.
 */
@Component
@RequiredArgsConstructor
public class RoundAmountRule implements DetectionRule {

    public static final String CODE = "ROUND_AMOUNT";
    private final SlidingWindowStore store;

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public Set<String> supportedParams() {
        return Set.of("ctrThresholdInr", "minAmountInr", "modulo", "nearThresholdPct");
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionContext ctx, RuleConfig config) {
        if (!isSuspiciouslyRound(ctx, config)) {
            return Optional.empty();
        }
        List<WindowEntry> around = store.addAndGet("aml:win:round:" + ctx.accountId(),
                new WindowEntry(ctx.transactionId(), ctx.amountInr(), ctx.txnTimeMillis()), config.window());
        List<WindowEntry> window = RuleSupport.fullestWindowContaining(around, ctx.txnTimeMillis(), config.window());

        if (window.size() < config.threshold().intValue()) {
            return Optional.empty();
        }
        String explanation = String.format(
                "Account %s made %d round-number or just-below-threshold transactions totalling %s within %d days. Evidence: %s.",
                ctx.accountId(), window.size(), Inr.format(RuleSupport.sum(window)),
                config.windowMinutes() / 1440, String.join(", ", RuleSupport.ids(window)));
        return Optional.of(new RuleHit(CODE, config.weight(), explanation, RuleSupport.ids(window),
                RuleSupport.bucket(window.get(0).timestampMillis(), config.window())));
    }

    static boolean isSuspiciouslyRound(TransactionContext ctx, RuleConfig config) {
        BigDecimal amountInr = ctx.amountInr();
        if (amountInr.compareTo(config.decimal("minAmountInr", new BigDecimal("100000"))) < 0) {
            return false;
        }
        BigDecimal modulo = config.decimal("modulo", new BigDecimal("1000"));
        boolean exactMultiple = ctx.originalAmount().remainder(modulo).signum() == 0;

        BigDecimal ctr = config.decimal("ctrThresholdInr", new BigDecimal("830000"));
        BigDecimal pct = config.decimal("nearThresholdPct", new BigDecimal("2"));
        BigDecimal lowerEdge = ctr.subtract(ctr.multiply(pct).movePointLeft(2));
        boolean justBelowThreshold = amountInr.compareTo(lowerEdge) >= 0 && amountInr.compareTo(ctr) < 0;

        return exactMultiple || justBelowThreshold;
    }
}
