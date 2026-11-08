package com.sentinel.aml.dto;

import com.sentinel.aml.enums.CustomerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CustomerRegistrationRequest(

        @NotNull(message = "Customer type is required (RETAIL, CORPORATE, INSTITUTIONAL)")
        CustomerType type,

        @NotBlank(message = "Full name cannot be blank")
        String fullName,

        @NotBlank(message = "National ID number cannot be blank")
        String idNumber,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be a past date")
        LocalDate dob,

        @NotBlank(message = "Nationality is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Nationality must be a valid 3-letter ISO code (e.g., IND)")
        String nationality
) {}