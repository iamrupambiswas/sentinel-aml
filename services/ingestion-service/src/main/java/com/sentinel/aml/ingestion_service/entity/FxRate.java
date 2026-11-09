package com.sentinel.aml.ingestion_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fx_rate")
@Data
public class FxRate {
    @Id
    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "rate_to_inr", nullable = false)
    private BigDecimal rateToInr;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}