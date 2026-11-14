package com.sentinel.aml.detection_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "aml_alert")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmlAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String transactionId;
    private String accountId;
    private String ruleTriggered;
    private BigDecimal alertAmount;
    private LocalDateTime createdAt;
    private String status;
    private Double riskScore;
    private String explanation;
}