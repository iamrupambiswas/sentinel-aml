package com.sentinel.aml.ingestion_service.controller;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.ingestion_service.entity.IngestionBatch;
import com.sentinel.aml.ingestion_service.repository.IngestionBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(ApiRoutes.INGESTION_BATCHES)
@RequiredArgsConstructor
@Slf4j
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job csvImportJob;
    private final IngestionBatchRepository batchRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<String>> uploadBatch(@RequestParam("file") MultipartFile file) {
        String batchId = UUID.randomUUID().toString();

        try {
            // Save file temporarily for the Batch Reader
            Path tempFile = Files.createTempFile("batch-", ".csv");
            file.transferTo(tempFile.toFile());

            // Create tracking record
            IngestionBatch batch = IngestionBatch.builder()
                    .id(batchId)
                    .status("PROCESSING")
                    .createdAt(LocalDateTime.now())
                    .build();
            batchRepository.save(batch);

            // Launch job asynchronously so we return 202 Accepted immediately
            CompletableFuture.runAsync(() -> {
                try {
                    JobParameters params = new JobParametersBuilder()
                            .addString("batchId", batchId)
                            .addString("filePath", tempFile.toAbsolutePath().toString())
                            .addLong("time", System.currentTimeMillis())
                            .toJobParameters();

                    jobLauncher.run(csvImportJob, params);

                    batch.setStatus("COMPLETED");
                    batch.setCompletedAt(LocalDateTime.now());
                    batchRepository.save(batch);

                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.error("Batch {} failed", batchId, e);
                    batch.setStatus("FAILED");
                    batchRepository.save(batch);
                }
            });

            return ResponseEntity.accepted().body(ApiResponse.<String>builder()
                    .success(true)
                    .message("Batch ingestion started")
                    .data(batchId)
                    .build());

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.<String>builder()
                    .success(false)
                    .message("Failed to process uploaded file: " + e.getMessage())
                    .build());
        }
    }
}