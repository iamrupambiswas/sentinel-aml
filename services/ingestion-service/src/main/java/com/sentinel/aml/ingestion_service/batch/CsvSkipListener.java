package com.sentinel.aml.ingestion_service.batch;

import com.sentinel.aml.ingestion_service.dto.ProcessedTransaction;
import com.sentinel.aml.ingestion_service.dto.TransactionCsvDto;
import com.sentinel.aml.ingestion_service.entity.IngestionError;
import com.sentinel.aml.ingestion_service.repository.IngestionErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Records every rejected row in ingestion_error.
 * <p>
 * This is a singleton shared by all concurrently running batches, so it must NOT keep the
 * batchId in a field (the old version did, and two parallel uploads overwrote each other's id).
 * The batchId is read from the step context of the thread that is reporting the skip.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CsvSkipListener implements SkipListener<TransactionCsvDto, ProcessedTransaction> {

    private static final int MAX_REASON = 1000;

    private final IngestionErrorRepository errorRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSkipInProcess(TransactionCsvDto item, Throwable t) {
        save(item.getLineNumber(), item.toString(), reason(t));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSkipInRead(Throwable t) {
        Integer lineNo = null;
        String raw = null;
        if (t instanceof FlatFileParseException ffpe) {
            lineNo = ffpe.getLineNumber();
            raw = ffpe.getInput();
        }
        save(lineNo, raw, "STRUCTURAL ERROR: " + reason(t));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSkipInWrite(ProcessedTransaction item, Throwable t) {
        save(null, item.getTransaction().getExternalRef(), "WRITE ERROR: " + reason(t));
    }

    private void save(Integer lineNumber, String raw, String reason) {
        String batchId = currentBatchId();
        log.warn("Batch {} line {} rejected: {}", batchId, lineNumber, reason);
        errorRepository.save(IngestionError.builder()
                .id(UUID.randomUUID().toString())
                .batchId(batchId)
                .lineNumber(lineNumber)
                .rawData(raw)
                .errorReason(reason)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());
    }

    private static String currentBatchId() {
        var ctx = StepSynchronizationManager.getContext();
        if (ctx == null) {
            throw new IllegalStateException("Skip reported outside of a step execution");
        }
        return ctx.getStepExecution().getJobParameters().getString("batchId");
    }

    private static String reason(Throwable t) {
        if (t == null) return "Unknown error";
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        if (root != t && root.getMessage() != null && !msg.contains(root.getMessage())) {
            msg = msg + " (cause: " + root.getMessage() + ")";
        }
        return msg.length() > MAX_REASON ? msg.substring(0, MAX_REASON) : msg;
    }
}
