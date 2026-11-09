package com.sentinel.aml.ingestion_service.controller;

import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.ingestion_service.service.BatchIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ingestion/batches")
@RequiredArgsConstructor
@Slf4j
public class BatchController {

    private final BatchIngestionService batchService;

    @PostMapping
    public ResponseEntity<ApiResponse<String>> uploadBatch(@RequestParam("file") MultipartFile file) {

        // 1. Guardrails (Size & Extension)
        if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .success(false).message("Invalid file format. Only non-empty .csv files are accepted.").build());
        }

        try {
            // 2. Delegate everything to the Service
            String batchId = batchService.processBatchAsync(file);

            return ResponseEntity.accepted().body(ApiResponse.<String>builder()
                    .success(true).message("Batch accepted for processing. ID: " + batchId).build());

        } catch (Exception e) {
            // FIX POINT 5: Obfuscate the error for the client, log the truth internally
            log.error("Fatal error intercepting batch upload", e);
            return ResponseEntity.internalServerError().body(ApiResponse.<String>builder()
                    .success(false).message("An unexpected error occurred during ingestion initialization.").build());
        }
    }
}