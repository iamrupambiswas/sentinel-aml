package com.sentinel.aml.risk;

import com.sentinel.aml.enums.RiskRating;

public final class RiskRatings {

    private RiskRatings() {}

    /**
     * The rating detection-service scores with: the HIGHER of customer and account rating.
     * A HIGH-risk customer makes all their accounts HIGH; one flagged account can be HIGH on its own.
     * Relies on the enum order LOW &lt; MEDIUM &lt; HIGH.
     */
    public static RiskRating effective(RiskRating customer, RiskRating account) {
        if (customer == null) return account == null ? RiskRating.LOW : account;
        if (account == null) return customer;
        return customer.ordinal() >= account.ordinal() ? customer : account;
    }
}
