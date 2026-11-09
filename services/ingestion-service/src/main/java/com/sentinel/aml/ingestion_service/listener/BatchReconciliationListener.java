package com.sentinel.aml.ingestion_service.listener;

import com.sentinel.aml.ingestion_service.entity.IngestionBatch;
import com.sentinel.aml.ingestion_service.repository.IngestionBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchReconciliationListener {

    private final IngestionBatchRepository batchRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupStaleBatches() {
        log.info("Running startup reconciliation for stale ingestion batches...");

        List<IngestionBatch> staleBatches = batchRepository.findByStatus("PROCESSING");

        if (staleBatches.isEmpty()) {
            log.info("No stale batches found. Startup reconciliation complete.");
            return;
        }

        for (IngestionBatch batch : staleBatches) {
            batch.setStatus("FAILED");
            log.warn("Marking stale batch {} as FAILED due to unexpected system restart.", batch.getId());
        }

        batchRepository.saveAll(staleBatches);
        log.info("Successfully reconciled {} stale batches.", staleBatches.size());
    }
}