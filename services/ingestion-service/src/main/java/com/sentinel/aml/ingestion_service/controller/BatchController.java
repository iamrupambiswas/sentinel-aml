package com.sentinel.aml.ingestion_service.controller;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.ingestion_service.dto.BatchStatusResponse;
import com.sentinel.aml.ingestion_service.service.BatchIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiRoutes.INGESTION_BATCHES)
@RequiredArgsConstructor
@Slf4j
public class BatchController {

    private final BatchIngestionService batchService;

    @PostMapping
    public ResponseEntity<ApiResponse<String>> uploadBatch(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .success(false).message("Invalid file format. Only non-empty .csv files are accepted.").build());
        }
        try {
            String batchId = batchService.processBatchAsync(file);
            return ResponseEntity.accepted()
                    .header("Location", ApiRoutes.INGESTION_BATCHES + "/" + batchId)
                    .body(ApiResponse.<String>builder()
                            .success(true).message("Batch accepted for processing").data(batchId).build());
        } catch (Exception e) {
            log.error("Fatal error initialising batch upload", e);
            return ResponseEntity.internalServerError().body(ApiResponse.<String>builder()
                    .success(false).message("An unexpected error occurred during ingestion initialization.").build());
        }
    }

    /** Status + rejected-row report (P4). */
    @GetMapping("/{batchId}")
    public ResponseEntity<ApiResponse<BatchStatusResponse>> getBatch(
            @PathVariable("batchId") String batchId,
            @RequestParam(name = "maxErrors", defaultValue = "100") int maxErrors) {
        return batchService.getBatchStatus(batchId, Math.min(Math.max(maxErrors, 0), 1000))
                .map(s -> ResponseEntity.ok(ApiResponse.<BatchStatusResponse>builder().success(true).data(s).build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<BatchStatusResponse>builder()
                        .success(false).message("Batch not found: " + batchId).build()));
    }
}
