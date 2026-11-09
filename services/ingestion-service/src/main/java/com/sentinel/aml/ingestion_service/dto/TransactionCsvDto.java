package com.sentinel.aml.ingestion_service.dto;

import lombok.Data;
import org.springframework.batch.item.ItemCountAware;

@Data
public class TransactionCsvDto implements ItemCountAware {
    private String sourceAccountId;
    private String destinationAccountId;
    private String amount;
    private String currency;
    private String type;

    // Mapped automatically by Spring Batch
    private int lineNumber;

    @Override
    public void setItemCount(int count) {
        // count starts at 1 for the first read data row.
        this.lineNumber = count + 1;
    }
}