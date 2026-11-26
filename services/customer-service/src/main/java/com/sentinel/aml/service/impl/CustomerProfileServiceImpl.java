package com.sentinel.aml.service.impl;

import com.sentinel.aml.dto.CustomerRegistrationRequest;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.enums.RiskRating;
import com.sentinel.aml.repository.CustomerRepository;
import com.sentinel.aml.service.CustomerProfileService;
import com.sentinel.aml.common.audit.AuditPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset; // all services store UTC; the console shows local time
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerProfileServiceImpl implements CustomerProfileService {

    private final CustomerRepository customerRepository;
    private final AuditPublisher audit;

    @Override
    @Transactional
    public Customer onboardCustomer(CustomerRegistrationRequest request) {
        Customer customer = Customer.builder()
                .id(UUID.randomUUID().toString())
                .type(request.type())
                .fullName(request.fullName())
                .idNumber(request.idNumber())
                .dob(request.dob())
                .nationality(request.nationality())
                .riskRating(RiskRating.MEDIUM)
                .onboardedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        Customer saved = customerRepository.save(customer);
        // No PII in audit details: the type and nationality are enough to find the record
        audit.record("CUSTOMER_ONBOARDED", "CUSTOMER", saved.getId(),
                Map.of("type", String.valueOf(saved.getType()), "nationality", String.valueOf(saved.getNationality())));
        return saved;
    }
}