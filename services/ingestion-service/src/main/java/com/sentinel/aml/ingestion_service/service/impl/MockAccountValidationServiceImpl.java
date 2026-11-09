package com.sentinel.aml.ingestion_service.service.impl;

import com.sentinel.aml.ingestion_service.service.AccountValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MockAccountValidationServiceImpl implements AccountValidationService {

    @Override
    public boolean isAccountValid(String accountId) {
        if (accountId == null || accountId.trim().isEmpty()) {
            return false;
        }

        // MOCK LOGIC: Simulate a fast Redis SISMEMBER check.
        // Accepts test accounts like ACC-1234 or EXT-9999.
        boolean isValid = accountId.startsWith("ACC-") || accountId.startsWith("EXT-");

        if (!isValid) {
            log.debug("Account validation failed for ID: {}", accountId);
        }

        return isValid;
    }
}