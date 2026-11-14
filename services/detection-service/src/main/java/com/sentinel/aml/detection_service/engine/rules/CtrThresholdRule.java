package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.*;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Rule 1 (P6): single transaction at or above the CTR threshold (INR-normalized). */
@Component
public class CtrThresholdRule implements DetectionRule {

    public static final String CODE = "CTR_THRESHOLD";

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionContext ctx, RuleConfig config) {
        if (ctx.amountInr().compareTo(config.threshold()) < 0) {
            return Optional.empty();
        }
        String explanation = String.format(
                "Account %s %s %s (%s %s) in transaction %s, at or above the %s CTR reporting threshold.",
                ctx.accountId(),
                ctx.direction() == TransactionContext.Direction.CREDIT ? "received" : "sent",
                Inr.format(ctx.amountInr()), ctx.event().currency(), ctx.originalAmount().toPlainString(),
                ctx.transactionId(), Inr.format(config.threshold()));
        String bucket = ctx.txnTime().atZone(ZoneOffset.UTC).toLocalDate().toString();
        return Optional.of(new RuleHit(CODE, config.weight(), explanation, List.of(ctx.transactionId()), bucket));
    }
}
