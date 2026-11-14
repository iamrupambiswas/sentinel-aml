package com.sentinel.aml.detection_service.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.detection_service.engine.RuleConfig;
import com.sentinel.aml.detection_service.entity.AmlRule;
import com.sentinel.aml.detection_service.repository.AmlRuleRepository;
import com.sentinel.aml.detection_service.service.RuleCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the active rule set in memory. The whole map is swapped in one volatile write, so a
 * consumer thread never sees a half-refreshed set (the old clear()+putAll() had that gap).
 * Phase 5: also reload on a message from the sentinel.rules.changed topic for < 5 s hot-reload.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleCacheServiceImpl implements RuleCacheService {

    private final AmlRuleRepository amlRuleRepository;
    private final ObjectMapper objectMapper;
    private volatile Map<String, RuleConfig> rules = Map.of();

    @Override
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedRateString = "${sentinel.rules.refresh-ms:300000}", initialDelayString = "${sentinel.rules.refresh-ms:300000}")
    public void refreshRuleCache() {
        Map<String, RuleConfig> fresh = new HashMap<>();
        for (AmlRule r : amlRuleRepository.findByActiveTrue()) {
            fresh.put(r.getRuleCode(), new RuleConfig(r.getRuleCode(), r.getWeight(), r.getThresholdAmount(),
                    r.getWindowMinutes(), r.getVersion(), parseParams(r)));
        }
        rules = Map.copyOf(fresh);
        log.info("Loaded {} active AML rules: {}", rules.size(), rules.keySet());
    }

    @Override
    public Map<String, RuleConfig> getActiveRules() {
        return rules;
    }

    private Map<String, Object> parseParams(AmlRule rule) {
        if (rule.getParams() == null || rule.getParams().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rule.getParams(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Invalid params JSON on rule {} - using defaults", rule.getRuleCode(), e);
            return Map.of();
        }
    }
}
