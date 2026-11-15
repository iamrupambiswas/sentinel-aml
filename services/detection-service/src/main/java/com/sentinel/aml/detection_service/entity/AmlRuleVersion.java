package com.sentinel.aml.detection_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable snapshot of one aml_rule version (P13 audit trail). Insert-only. */
@Entity
@Table(name = "aml_rule_version")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmlRuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Column(nullable = false)
    private int version;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "threshold_amount", nullable = false)
    private BigDecimal thresholdAmount;

    @Column(name = "window_minutes", nullable = false)
    private long windowMinutes;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(columnDefinition = "JSON")
    private String params;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    @Column(name = "change_reason", nullable = false)
    private String changeReason;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static AmlRuleVersion snapshotOf(AmlRule rule, String changedBy, String reason, LocalDateTime at) {
        return AmlRuleVersion.builder()
                .ruleCode(rule.getRuleCode())
                .version(rule.getVersion())
                .ruleName(rule.getRuleName())
                .thresholdAmount(rule.getThresholdAmount())
                .windowMinutes(rule.getWindowMinutes())
                .weight(rule.getWeight())
                .active(rule.isActive())
                .params(rule.getParams())
                .changedBy(changedBy)
                .changeReason(reason)
                .changedAt(at)
                .build();
    }
}
