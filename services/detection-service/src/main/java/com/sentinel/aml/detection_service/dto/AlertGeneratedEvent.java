package com.sentinel.aml.detection_service.dto;

import java.math.BigDecimal;

public record AlertGeneratedEvent(
        String alertId,
        String transactionId,
        String accountId,
        String ruleTriggered,
        BigDecimal alertAmount,
        String timestamp
) {}