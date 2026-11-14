package com.sentinel.aml.detection_service.consumer;

import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.detection_service.dto.RuleHit;
import com.sentinel.aml.detection_service.dto.TransactionEvent;
import com.sentinel.aml.detection_service.entity.AmlAlert;
import com.sentinel.aml.detection_service.entity.AmlRule;
import com.sentinel.aml.detection_service.entity.ProcessedEvent;
import com.sentinel.aml.detection_service.repository.AmlAlertRepository;
import com.sentinel.aml.detection_service.repository.ProcessedEventRepository;
import com.sentinel.aml.detection_service.rule.DetectionRule;
import com.sentinel.aml.detection_service.service.AlertPublisherService;
import com.sentinel.aml.detection_service.service.RiskScoringService;
import com.sentinel.aml.detection_service.service.RuleCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final AmlAlertRepository amlAlertRepository;
    private final RuleCacheService ruleCacheService;
    private final AlertPublisherService alertPublisherService;
    private final RiskScoringService riskScoringService;

    // Automatically injects all implementations of DetectionRule
    private final List<DetectionRule> rules;

    @JmsListener(destination = MessagingConstants.TRANSACTION_QUEUE)
    @Transactional
    public void consumeTransaction(TransactionEvent event) {
        if (processedEventRepository.existsById(event.transactionId())) {
            log.warn("Duplicate message dropped for ID: {}", event.transactionId());
            return;
        }

        Map<String, AmlRule> activeRulesMap = ruleCacheService.getActiveRules().stream()
                .collect(Collectors.toMap(AmlRule::getRuleCode, Function.identity()));

        List<RuleHit> triggeredHits = new ArrayList<>();

        // Execute Strategy Pattern for all injected rules
        for (DetectionRule rule : rules) {
            AmlRule config = activeRulesMap.get(rule.getRuleCode());
            if (config != null && config.isActive()) {
                Optional<RuleHit> hit = rule.evaluate(event, config);
                hit.ifPresent(triggeredHits::add);
            }
        }

        if (!triggeredHits.isEmpty()) {
            // Assume 1.0 (LOW) for now until we integrate with Customer Service
            double finalScore = riskScoringService.calculateAggregateScore(triggeredHits, 1.0);

            // Combine explanations
            String combinedExplanation = triggeredHits.stream()
                    .map(RuleHit::getExplanation)
                    .collect(Collectors.joining(" | "));

            AmlAlert alert = AmlAlert.builder()
                    .transactionId(event.transactionId())
                    .accountId(event.getSourceAccountId())
                    .ruleTriggered(triggeredHits.get(0).getRuleCode()) // Primary rule
                    .alertAmount(event.getAmount())
                    .status("NEW")
                    .createdAt(LocalDateTime.now())
                    .build();

            // Note: We need to update AmlAlert entity to hold riskScore and explanation fields
            alert = amlAlertRepository.save(alert);
            alertPublisherService.publishAlert(alert);
            log.warn("AML ALERT generated for account {}. Score: {}", event.getSourceAccountId(), finalScore);
        }

        ProcessedEvent marker = ProcessedEvent.builder()
                .transactionId(event.transactionId())
                .processedAt(LocalDateTime.now())
                .build();
        processedEventRepository.save(marker);
    }
}