package com.sentinel.aml.detection_service.dto;

import java.math.BigDecimal;
import java.util.Map;

public record TransactionEvent(
        String transactionId,
        String timestamp,
        Map<String, Object> payload
) {
    // Helper method to extract the amount cleanly
    public BigDecimal getAmount() {
        if (payload != null && payload.get("amount") != null) {
            return new BigDecimal(payload.get("amount").toString());
        }
        return BigDecimal.ZERO;
    }
}