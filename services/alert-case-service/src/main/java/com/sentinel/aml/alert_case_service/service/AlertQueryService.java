package com.sentinel.aml.alert_case_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.alert_case_service.dto.AlertDtos.AlertDetail;
import com.sentinel.aml.alert_case_service.dto.AlertDtos.AlertStats;
import com.sentinel.aml.alert_case_service.dto.AlertDtos.AlertSummary;
import com.sentinel.aml.alert_case_service.dto.AlertDtos.HitView;
import com.sentinel.aml.alert_case_service.dto.AlertDtos.StatusChangeRequest;
import com.sentinel.aml.alert_case_service.entity.Alert;
import com.sentinel.aml.alert_case_service.entity.AlertStatus;
import com.sentinel.aml.alert_case_service.repository.AlertHitRepository;
import com.sentinel.aml.alert_case_service.repository.AlertRepository;
import com.sentinel.aml.alert_case_service.audit.AuditLog;
import com.sentinel.aml.alert_case_service.cases.CaseService;
import com.sentinel.aml.common.event.AlertChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Analyst-facing reads and the status workflow. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertQueryService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final AlertRepository alertRepository;
    private final AlertHitRepository hitRepository;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;
    private final CaseService cases;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<AlertSummary> search(AlertStatus status, String accountId, String ruleCode, Pageable pageable) {
        return alertRepository.search(status, blankToNull(accountId), blankToNull(ruleCode), pageable)
                .map(AlertSummary::from);
    }

    @Transactional(readOnly = true)
    public AlertDetail get(String id) {
        Alert alert = find(id);
        List<HitView> hits = hitRepository.findByAlertIdOrderByTxnTimestampAsc(id).stream()
                .map(h -> HitView.from(h, parseEvidence(h.getEvidenceTxnIds())))
                .toList();
        return new AlertDetail(AlertSummary.from(alert), alert.getDedupKey(), alert.getStatusNote(),
                alert.getStatusChangedAt(), new ArrayList<>(alert.getStatus().allowedNext()), hits, cases.caseOfAlert(id));
    }

    @Transactional
    public AlertSummary changeStatus(String id, StatusChangeRequest request) {
        Alert alert = find(id);
        if (request.expectedVersion() != null && request.expectedVersion() != alert.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, String.format(
                    "Alert %s was changed by someone else (version %d, you had %d). Reload and retry.",
                    id, alert.getVersion(), request.expectedVersion()));
        }
        AlertStatus from = alert.getStatus();
        if (!from.canMoveTo(request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, String.format(
                    "Cannot move alert from %s to %s. Allowed: %s", from, request.status(), from.allowedNext()));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        alert.setStatus(request.status());
        alert.setStatusNote(request.note());
        alert.setStatusChangedAt(now);
        alert.setUpdatedAt(now);
        try {
            alertRepository.saveAndFlush(alert);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alert " + id + " was changed concurrently. Reload and retry.");
        }
        log.info("Alert {} moved {} -> {}", id, from, request.status());
        // Same transaction: the status change and its audit entry commit together
        auditLog.record("ALERT_STATUS_CHANGED", "ALERT", alert.getId(), request.note() == null || request.note().isBlank()
                ? Map.of("from", from.name(), "to", request.status().name())
                : Map.of("from", from.name(), "to", request.status().name(), "note", request.note()));
        events.publishEvent(new AlertChangedEvent(alert.getId(), alert.getAccountId(), alert.getRuleCode(),
                AlertChangedEvent.Change.STATUS_CHANGED, request.status().name(), clock.instant()));
        return AlertSummary.from(alert);
    }

    @Transactional(readOnly = true)
    public AlertStats stats() {
        Map<AlertStatus, Long> byStatus = new EnumMap<>(AlertStatus.class);
        long alerts = 0, hits = 0;
        for (AlertRepository.StatusCount c : alertRepository.countByStatus()) {
            byStatus.put(c.getStatus(), c.getAlerts());
            alerts += c.getAlerts();
            hits += c.getHits() == null ? 0 : c.getHits();
        }
        return new AlertStats(alerts, hits, byStatus);
    }

    private Alert find(String id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found: " + id));
    }

    private List<String> parseEvidence(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            return List.of(json);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
