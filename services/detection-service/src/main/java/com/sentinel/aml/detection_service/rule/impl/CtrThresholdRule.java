package com.sentinel.aml.detection_service.rule.impl;

import com.sentinel.aml.detection_service.dto.RuleHit;
import com.sentinel.aml.detection_service.dto.TransactionEvent;
import com.sentinel.aml.detection_service.entity.AmlRule;
import com.sentinel.aml.detection_service.rule.DetectionRule;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CtrThresholdRule implements DetectionRule {

    private static final String RULE_CODE = "CTR_THRESHOLD";

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionEvent event, AmlRule ruleConfig) {
        if (event.getAmount().compareTo(ruleConfig.getThresholdAmount()) > 0) {
            String explanation = String.format(
                    "Account %s processed a transaction (ID: %s) of $%s, exceeding the CTR threshold of $%s.",
                    event.getSourceAccountId(),
                    event.transactionId(),
                    event.getAmount().toPlainString(),
                    ruleConfig.getThresholdAmount().toPlainString()
            );

            return Optional.of(RuleHit.builder()
                    .ruleCode(RULE_CODE)
                    .ruleWeight(ruleConfig.getWeight())
                    .explanation(explanation)
                    .build());
        }
        return Optional.empty();
    }
}