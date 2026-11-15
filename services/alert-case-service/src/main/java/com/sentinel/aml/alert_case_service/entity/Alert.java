package com.sentinel.aml.alert_case_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Rows are CREATED and their counters UPDATED only by AlertIngestService (plain SQL, atomic).
 * JPA is used for reads and for analyst status changes.
 *
 * {@code @DynamicUpdate}: a status change writes only the columns that changed. Without it,
 * Hibernate would write back every column, overwriting a hit_count that the consumer
 * incremented after the analyst loaded the alert.
 */
@Entity
@Table(name = "alert")
@DynamicUpdate
@Getter
@Setter
public class Alert {

    @Id
    private String id;

    @Column(name = "dedup_key", nullable = false, updatable = false)
    private String dedupKey;

    @Column(name = "account_id", nullable = false, updatable = false)
    private String accountId;

    @Column(name = "rule_code", nullable = false, updatable = false)
    private String ruleCode;

    @Column(name = "window_bucket", nullable = false, updatable = false)
    private String windowBucket;

    // VARCHAR, not a MySQL ENUM: Hibernate 6 would otherwise expect ENUM(...) and fail schema validation,
    // and adding a status later would need an ALTER TABLE.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private AlertStatus status;

    @Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "latest_explanation", nullable = false, columnDefinition = "TEXT")
    private String latestExplanation;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "first_txn_at", nullable = false)
    private LocalDateTime firstTxnAt;

    @Column(name = "last_txn_at", nullable = false)
    private LocalDateTime lastTxnAt;

    @Column(name = "first_detected_at", nullable = false)
    private LocalDateTime firstDetectedAt;

    @Column(name = "last_detected_at", nullable = false)
    private LocalDateTime lastDetectedAt;

    @Column(name = "status_note", columnDefinition = "TEXT")
    private String statusNote;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
