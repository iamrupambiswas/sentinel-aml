package com.sentinel.aml.detection_service.rules;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Request/response shapes of the rule admin API (P13). */
public final class RuleAdminDtos {

    private RuleAdminDtos() {}

    /**
     * Every tunable field is optional: null means "keep the current value". {@code params}, when
     * given, REPLACES the whole params object (send the full object, not a diff).
     *
     * @param expectedVersion the version you read; if the rule changed since, the update is rejected (409)
     * Who made the change is not part of the body: it is the caller's JWT username (Phase 6).
     */
    public record RuleUpdateRequest(
            @PositiveOrZero BigDecimal thresholdAmount,
            @PositiveOrZero @Max(527040) Long windowMinutes,         // at most 366 days
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal weight,
            Boolean active,
            Map<String, Object> params,
            @NotNull Integer expectedVersion,
            @NotBlank @Size(max = 500) String reason) {}

    /**
     * @param loadedVersion the version THIS detection pod is currently evaluating with
     *                      (null when the rule is inactive). Equal to {@code version} = change is live.
     */
    public record RuleView(String ruleCode, String ruleName, String description, BigDecimal thresholdAmount,
                           long windowMinutes, BigDecimal weight, boolean active, Map<String, Object> params,
                           int version, Integer loadedVersion, java.util.Set<String> supportedParams) {}

    public record RuleVersionView(int version, BigDecimal thresholdAmount, long windowMinutes, BigDecimal weight,
                                  boolean active, Map<String, Object> params, String changedBy,
                                  String reason, LocalDateTime changedAt) {}
}
