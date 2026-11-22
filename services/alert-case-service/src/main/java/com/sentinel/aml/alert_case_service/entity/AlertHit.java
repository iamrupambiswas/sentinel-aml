package com.sentinel.aml.alert_case_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One hit attached to an alert. Written by AlertIngestService; read-only for JPA. */
@Entity
@Table(name = "alert_hit")
@Immutable
@Getter
public class AlertHit {

    @Id
    private Long id;

    @Column(name = "alert_id")
    private String alertId;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "rule_code")
    private String ruleCode;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** JSON array of transaction ids. */
    @Column(name = "evidence_txn_ids", columnDefinition = "JSON")
    private String evidenceTxnIds;

    @Column(name = "txn_timestamp")
    private LocalDateTime txnTimestamp;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;
}
