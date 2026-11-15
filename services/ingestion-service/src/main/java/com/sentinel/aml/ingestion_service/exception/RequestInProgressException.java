package com.sentinel.aml.ingestion_service.exception;

/** Same Idempotency-Key is currently being processed by another request. */
public class RequestInProgressException extends RuntimeException {
    public RequestInProgressException(String message) {
        super(message);
    }
}
