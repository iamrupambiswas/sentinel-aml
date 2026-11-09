package com.sentinel.aml.ingestion_service.service;

public interface AccountValidationService {
    boolean isAccountValid(String accountId);
}