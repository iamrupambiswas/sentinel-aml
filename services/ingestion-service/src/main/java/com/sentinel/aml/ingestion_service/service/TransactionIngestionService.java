package com.sentinel.aml.ingestion_service.service;

import com.sentinel.aml.ingestion_service.dto.IngestionResult;
import com.sentinel.aml.ingestion_service.dto.TransactionRequest;

public interface TransactionIngestionService {
    IngestionResult ingestTransaction(TransactionRequest request, String idempotencyKey);
}
