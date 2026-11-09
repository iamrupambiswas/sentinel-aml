package com.sentinel.aml.ingestion_service.batch;

import com.sentinel.aml.ingestion_service.dto.ProcessedTransaction;
import com.sentinel.aml.ingestion_service.dto.TransactionCsvDto;
import com.sentinel.aml.ingestion_service.entity.IngestionError;
import com.sentinel.aml.ingestion_service.repository.IngestionErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
// 1. Removed @StepScope completely to prevent proxy injection bugs
@RequiredArgsConstructor
@Slf4j
public class CsvSkipListener implements SkipListener<TransactionCsvDto, ProcessedTransaction>, StepExecutionListener {

    private final IngestionErrorRepository errorRepository;
    private String batchId;

    // 2. Safely grab the batchId before the step even starts
    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.batchId = stepExecution.getJobParameters().getString("batchId");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSkipInProcess(TransactionCsvDto item, Throwable t) {
        try {
            // Absolute guarantee against nulls
            String errorMessage = "Unknown Error";
            if (t != null) {
                if (t.getMessage() != null) {
                    errorMessage = t.getMessage();
                } else {
                    errorMessage = t.getClass().getSimpleName() + " occurred without a message.";
                }
            }

            if (errorMessage.length() > 250) {
                errorMessage = errorMessage.substring(0, 250);
            }

            log.warn("Intercepted rule violation for {}: {}", item.getSourceAccountId(), errorMessage);

            IngestionError error = IngestionError.builder()
                    .id(UUID.randomUUID().toString())
                    .batchId(batchId)
                    .lineNumber(item.getLineNumber()) // <-- Now guaranteed by ItemCountAware
                    .rawData(item.toString())
                    .errorReason(errorMessage) // Guaranteed NOT NULL
                    .createdAt(LocalDateTime.now())
                    .build();

            errorRepository.save(error);
            log.info("Successfully saved error log to DB for: {}", item.getSourceAccountId());

        } catch (Exception e) {
            log.error("CRITICAL: Failed to save to ingestion_error table! Reason: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSkipInRead(Throwable t) {
        try {
            int lineNo = 0;
            String rawInput = "Unknown raw data";

            // Extract exact line number and raw input from the parsing exception
            if (t instanceof org.springframework.batch.item.file.FlatFileParseException ffpe) {
                lineNo = ffpe.getLineNumber();
                rawInput = ffpe.getInput();
            }

            String errorMessage = t.getMessage() != null ? t.getMessage() : "Parsing failed";
            if (errorMessage.length() > 250) {
                errorMessage = errorMessage.substring(0, 250);
            }

            log.warn("Skipping fundamentally broken line at {}: {}", lineNo, errorMessage);

            IngestionError error = IngestionError.builder()
                    .id(UUID.randomUUID().toString())
                    .batchId(batchId)
                    .lineNumber(lineNo > 0 ? lineNo : null)
                    .rawData(rawInput)
                    .errorReason("STRUCTURAL ERROR: " + errorMessage)
                    .createdAt(LocalDateTime.now())
                    .build();

            errorRepository.save(error);
        } catch (Exception e) {
            log.error("Failed to save structural error to DB: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onSkipInWrite(ProcessedTransaction item, Throwable t) {
        log.error("Failed to write valid transaction to DB: {}", t.getMessage());
    }
}