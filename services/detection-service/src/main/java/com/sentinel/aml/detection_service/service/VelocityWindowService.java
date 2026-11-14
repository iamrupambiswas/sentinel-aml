package com.sentinel.aml.detection_service.service;

import java.math.BigDecimal;

public interface VelocityWindowService {
    BigDecimal calculateWindowSum(String accountId, String transactionId, BigDecimal amount, long windowMillis);
}