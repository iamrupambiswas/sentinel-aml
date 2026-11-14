package com.sentinel.aml.detection_service.service.impl;

import com.sentinel.aml.detection_service.entity.AmlRule;
import com.sentinel.aml.detection_service.repository.AmlRuleRepository;
import com.sentinel.aml.detection_service.service.RuleCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleCacheServiceImpl implements RuleCacheService {

    private final AmlRuleRepository amlRuleRepository;
    private final Map<String, AmlRule> ruleCache = new ConcurrentHashMap<>();

    @Override
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedRate = 300000) // Refresh every 5 minutes
    public void refreshRuleCache() {
        log.info("Refreshing AML Rule Cache from database...");
        List<AmlRule> activeRules = amlRuleRepository.findByIsActiveTrue();

        Map<String, AmlRule> newCache = activeRules.stream()
                .collect(Collectors.toMap(AmlRule::getRuleCode, rule -> rule));

        ruleCache.clear();
        ruleCache.putAll(newCache);

        log.info("Successfully loaded {} active AML rules into memory.", ruleCache.size());
    }

    @Override
    public List<AmlRule> getActiveRules() {
        return new ArrayList<>(ruleCache.values());
    }
}