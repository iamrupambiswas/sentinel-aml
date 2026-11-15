package com.sentinel.aml.detection_service.engine;

import com.sentinel.aml.common.event.RuleHitPayload;
import com.sentinel.aml.common.event.RuleHitsDetectedEvent;
import com.sentinel.aml.common.event.TransactionIngestedEvent;
import com.sentinel.aml.detection_service.service.CustomerRiskLookup;
import com.sentinel.aml.detection_service.service.RiskScoringService;
import com.sentinel.aml.detection_service.service.RuleCacheService;
import com.sentinel.aml.detection_service.window.SlidingWindowStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure orchestration, no JMS/DB: event -> per-account contexts -> enabled rules -> score + explanation.
 * Kept free of messaging so it can be unit-tested directly.
 */
@Component
@Slf4j
public class DetectionEngine {

    private final List<DetectionRule> rules;
    private final RuleCacheService ruleCache;
    private final RiskScoringService scoring;
    private final CustomerRiskLookup customerRisk;
    private final SlidingWindowStore store;
    private final Clock clock;
    private final List<String> externalPrefixes;

    public DetectionEngine(List<DetectionRule> rules, RuleCacheService ruleCache, RiskScoringService scoring,
                           CustomerRiskLookup customerRisk, SlidingWindowStore store, Clock clock,
                           @Value("${sentinel.detection.external-account-prefixes:EXT-}") List<String> externalPrefixes) {
        this.rules = rules;
        this.ruleCache = ruleCache;
        this.scoring = scoring;
        this.customerRisk = customerRisk;
        this.store = store;
        this.clock = clock;
        this.externalPrefixes = externalPrefixes;
    }

    public List<RuleHitsDetectedEvent> evaluate(TransactionIngestedEvent event) {
        Map<String, RuleConfig> active = ruleCache.getActiveRules();
        List<RuleHitsDetectedEvent> results = new ArrayList<>();

        for (TransactionContext ctx : contextsFor(event)) {
            store.markAccountActive(ctx.accountId());
            List<RuleHit> hits = new ArrayList<>();
            for (DetectionRule rule : rules) {
                RuleConfig config = active.get(rule.getRuleCode());
                if (config == null) continue; // disabled or unknown
                try {
                    rule.evaluate(ctx, config).ifPresent(hits::add);
                } catch (RuntimeException e) {
                    // One broken rule must not stop the others (and must not poison the message)
                    log.error("Rule {} failed for txn {} / account {}", rule.getRuleCode(), ctx.transactionId(), ctx.accountId(), e);
                }
            }
            if (!hits.isEmpty()) {
                results.add(toEvent(ctx, hits));
            }
        }
        return results;
    }

    /** One context per INTERNAL account touched by the transaction. */
    List<TransactionContext> contextsFor(TransactionIngestedEvent e) {
        List<TransactionContext> out = new ArrayList<>(2);
        if (!isExternal(e.sourceAccountId())) {
            out.add(new TransactionContext(e, e.sourceAccountId(), TransactionContext.Direction.DEBIT, e.destinationAccountId()));
        }
        if (!isExternal(e.destinationAccountId())) {
            out.add(new TransactionContext(e, e.destinationAccountId(), TransactionContext.Direction.CREDIT, e.sourceAccountId()));
        }
        return out;
    }

    private boolean isExternal(String accountId) {
        return accountId == null || externalPrefixes.stream().anyMatch(accountId::startsWith);
    }

    private RuleHitsDetectedEvent toEvent(TransactionContext ctx, List<RuleHit> hits) {
        hits.sort(Comparator.comparing(RuleHit::weight).reversed()); // primary (heaviest) rule first
        BigDecimal score = scoring.calculateAggregateScore(hits, customerRisk.multiplierFor(ctx.accountId()));
        String explanation = hits.stream().map(RuleHit::explanation).collect(Collectors.joining(" | "));
        List<RuleHitPayload> payloads = hits.stream()
                .map(h -> new RuleHitPayload(h.ruleCode(), h.weight(), h.explanation(), h.evidenceTxnIds(), h.windowBucket()))
                .toList();
        return new RuleHitsDetectedEvent(UUID.randomUUID().toString(), ctx.transactionId(), ctx.accountId(),
                payloads, score, explanation, ctx.txnTime(), clock.instant());
    }
}
