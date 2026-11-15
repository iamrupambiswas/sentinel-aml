package com.sentinel.aml.ingestion_service.dto;

/**
 * @param replayed true when this is a retry with an Idempotency-Key we already processed;
 *                 the original transactionId is returned and nothing new is stored (X1).
 */
public record IngestionResult(String transactionId, boolean replayed) {}
