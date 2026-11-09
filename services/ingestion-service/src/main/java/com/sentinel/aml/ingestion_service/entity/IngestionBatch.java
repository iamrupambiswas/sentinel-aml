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
@Table(name = "ingestion_batch")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionBatch {
    @Id
    private String id;

    @Column(nullable = false)
    private String status; // PROCESSING, COMPLETED, FAILED

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "failed_records")
    private Integer failedRecords;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}