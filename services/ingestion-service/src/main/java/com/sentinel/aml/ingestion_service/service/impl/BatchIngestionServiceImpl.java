package com.sentinel.aml.ingestion_service.service.impl;

import com.sentinel.aml.ingestion_service.entity.IngestionBatch;
import com.sentinel.aml.ingestion_service.repository.IngestionBatchRepository;
import com.sentinel.aml.ingestion_service.service.BatchIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class BatchIngestionServiceImpl implements BatchIngestionService {

    private final JobLauncher jobLauncher;
    private final Job csvImportJob;
    private final IngestionBatchRepository batchRepository;
    private final Executor batchTaskExecutor;

    public BatchIngestionServiceImpl(
            JobLauncher jobLauncher,
            @Qualifier("csvImportJob") Job csvImportJob,
            IngestionBatchRepository batchRepository,
            @Qualifier("batchTaskExecutor") Executor batchTaskExecutor) {
        this.jobLauncher = jobLauncher;
        this.csvImportJob = csvImportJob;
        this.batchRepository = batchRepository;
        this.batchTaskExecutor = batchTaskExecutor;
    }

    @Override
    public String processBatchAsync(MultipartFile file) throws IOException {
        String batchId = UUID.randomUUID().toString();
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
                JobParameters parameters = new JobParametersBuilder()
                        .addString("batchId", batchId)
                        .addString("filePath", tempFile.toAbsolutePath().toString())
                        .addLong("time", System.currentTimeMillis())
                        .toJobParameters();

                JobExecution execution = jobLauncher.run(csvImportJob, parameters);

                batch.setStatus(execution.getStatus().toString());
                batchRepository.save(batch);

            } catch (Exception e) {
                log.error("Batch processing failed for ID: {}", batchId, e);
                batch.setStatus("FAILED");
                batchRepository.save(batch);
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                    log.debug("Temporary file successfully deleted: {}", tempFile);
                } catch (IOException e) {
                    log.error("CRITICAL: Failed to delete temporary file: {}", tempFile, e);
                }
            }
        }, batchTaskExecutor);

        return batchId;
    }
}