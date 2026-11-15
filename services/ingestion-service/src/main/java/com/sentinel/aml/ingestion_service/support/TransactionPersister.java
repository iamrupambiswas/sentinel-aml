package com.sentinel.aml.ingestion_service.support;

import com.sentinel.aml.ingestion_service.dto.ProcessedTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes transaction + outbox row in the SAME DB transaction (transactional outbox).
 * Uses persist() instead of repository.save(): our ids are pre-assigned UUIDs, so save()
 * would call merge() and fire an extra SELECT per row - a big cost on a 10k-row CSV.
 */
@Component
public class TransactionPersister {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void persistAll(List<? extends ProcessedTransaction> items) {
        for (ProcessedTransaction item : items) {
            entityManager.persist(item.getTransaction());
            entityManager.persist(item.getOutboxEvent());
        }
        entityManager.flush();
        entityManager.clear(); // keep the persistence context small across chunks
    }
}
