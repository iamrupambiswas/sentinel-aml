package com.sentinel.aml.detection_service.dto;

import java.math.BigDecimal;
import java.util.Map;

public record TransactionEvent(
        String transactionId,
        String timestamp,
        Map<String, Object> payload
) {
    public BigDecimal getAmount() {
        if (payload != null && payload.get("amount") != null) {
            return new BigDecimal(payload.get("amount").toString());
        }
        return BigDecimal.ZERO;
    }

    // Add this helper so the Consumer can extract the account ID
    public String getSourceAccountId() {
        if (payload != null && payload.get("sourceAccountId") != null) {
            return payload.get("sourceAccountId").toString();
        }
        return "UNKNOWN";
    }

    public String getDirection() {
        if (payload != null && payload.get("direction") != null) {
            return payload.get("direction").toString().toUpperCase();
        }
        return "UNKNOWN";
    }

    public String getCounterpartyCountry() {
        if (payload != null && payload.get("counterpartyCountry") != null) {
            return payload.get("counterpartyCountry").toString().toUpperCase();
        }
        return "UNKNOWN";
    }

    public String getCounterpartyName() {
        if (payload != null && payload.get("counterpartyName") != null) {
            return payload.get("counterpartyName").toString().toUpperCase();
        }
        return "UNKNOWN";
    }
}