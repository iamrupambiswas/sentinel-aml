package com.sentinel.aml.ingestion_service.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BatchStatusResponse(
        String batchId,
        String status,
        Integer totalRecords,
        Integer acceptedRecords,
        Integer failedRecords,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<RowError> errors
) {
    public record RowError(Integer lineNumber, String reason, String rawData) {}
}
