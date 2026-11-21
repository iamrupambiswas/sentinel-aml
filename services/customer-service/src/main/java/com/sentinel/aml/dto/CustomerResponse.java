package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.enums.CustomerType;
import com.sentinel.aml.enums.RiskRating;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * API view of a customer. Never return the JPA entity: it carries the DECRYPTED id_number
 * (and lazy collections). The full value is only available through the audited reveal endpoint (Phase 7b).
 */
public record CustomerResponse(
        String id,
        CustomerType type,
        String fullName,
        String idNumberMasked,
        LocalDate dob,
        String nationality,
        RiskRating riskRating,
        LocalDateTime onboardedAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getType(), c.getFullName(), mask(c.getIdNumber()),
                c.getDob(), c.getNationality(), c.getRiskRating(), c.getOnboardedAt());
    }

    /** Keep only the last 4 characters: "PAN-ABCD1234E" -> "*********234E". */
    static String mask(String value) {
        if (value == null) return null;
        int visible = Math.min(4, value.length() / 3);
        return "*".repeat(value.length() - visible) + value.substring(value.length() - visible);
    }
}
