package com.sentinel.aml.ingestion_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "ingestion_error")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionError {
    @Id
    private String id;

    @Column(name = "batch_id", nullable = false)
    private String batchId;

    @Column(name = "line_number")
    private Integer lineNumber; // <-- Renamed from rowNumber

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Column(name = "error_reason", columnDefinition = "TEXT", nullable = false)
    private String errorReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}