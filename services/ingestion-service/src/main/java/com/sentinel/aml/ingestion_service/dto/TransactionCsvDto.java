package com.sentinel.aml.ingestion_service.dto;

import lombok.Data;

@Data
public class TransactionCsvDto {
    private String sourceAccountId;
    private String destinationAccountId;
    private String amount;
    private String currency;
    private String type;
}