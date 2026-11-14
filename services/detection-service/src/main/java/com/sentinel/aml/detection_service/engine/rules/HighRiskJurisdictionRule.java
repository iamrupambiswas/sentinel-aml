package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.*;
import com.sentinel.aml.detection_service.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Rule 4 (P9): counterparty country on the watchlist, or counterparty name fuzzy-matches a listed name. */
@Component
@RequiredArgsConstructor
public class HighRiskJurisdictionRule implements DetectionRule {

    public static final String CODE = "HIGH_RISK_JURISDICTION";
    private final WatchlistService watchlist;

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionContext ctx, RuleConfig config) {
        String country = ctx.event().counterpartyCountry();
        String name = ctx.event().counterpartyName();

        boolean countryHit = watchlist.isHighRiskCountry(country);
        Optional<WatchlistService.NameMatch> nameHit =
                watchlist.bestNameMatch(name, config.number("nameSimilarity", 0.92));

        if (!countryHit && nameHit.isEmpty()) {
            return Optional.empty();
        }
        List<String> reasons = new ArrayList<>();
        if (countryHit) {
            reasons.add("counterparty jurisdiction " + country.toUpperCase() + " is on the high-risk country list");
        }
        nameHit.ifPresent(m -> reasons.add(String.format(
                "counterparty name '%s' matches watchlisted '%s' (similarity %.2f)", name, m.listedName(), m.score())));

        String explanation = String.format("Transaction %s on account %s for %s: %s.",
                ctx.transactionId(), ctx.accountId(), Inr.format(ctx.amountInr()), String.join("; ", reasons));
        String bucket = ctx.txnTime().atZone(ZoneOffset.UTC).toLocalDate().toString();
        return Optional.of(new RuleHit(CODE, config.weight(), explanation, List.of(ctx.transactionId()), bucket));
    }
}
