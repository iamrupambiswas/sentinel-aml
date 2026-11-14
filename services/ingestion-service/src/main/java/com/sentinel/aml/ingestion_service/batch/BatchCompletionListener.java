package com.sentinel.aml.ingestion_service.batch;

import com.sentinel.aml.ingestion_service.repository.IngestionBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Writes final status and row counts to ingestion_batch when the job ends. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchCompletionListener implements JobExecutionListener {

    private final IngestionBatchRepository batchRepository;

    @Override
    public void afterJob(JobExecution jobExecution) {
        String batchId = jobExecution.getJobParameters().getString("batchId");
        long read = 0, readSkips = 0, processSkips = 0, writeSkips = 0, written = 0;
        for (StepExecution step : jobExecution.getStepExecutions()) {
            read += step.getReadCount();
            readSkips += step.getReadSkipCount();
            processSkips += step.getProcessSkipCount();
            writeSkips += step.getWriteSkipCount();
            written += step.getWriteCount();
        }
        long total = read + readSkips;
        long failed = readSkips + processSkips + writeSkips;
        String status = jobExecution.getStatus() == BatchStatus.COMPLETED ? "COMPLETED" : "FAILED";
        final long acceptedRows = written;

        batchRepository.findById(batchId).ifPresent(batch -> {
            batch.setStatus(status);
            batch.setTotalRecords((int) total);
            batch.setFailedRecords((int) failed);
            batch.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
            batchRepository.save(batch);
        });
        log.info("Batch {} {}: total={}, accepted={}, failed={}", batchId, status, total, acceptedRows, failed);
    }
}
