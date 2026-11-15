package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Account;
import com.sentinel.aml.enums.RiskRating;
import com.sentinel.aml.risk.RiskRatings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AccountDtos {

    private AccountDtos() {}

    /**
     * @param accountId optional. Give one (e.g. ACC-RAPID-01) to register an account that already
     *                  appears in transactions; omit it to get a generated UUID.
     */
    public record OpenAccountRequest(
            @Size(max = 36) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "accountId may contain letters, digits and '-'") String accountId,
            @NotBlank String type,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code") String currency,
            RiskRating riskRating) {}

    public record RiskRatingChangeRequest(@NotNull RiskRating riskRating, @NotBlank @Size(max = 500) String reason) {}

    /** effectiveRiskRating is exactly what detection-service reads from Redis. */
    public record AccountResponse(String id, String customerId, String type, String currency, String status,
                                  RiskRating accountRiskRating, RiskRating customerRiskRating,
                                  RiskRating effectiveRiskRating, LocalDateTime openedAt) {
        public static AccountResponse from(Account a) {
            RiskRating customer = a.getCustomer().getRiskRating();
            return new AccountResponse(a.getId(), a.getCustomer().getId(), a.getType(), a.getCurrency(), a.getStatus(),
                    a.getRiskRating(), customer, RiskRatings.effective(customer, a.getRiskRating()), a.getOpenedAt());
        }
    }
}
