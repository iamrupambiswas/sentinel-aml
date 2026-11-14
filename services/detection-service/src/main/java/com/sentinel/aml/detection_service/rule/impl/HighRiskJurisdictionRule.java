package com.sentinel.aml.detection_service.rule.impl;

import com.sentinel.aml.detection_service.dto.RuleHit;
import com.sentinel.aml.detection_service.dto.TransactionEvent;
import com.sentinel.aml.detection_service.entity.AmlRule;
import com.sentinel.aml.detection_service.rule.DetectionRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class HighRiskJurisdictionRule implements DetectionRule {

    private static final String RULE_CODE = "HIGH_RISK_JURISDICTION";
    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_SET_COUNTRIES = "watch:countries";
    private static final String REDIS_HASH_NAMES = "watch:names";

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionEvent event, AmlRule ruleConfig) {
        String country = event.getCounterpartyCountry();
        String name = event.getCounterpartyName();

        boolean isCountryHighRisk = Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(REDIS_SET_COUNTRIES, country));
        boolean isNameHighRisk = Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(REDIS_HASH_NAMES, name));

        if (isCountryHighRisk || isNameHighRisk) {
            String explanation;
            if (isCountryHighRisk && isNameHighRisk) {
                explanation = String.format("Transaction involves both a high-risk country (%s) and a sanctioned counterparty (%s).", country, name);
            } else if (isCountryHighRisk) {
                explanation = String.format("Transaction involves a high-risk jurisdiction: %s.", country);
            } else {
                explanation = String.format("Transaction involves a sanctioned counterparty: %s.", name);
            }

            return Optional.of(RuleHit.builder()
                    .ruleCode(RULE_CODE)
                    .ruleWeight(ruleConfig.getWeight())
                    .explanation(explanation)
                    .build());
        }

        return Optional.empty();
    }
}