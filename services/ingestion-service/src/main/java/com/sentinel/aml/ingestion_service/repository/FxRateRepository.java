package com.sentinel.aml.ingestion_service.repository;

import com.sentinel.aml.ingestion_service.entity.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRate, String> {

}