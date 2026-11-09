package com.sentinel.aml.ingestion_service.service;

import com.sentinel.aml.ingestion_service.dto.TransactionRequest;

public interface TransactionIngestionService {
    String ingestTransaction(TransactionRequest request, String idempotencyKey);
}