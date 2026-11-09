package com.sentinel.aml.ingestion_service.controller;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.ingestion_service.entity.IngestionBatch;
import com.sentinel.aml.ingestion_service.repository.IngestionBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
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

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .success(false)
                    .message("The uploaded file is empty.")
                    .build());
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .success(false)
                    .message("Invalid file format. The Ingestion Engine only accepts .csv files.")
                    .build());
        }

        String batchId = UUID.randomUUID().toString();

        try {
            Path tempFile = Files.createTempFile("batch-", ".csv");
            file.transferTo(tempFile.toFile());

            IngestionBatch batch = IngestionBatch.builder()
                    .id(batchId)
                    .status("PROCESSING")
                    .createdAt(LocalDateTime.now())
                    .build();
            batchRepository.save(batch);

            CompletableFuture.runAsync(() -> {
                try {
                    JobParameters params = new JobParametersBuilder()
                            .addString("batchId", batchId)
                            .addString("filePath", tempFile.toAbsolutePath().toString())
                            .addLong("time", System.currentTimeMillis())
                            .toJobParameters();

                    JobExecution execution = jobLauncher.run(csvImportJob, params);

                    // 1. Calculate Metrics from the Spring Batch execution context
                    int readCount = 0;
                    int filterCount = 0;
                    int skipCount = 0;

                    for (StepExecution step : execution.getStepExecutions()) {
                        readCount += step.getReadCount(); // Successfully read from CSV
                        filterCount += step.getFilterCount(); // Failed in processor (returned null)
                        skipCount += step.getSkipCount(); // Failed in Reader (bad commas)
                    }

                    // 2. Save final stats
                    batch.setStatus(execution.getStatus().toString());
                    batch.setTotalRecords(readCount + skipCount);
                    batch.setFailedRecords(filterCount + skipCount);
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