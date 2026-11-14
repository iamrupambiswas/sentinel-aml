package com.sentinel.aml.detection_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "aml_rule")
@Data
public class AmlRule {
    @Id
    private String ruleCode;
    private String ruleName;
    private String description;
    private BigDecimal thresholdAmount;
    private long windowMinutes;

    // Add the weight field to resolve the error
    private double weight;

    private boolean isActive;
}