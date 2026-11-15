package com.sentinel.aml.ingestion_service.dto;

import lombok.Data;
import org.springframework.batch.item.ItemCountAware;

/**
 * One CSV row. Columns are mapped by the header line, so files may contain only the five
 * required columns (sourceAccountId,destinationAccountId,amount,currency,type) or also the
 * optional ones (counterpartyName,counterpartyCountry,txnTimestamp) in any order.
 */
@Data
public class TransactionCsvDto implements ItemCountAware {
    private String sourceAccountId;
    private String destinationAccountId;
    private String amount;
    private String currency;
    private String type;

    // Optional columns
    private String counterpartyName;
    private String counterpartyCountry;
    private String txnTimestamp;

    // Physical line number in the file (header is line 1)
    private int lineNumber;

    @Override
    public void setItemCount(int count) {
        // count starts at 1 for the first data row, which is line 2 of the file.
        this.lineNumber = count + 1;
    }
}
