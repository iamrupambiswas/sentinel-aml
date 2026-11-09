package com.sentinel.aml.ingestion_service.dto;

import com.sentinel.aml.ingestion_service.entity.OutboxEvent;
import com.sentinel.aml.ingestion_service.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProcessedTransaction {
    private Transaction transaction;
    private OutboxEvent outboxEvent;
}