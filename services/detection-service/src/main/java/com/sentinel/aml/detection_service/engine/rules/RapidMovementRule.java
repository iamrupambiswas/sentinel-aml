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
    public Optional<RuleHit> evaluate(TransactionContext ctx, RuleConfig config) {
        String creditKey = "aml:win:cr:" + ctx.accountId();
        String debitKey = "aml:win:dr:" + ctx.accountId();
        WindowEntry entry = new WindowEntry(ctx.transactionId(), ctx.amountInr(), ctx.txnTimeMillis());

        if (ctx.direction() == TransactionContext.Direction.CREDIT) {
            store.addAndGet(creditKey, entry, config.window());
            return Optional.empty(); // only money LEAVING the account can complete the pattern
        }

        List<WindowEntry> debits = store.addAndGet(debitKey, entry, config.window());
        long from = ctx.txnTimeMillis() - config.window().toMillis();
        List<WindowEntry> credits = store.read(creditKey, from, ctx.txnTimeMillis());

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
        long firstCredit = credits.isEmpty() ? ctx.txnTimeMillis() : credits.get(0).timestampMillis();
        return Optional.of(new RuleHit(CODE, config.weight(), explanation, evidence,
                RuleSupport.bucket(firstCredit, config.window())));
    }
}
