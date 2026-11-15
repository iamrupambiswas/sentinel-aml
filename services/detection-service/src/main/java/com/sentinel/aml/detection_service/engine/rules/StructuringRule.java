package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.*;
import com.sentinel.aml.detection_service.window.SlidingWindowStore;
import com.sentinel.aml.detection_service.window.WindowEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Rule 2 (P7): >= N transactions just below the CTR threshold on one account within the window. */
@Component
@RequiredArgsConstructor
public class StructuringRule implements DetectionRule {

    public static final String CODE = "STRUCTURING";
    private final SlidingWindowStore store;

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public Set<String> supportedParams() {
        return Set.of("minAmountInr", "maxAmountInr");
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionContext ctx, RuleConfig config) {
        BigDecimal min = config.decimal("minAmountInr", new BigDecimal("751500"));
        BigDecimal max = config.decimal("maxAmountInr", new BigDecimal("834999"));
        BigDecimal amount = ctx.amountInr();
        if (amount.compareTo(min) < 0 || amount.compareTo(max) > 0) {
            return Optional.empty();
        }

        List<WindowEntry> around = store.addAndGet("aml:win:struct:" + ctx.accountId(),
                new WindowEntry(ctx.transactionId(), amount, ctx.txnTimeMillis()), config.window());
        List<WindowEntry> window = RuleSupport.fullestWindowContaining(around, ctx.txnTimeMillis(), config.window());

        int required = config.threshold().intValue();
        if (window.size() < required) {
            return Optional.empty();
        }
        long span = window.get(window.size() - 1).timestampMillis() - window.get(0).timestampMillis();
        String explanation = String.format(
                "Account %s had %d transactions between %s and %s (each just under the %s reporting threshold) within %s. Evidence: %s.",
                ctx.accountId(), window.size(), Inr.format(min), Inr.format(max),
                Inr.format(max.add(BigDecimal.ONE)), RuleSupport.humanDuration(Duration.ofMillis(span)),
                String.join(", ", RuleSupport.ids(window)));
        return Optional.of(new RuleHit(CODE, config.weight(), explanation, RuleSupport.ids(window),
                RuleSupport.bucket(window.get(0).timestampMillis(), config.window())));
    }
}
