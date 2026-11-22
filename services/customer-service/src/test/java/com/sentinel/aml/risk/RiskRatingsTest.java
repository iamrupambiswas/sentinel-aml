package com.sentinel.aml.risk;

import com.sentinel.aml.enums.RiskRating;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RiskRatingsTest {

    @ParameterizedTest(name = "customer {0} + account {1} -> {2}")
    @CsvSource({
            "LOW,    LOW,    LOW",
            "LOW,    HIGH,   HIGH",     // one flagged account is HIGH on its own
            "HIGH,   LOW,    HIGH",     // a HIGH-risk customer makes every account HIGH
            "MEDIUM, LOW,    MEDIUM",
            "MEDIUM, HIGH,   HIGH",
            ",       MEDIUM, MEDIUM",   // missing customer rating
            "MEDIUM, ,       MEDIUM",   // missing account rating
            ",       ,       LOW"
    })
    void effectiveRatingIsTheHigherOfCustomerAndAccount(RiskRating customer, RiskRating account, RiskRating expected) {
        assertThat(RiskRatings.effective(customer, account)).isEqualTo(expected);
    }
}
