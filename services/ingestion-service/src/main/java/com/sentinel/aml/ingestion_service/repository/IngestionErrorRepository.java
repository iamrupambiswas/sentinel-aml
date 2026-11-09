package com.sentinel.aml.ingestion_service.repository;

import com.sentinel.aml.ingestion_service.entity.IngestionError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngestionErrorRepository extends JpaRepository<IngestionError, String> {
    List<IngestionError> findByBatchId(String batchId);
}