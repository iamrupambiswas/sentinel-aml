package com.sentinel.aml.ingestion_service.exception;

/** A business-rule violation in an incoming transaction (unknown account, unsupported currency...). */
public class InvalidTransactionException extends RuntimeException {
    public InvalidTransactionException(String message) {
        super(message);
    }
}
