package com.sentinel.aml.ingestion_service.repository;

import com.sentinel.aml.ingestion_service.entity.IngestionBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionBatchRepository extends JpaRepository<IngestionBatch, String> {
}