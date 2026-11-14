package com.sentinel.aml.detection_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "aml_rule")
@Getter
@Setter
public class AmlRule {
    @Id
    @Column(name = "rule_code")
    private String ruleCode;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Meaning depends on the rule: an INR amount, a count, a percentage or a multiplier. */
    @Column(name = "threshold_amount", nullable = false)
    private BigDecimal thresholdAmount;

    @Column(name = "window_minutes", nullable = false)
    private long windowMinutes;

    // DECIMAL(5,2) in the DB - must be BigDecimal or Hibernate's schema validation fails
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "is_active")
    private boolean active;

    /** Rule-specific parameters as JSON, e.g. {"minAmountInr": 751500}. */
    @Column(columnDefinition = "JSON")
    private String params;

    @Column(nullable = false)
    private int version;
}
