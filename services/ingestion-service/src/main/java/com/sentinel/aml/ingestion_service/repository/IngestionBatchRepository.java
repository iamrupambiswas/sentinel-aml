package com.sentinel.aml.ingestion_service.repository;

import com.sentinel.aml.ingestion_service.entity.IngestionBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngestionBatchRepository extends JpaRepository<IngestionBatch, String> {

    List<IngestionBatch> findByStatus(String status);

}