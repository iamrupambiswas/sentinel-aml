package com.sentinel.aml.ingestion_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

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

        @NotBlank(message = "Transaction type is required (e.g., WIRE, ACH, CASH, INTERNAL)")
        String type,

        // ---- Optional enrichment (used by detection rules) ----
        @Size(max = 255, message = "Counterparty name must be at most 255 characters")
        String counterpartyName,

        @Pattern(regexp = "^[A-Z]{2,3}$", message = "Counterparty country must be an ISO code (e.g., IR or IRN)")
        String counterpartyCountry,

        /* Business time of the transaction; defaults to "now" when the bank doesn't send it. */
        @PastOrPresent(message = "Transaction timestamp cannot be in the future")
        Instant txnTimestamp
) {}
