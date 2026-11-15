package com.sentinel.aml.detection_service.rules;

import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.common.event.RulesChangedEvent;
import com.sentinel.aml.detection_service.engine.RuleConfig;
import com.sentinel.aml.detection_service.service.RuleCacheService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Every detection pod subscribes to the topic (pub/sub: each subscriber gets its own copy).
 * The subscription is non-durable on purpose: a pod that is down misses the message, but it loads
 * the full rule set from MySQL when it starts, so nothing is lost.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RulesChangedListener {

    private final RuleCacheService ruleCache;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @JmsListener(destination = MessagingConstants.RULES_CHANGED_TOPIC, containerFactory = "topicListenerFactory")
    public void onRulesChanged(RulesChangedEvent event) {
        ruleCache.refreshRuleCache();
        RuleConfig loaded = ruleCache.getActiveRules().get(event.ruleCode());
        Duration lag = event.changedAt() == null ? Duration.ZERO : Duration.between(event.changedAt(), clock.instant());
        meterRegistry.timer("sentinel_rule_reload_lag").record(lag);
        log.info("Hot-reloaded rules after change to {} v{} by {} (now loaded: {}, commit-to-live {} ms)",
                event.ruleCode(), event.version(), event.changedBy(),
                loaded == null ? "inactive" : "v" + loaded.version(), lag.toMillis());
    }
}
