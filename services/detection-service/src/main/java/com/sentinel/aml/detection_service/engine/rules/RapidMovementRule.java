package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.*;
import com.sentinel.aml.detection_service.window.SlidingWindowStore;
import com.sentinel.aml.detection_service.window.WindowEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Rule 3 (P8): debits within the window reach >= X% of the credits received in it (layering). */
@Component
@RequiredArgsConstructor
public class RapidMovementRule implements DetectionRule {

    public static final String CODE = "RAPID_MOVEMENT";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final SlidingWindowStore store;

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public Set<String> supportedParams() {
        return Set.of("minCreditInr");
    }

    /**
     * The pattern is anchored at a debit d: credits and debits in {@code [d - window, d]}. Evaluation is
     * order-independent (ADR-005 §11): the new entry (credit or debit) is checked against every anchor
     * debit whose window contains it, i.e. debits in {@code [ts, ts + window]}, latest first. So a debit
     * evaluated before its credit still fires, once the credit arrives. With in-order delivery the only
     * anchor is the debit being evaluated, exactly as before.
     */
    @Override
    public Optional<RuleHit> evaluate(TransactionContext ctx, RuleConfig config) {
        String creditKey = "aml:win:cr:" + ctx.accountId();
        String debitKey = "aml:win:dr:" + ctx.accountId();
        long ts = ctx.txnTimeMillis();
        long w = config.window().toMillis();
        WindowEntry entry = new WindowEntry(ctx.transactionId(), ctx.amountInr(), ts);

        // Add to our own side first, then read the other side: of two concurrent arrivals the later reader sees both.
        List<WindowEntry> credits;
        List<WindowEntry> debits;
        if (ctx.direction() == TransactionContext.Direction.CREDIT) {
            credits = store.addAndGet(creditKey, entry, config.window());
            debits = store.read(debitKey, ts - w, ts + w);
        } else {
            debits = store.addAndGet(debitKey, entry, config.window());
            credits = store.read(creditKey, ts - w, ts + w);
        }

        for (int i = debits.size() - 1; i >= 0; i--) {
            long anchor = debits.get(i).timestampMillis();
            if (anchor < ts) break; // this anchor's window doesn't contain the new entry
            Optional<RuleHit> hit = evaluateAnchor(ctx, config, between(credits, anchor - w, anchor),
                    between(debits, anchor - w, anchor), anchor);
            if (hit.isPresent()) return hit;
        }
        return Optional.empty();
    }

    private Optional<RuleHit> evaluateAnchor(TransactionContext ctx, RuleConfig config,
                                             List<WindowEntry> credits, List<WindowEntry> debits, long anchor) {
        BigDecimal totalCredits = RuleSupport.sum(credits);
        if (totalCredits.compareTo(config.decimal("minCreditInr", new BigDecimal("100000"))) < 0) {
            return Optional.empty();
        }
        BigDecimal totalDebits = RuleSupport.sum(debits);
        // Compare exactly (debits*100 >= threshold*credits). Rounding the percentage first would turn
        // 79.9999% into 80.00% and fire on a boundary it shouldn't.
        if (totalDebits.multiply(HUNDRED).compareTo(config.threshold().multiply(totalCredits)) < 0) {
            return Optional.empty();
        }
        BigDecimal pctOut = totalDebits.multiply(HUNDRED).divide(totalCredits, 2, RoundingMode.DOWN);

        List<String> evidence = new ArrayList<>(RuleSupport.ids(credits));
        evidence.addAll(RuleSupport.ids(debits));
        String explanation = String.format(
                "Account %s received %s and sent out %s (%s%%) within %d hours. Evidence: %s.",
                ctx.accountId(), Inr.format(totalCredits), Inr.format(totalDebits), pctOut.toPlainString(),
                config.windowMinutes() / 60, String.join(", ", evidence));
        long firstCredit = credits.isEmpty() ? anchor : credits.get(0).timestampMillis();
        return Optional.of(new RuleHit(CODE, config.weight(), explanation, evidence,
                RuleSupport.bucket(firstCredit, config.window())));
    }

    private static List<WindowEntry> between(List<WindowEntry> sorted, long from, long to) {
        return sorted.stream().filter(e -> e.timestampMillis() >= from && e.timestampMillis() <= to).toList();
    }
}
