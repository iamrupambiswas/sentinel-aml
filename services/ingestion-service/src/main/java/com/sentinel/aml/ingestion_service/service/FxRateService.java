package com.sentinel.aml.ingestion_service.service;

import java.math.BigDecimal;

public interface FxRateService {

    BigDecimal getRateToInr(String currencyCode);

    BigDecimal normalizeToInr(BigDecimal amount, String currencyCode);
}