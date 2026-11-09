package com.sentinel.aml.ingestion_service.service.impl;

import com.sentinel.aml.ingestion_service.entity.FxRate;
import com.sentinel.aml.ingestion_service.repository.FxRateRepository;
import com.sentinel.aml.ingestion_service.service.FxRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class FxRateServiceImpl implements FxRateService {

    private final FxRateRepository fxRateRepository;

    @Override
    @Cacheable(value = "fxRates", key = "#currencyCode")
    public BigDecimal getRateToInr(String currencyCode) {
        return fxRateRepository.findById(currencyCode.toUpperCase())
                .map(FxRate::getRateToInr)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported currency: " + currencyCode));
    }

    @Override
    public BigDecimal normalizeToInr(BigDecimal amount, String currencyCode) {
        if ("INR".equalsIgnoreCase(currencyCode)) {
            return amount;
        }
        BigDecimal rate = getRateToInr(currencyCode);
        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }
}