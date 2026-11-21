package com.sentinel.aml.alert_case_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sentinel.aml.alert_case_service.entity.Alert;
import com.sentinel.aml.alert_case_service.entity.AlertHit;
import com.sentinel.aml.alert_case_service.entity.AlertStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Request/response shapes of the alerts API. */
public final class AlertDtos {

    private AlertDtos() {}

    public record AlertSummary(String id, String accountId, String ruleCode, String windowBucket,
                               AlertStatus status, BigDecimal riskScore, int hitCount,
                               String latestExplanation, LocalDateTime firstTxnAt, LocalDateTime lastTxnAt,
                               LocalDateTime lastDetectedAt, int version) {
        public static AlertSummary from(Alert a) {
            return new AlertSummary(a.getId(), a.getAccountId(), a.getRuleCode(), a.getWindowBucket(),
                    a.getStatus(), a.getRiskScore(), a.getHitCount(), a.getLatestExplanation(),
                    a.getFirstTxnAt(), a.getLastTxnAt(), a.getLastDetectedAt(), a.getVersion());
        }
    }

    public record HitView(String eventId, String transactionId, String ruleCode, BigDecimal weight,
                          BigDecimal riskScore, String explanation, List<String> evidenceTxnIds,
                          LocalDateTime txnTimestamp, LocalDateTime detectedAt) {
        public static HitView from(AlertHit h, List<String> evidence) {
            return new HitView(h.getEventId(), h.getTransactionId(), h.getRuleCode(), h.getWeight(),
                    h.getRiskScore(), h.getExplanation(), evidence, h.getTxnTimestamp(), h.getDetectedAt());
        }
    }

    /** @param caseId the investigation case this alert belongs to, or null (Phase 7b) */
    public record AlertDetail(AlertSummary alert, String dedupKey, String statusNote,
                              LocalDateTime statusChangedAt, List<AlertStatus> allowedNextStatuses,
                              List<HitView> hits, Long caseId) {}

    /**
     * @param expectedVersion optional optimistic-lock check: the version the analyst was looking at.
     *                        If someone else changed the status meanwhile, the update is rejected (409).
     */
    public record StatusChangeRequest(@NotNull AlertStatus status,
                                      @Size(max = 2000) String note,
                                      Integer expectedVersion) {}

    /** hits / alerts shows how much noise de-duplication removed. */
    public record AlertStats(long alerts, long hits, Map<AlertStatus, Long> alertsByStatus) {
        @JsonProperty("hitsPerAlert")   // record methods that are not components are not serialized by default
        public double hitsPerAlert() {
            return alerts == 0 ? 0 : Math.round((double) hits / alerts * 100.0) / 100.0;
        }
    }
}
