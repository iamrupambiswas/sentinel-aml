package com.sentinel.aml.ingestion_service.service;

import com.sentinel.aml.ingestion_service.dto.BatchStatusResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

public interface BatchIngestionService {
    String processBatchAsync(MultipartFile file) throws IOException;

    Optional<BatchStatusResponse> getBatchStatus(String batchId, int maxErrors);
}
