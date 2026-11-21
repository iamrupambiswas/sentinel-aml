package com.sentinel.aml.detection_service.rules;

import com.sentinel.aml.common.audit.AuditPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.detection_service.engine.DetectionRule;
import com.sentinel.aml.detection_service.engine.RuleConfig;
import com.sentinel.aml.detection_service.entity.AmlRule;
import com.sentinel.aml.detection_service.entity.AmlRuleVersion;
import com.sentinel.aml.detection_service.repository.AmlRuleRepository;
import com.sentinel.aml.detection_service.repository.AmlRuleVersionRepository;
import com.sentinel.aml.detection_service.rules.RuleAdminDtos.RuleUpdateRequest;
import com.sentinel.aml.detection_service.rules.RuleAdminDtos.RuleVersionView;
import com.sentinel.aml.detection_service.rules.RuleAdminDtos.RuleView;
import com.sentinel.aml.detection_service.service.RuleCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rule administration (P13): versioned, audited, optimistic-locked changes that go live on every
 * detection pod within seconds.
 *
 * Flow of an update, all in ONE DB transaction:
 *   check expectedVersion -> validate -> apply -> flush (Hibernate bumps @Version)
 *   -> insert aml_rule_version snapshot -> raise RuleChangedApplicationEvent.
 * {@link RuleChangePublisher} sends the broker message only AFTER that transaction commits.
 */
@Service
@Slf4j
public class RuleAdminService {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};

    private final AmlRuleRepository ruleRepository;
    private final AmlRuleVersionRepository versionRepository;
    private final RuleCacheService ruleCache;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, DetectionRule> rulesByCode;
    private final AuditPublisher audit;

    public RuleAdminService(AmlRuleRepository ruleRepository, AmlRuleVersionRepository versionRepository,
                            RuleCacheService ruleCache, ApplicationEventPublisher events, ObjectMapper objectMapper,
                            Clock clock, List<DetectionRule> rules, AuditPublisher audit) {
        this.ruleRepository = ruleRepository;
        this.versionRepository = versionRepository;
        this.ruleCache = ruleCache;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.rulesByCode = rules.stream().collect(Collectors.toMap(DetectionRule::getRuleCode, Function.identity()));
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RuleView> list() {
        Map<String, RuleConfig> live = ruleCache.getActiveRules();
        return ruleRepository.findAll().stream()
                .sorted(Comparator.comparing(AmlRule::getRuleCode))
                .map(r -> toView(r, live))
                .toList();
    }

    @Transactional(readOnly = true)
    public RuleView get(String code) {
        return toView(find(code), ruleCache.getActiveRules());
    }

    @Transactional(readOnly = true)
    public List<RuleVersionView> history(String code) {
        find(code);
        return versionRepository.findByRuleCodeOrderByVersionDesc(code).stream()
                .map(v -> new RuleVersionView(v.getVersion(), v.getThresholdAmount(), v.getWindowMinutes(),
                        v.getWeight(), v.isActive(), parse(v.getParams()), v.getChangedBy(),
                        v.getChangeReason(), v.getChangedAt()))
                .toList();
    }

    @Transactional
    public RuleView update(String code, RuleUpdateRequest req, String changedBy) {
        AmlRule rule = find(code);
        if (rule.getVersion() != req.expectedVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, String.format(
                    "Rule %s is at version %d, you edited version %d. Reload, re-apply your change and retry.",
                    code, rule.getVersion(), req.expectedVersion()));
        }
        if (req.params() != null) {
            validateParams(code, req.params());
        }
        boolean changed = false;
        if (req.thresholdAmount() != null && req.thresholdAmount().compareTo(rule.getThresholdAmount()) != 0) {
            rule.setThresholdAmount(req.thresholdAmount());
            changed = true;
        }
        if (req.windowMinutes() != null && req.windowMinutes() != rule.getWindowMinutes()) {
            rule.setWindowMinutes(req.windowMinutes());
            changed = true;
        }
        if (req.weight() != null && req.weight().compareTo(rule.getWeight()) != 0) {
            rule.setWeight(req.weight());
            changed = true;
        }
        if (req.active() != null && req.active() != rule.isActive()) {
            rule.setActive(req.active());
            changed = true;
        }
        if (req.params() != null) {
            String json = toJson(req.params());
            if (!req.params().equals(parse(rule.getParams()))) {
                rule.setParams(json);
                changed = true;
            }
        }
        if (!changed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nothing to change: every field equals the current value of rule " + code);
        }

        try {
            ruleRepository.saveAndFlush(rule); // @Version: UPDATE ... WHERE version = old -> bumps to old + 1
        } catch (ObjectOptimisticLockingFailureException e) {
            // Someone committed between our read and our write
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rule " + code + " was changed concurrently. Reload and retry.");
        }

        Instant now = clock.instant();
        versionRepository.save(AmlRuleVersion.snapshotOf(rule, changedBy, req.reason(),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC)));
        events.publishEvent(new RuleChangedApplicationEvent(code, rule.getVersion(), changedBy, now));
        log.info("Rule {} updated to version {} by {}: {}", code, rule.getVersion(), changedBy, req.reason());
        audit.record("RULE_CHANGED", "RULE", code, Map.of("version", rule.getVersion(), "reason", req.reason()));
        // loadedVersion still shows the OLD version here: the reload happens after commit
        return toView(rule, ruleCache.getActiveRules());
    }

    private void validateParams(String code, Map<String, Object> params) {
        DetectionRule rule = rulesByCode.get(code);
        Set<String> supported = rule == null ? Set.of() : rule.supportedParams();
        Set<String> unknown = new HashSet<>(params.keySet());
        unknown.removeAll(supported);
        if (!unknown.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format(
                    "Unknown params for %s: %s. Supported: %s", code, unknown, supported.isEmpty() ? "none" : supported));
        }
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (!(e.getValue() instanceof Number) || ((Number) e.getValue()).doubleValue() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Param '" + e.getKey() + "' must be a non-negative number, got: " + e.getValue());
            }
        }
    }

    private RuleView toView(AmlRule r, Map<String, RuleConfig> live) {
        RuleConfig loaded = live.get(r.getRuleCode());
        DetectionRule impl = rulesByCode.get(r.getRuleCode());
        return new RuleView(r.getRuleCode(), r.getRuleName(), r.getDescription(), r.getThresholdAmount(),
                r.getWindowMinutes(), r.getWeight(), r.isActive(), parse(r.getParams()), r.getVersion(),
                loaded == null ? null : loaded.version(), impl == null ? Set.of() : impl.supportedParams());
    }

    private AmlRule find(String code) {
        return ruleRepository.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown rule: " + code));
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, JSON_MAP);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "params is not valid JSON");
        }
    }
}
