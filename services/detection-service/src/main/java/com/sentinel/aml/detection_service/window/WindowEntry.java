package com.sentinel.aml.detection_service.window;

import java.math.BigDecimal;

/** One transaction inside a sliding window. Encoded in Redis as "txnId|amountInr". */
public record WindowEntry(String transactionId, BigDecimal amountInr, long timestampMillis) {

    public String encode() {
        return transactionId + "|" + amountInr.toPlainString();
    }

    public static WindowEntry decode(String member, long score) {
        int sep = member.lastIndexOf('|');
        if (sep < 0) {
            return new WindowEntry(member, BigDecimal.ZERO, score);
        }
        return new WindowEntry(member.substring(0, sep), new BigDecimal(member.substring(sep + 1)), score);
    }
}
