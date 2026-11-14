package com.sentinel.aml.detection_service.rule.impl;

import com.sentinel.aml.detection_service.dto.RuleHit;
import com.sentinel.aml.detection_service.dto.TransactionEvent;
import com.sentinel.aml.detection_service.entity.AmlRule;
import com.sentinel.aml.detection_service.rule.DetectionRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BehavioralDeviationRule implements DetectionRule {

    private static final String RULE_CODE = "BEHAVIORAL_DEVIATION";
    private final StringRedisTemplate redisTemplate;

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionEvent event, AmlRule ruleConfig) {
        String accountId = event.getSourceAccountId();
        BigDecimal amount = event.getAmount();

        // Format today's date as YYYYMMDD in UTC
        String todayStr = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String dailyVolumeKey = "aml:day:" + accountId + ":" + todayStr;
        String baselineKey = "aml:baseline:" + accountId;

        // 1. Fetch 90-day baseline from Redis (populated by a nightly job)
        String baselineStr = redisTemplate.opsForValue().get(baselineKey);
        if (baselineStr == null) {
            return Optional.empty(); // No baseline exists yet for this customer
        }

        BigDecimal baseline = new BigDecimal(baselineStr);
        if (baseline.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        // 2. Increment today's total volume atomically
        Double newTotalDouble = redisTemplate.opsForValue().increment(dailyVolumeKey, amount.doubleValue());
        redisTemplate.expire(dailyVolumeKey, Duration.ofHours(48)); // Keep for 2 days then drop

        BigDecimal todayTotal = BigDecimal.valueOf(newTotalDouble != null ? newTotalDouble : 0.0);

        // 3. Evaluate multiplier (thresholdAmount represents the 3x multiplier)
        BigDecimal allowedVolume = baseline.multiply(ruleConfig.getThresholdAmount());

        if (todayTotal.compareTo(allowedVolume) > 0) {
            String explanation = String.format(
                    "Account %s reached a daily volume of $%s, exceeding %sx their 90-day historical average ($%s).",
                    accountId,
                    todayTotal.toPlainString(),
                    ruleConfig.getThresholdAmount().toPlainString(),
                    baseline.toPlainString()
            );

            return Optional.of(RuleHit.builder()
                    .ruleCode(RULE_CODE)
                    .ruleWeight(ruleConfig.getWeight())
                    .explanation(explanation)
                    .build());
        }

        return Optional.empty();
    }
}