package com.sentinel.aml.detection_service.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formats INR with Indian digit grouping for explanations: 830000 -> ₹8,30,000.
 * (The JDK's en-IN NumberFormat still groups in thousands, so this is done by hand.)
 */
public final class Inr {
    private Inr() {}

    public static String format(BigDecimal amount) {
        String digits = amount.setScale(0, RoundingMode.HALF_UP).abs().toPlainString();
        String sign = amount.signum() < 0 ? "-" : "";
        if (digits.length() <= 3) {
            return "₹" + sign + digits;
        }
        String last3 = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);
        StringBuilder sb = new StringBuilder();
        int firstGroup = rest.length() % 2 == 0 ? 2 : 1;
        sb.append(rest, 0, firstGroup);
        for (int i = firstGroup; i < rest.length(); i += 2) {
            sb.append(',').append(rest, i, i + 2);
        }
        return "₹" + sign + sb + "," + last3;
    }
}
