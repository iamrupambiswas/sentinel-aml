package com.sentinel.aml.detection_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Detection-side record of a scored rule hit for one account (the blueprint's "rule_hit").
 * The analyst-facing alert with status/de-dup/workflow is owned by alert-case-service (Phase 5).
 */
@Entity
@Table(name = "aml_alert")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmlAlert {
    @Id
    private String id;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    /** Highest-weighted rule (FK to aml_rule). */
    @Column(name = "rule_triggered", nullable = false)
    private String ruleTriggered;

    /** All rules that fired, comma separated. */
    @Column(name = "rule_codes")
    private String ruleCodes;

    @Column(name = "alert_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal alertAmount;

    @Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    /** JSON array of the rule hits with evidence transaction ids. */
    @Column(columnDefinition = "JSON")
    private String evidence;

    @Column(length = 10)
    private String direction;

    @Column(name = "txn_timestamp")
    private LocalDateTime txnTimestamp;

    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
