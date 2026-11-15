package com.sentinel.aml.ingestion_service.service.impl;

import com.sentinel.aml.ingestion_service.dto.BatchStatusResponse;
import com.sentinel.aml.ingestion_service.entity.IngestionBatch;
import com.sentinel.aml.ingestion_service.repository.IngestionBatchRepository;
import com.sentinel.aml.ingestion_service.repository.IngestionErrorRepository;
import com.sentinel.aml.ingestion_service.service.BatchIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class BatchIngestionServiceImpl implements BatchIngestionService {

    private final JobLauncher jobLauncher;
    private final Job csvImportJob;
    private final IngestionBatchRepository batchRepository;
    private final IngestionErrorRepository errorRepository;
    private final Executor batchTaskExecutor;

    public BatchIngestionServiceImpl(
            JobLauncher jobLauncher,
            @Qualifier("csvImportJob") Job csvImportJob,
            IngestionBatchRepository batchRepository,
            IngestionErrorRepository errorRepository,
            @Qualifier("batchTaskExecutor") Executor batchTaskExecutor) {
        this.jobLauncher = jobLauncher;
        this.csvImportJob = csvImportJob;
        this.batchRepository = batchRepository;
        this.errorRepository = errorRepository;
        this.batchTaskExecutor = batchTaskExecutor;
    }

    @Override
    public String processBatchAsync(MultipartFile file) throws IOException {
        String batchId = UUID.randomUUID().toString();
        Path tempFile = Files.createTempFile("batch-", ".csv");
        file.transferTo(tempFile.toFile());

        batchRepository.save(IngestionBatch.builder()
                .id(batchId)
                .status("PROCESSING")
                .totalRecords(0)
                .failedRecords(0)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());

        CompletableFuture.runAsync(() -> {
            try {
                JobParameters parameters = new JobParametersBuilder()
                        .addString("batchId", batchId)
                        .addString("filePath", tempFile.toAbsolutePath().toString())
                        .toJobParameters();
                // Final status + counts are written by BatchCompletionListener
                jobLauncher.run(csvImportJob, parameters);
            } catch (Exception e) {
                log.error("Batch {} could not be launched", batchId, e);
                batchRepository.findById(batchId).ifPresent(b -> {
                    b.setStatus("FAILED");
                    b.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
                    batchRepository.save(b);
                });
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.error("Failed to delete temporary file {}", tempFile, e);
                }
            }
        }, batchTaskExecutor);

        return batchId;
    }

    @Override
    public Optional<BatchStatusResponse> getBatchStatus(String batchId, int maxErrors) {
        return batchRepository.findById(batchId).map(b -> {
            var errors = errorRepository.findByBatchIdOrderByLineNumberAsc(batchId, PageRequest.of(0, maxErrors))
                    .stream()
                    .map(e -> new BatchStatusResponse.RowError(e.getLineNumber(), e.getErrorReason(), e.getRawData()))
                    .toList();
            Integer total = b.getTotalRecords();
            Integer failed = b.getFailedRecords();
            Integer accepted = (total == null || failed == null) ? null : total - failed;
            return new BatchStatusResponse(b.getId(), b.getStatus(), total, accepted, failed,
                    b.getCreatedAt(), b.getCompletedAt(), errors);
        });
    }
}
