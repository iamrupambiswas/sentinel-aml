package com.sentinel.aml.detection_service.engine;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/** Immutable, parsed view of one aml_rule row. Swapped atomically on reload. */
public record RuleConfig(
        String ruleCode,
        BigDecimal weight,
        BigDecimal threshold,
        long windowMinutes,
        int version,
        Map<String, Object> params
) {
    public Duration window() {
        return Duration.ofMinutes(windowMinutes);
    }

    public BigDecimal decimal(String name, BigDecimal defaultValue) {
        Object v = params == null ? null : params.get(name);
        return v == null ? defaultValue : new BigDecimal(v.toString());
    }

    public double number(String name, double defaultValue) {
        Object v = params == null ? null : params.get(name);
        return v == null ? defaultValue : Double.parseDouble(v.toString());
    }
}
