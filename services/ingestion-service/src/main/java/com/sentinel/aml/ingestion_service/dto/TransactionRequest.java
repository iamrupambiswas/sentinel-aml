package com.sentinel.aml.ingestion_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record TransactionRequest(
        @NotBlank(message = "Source account ID is required")
        String sourceAccountId,

        @NotBlank(message = "Destination account ID is required")
        String destinationAccountId,

        @NotNull(message = "Transaction amount is required")
        @DecimalMin(value = "0.01", message = "Transaction amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter code (e.g., USD)")
        String currency,

        @NotBlank(message = "Transaction type is required (e.g., WIRE, ACH, INTERNAL)")
        String type
) {}