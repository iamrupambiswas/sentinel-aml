package com.sentinel.aml.service.impl;

import com.sentinel.aml.dto.CustomerRegistrationRequest;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.enums.RiskRating;
import com.sentinel.aml.repository.CustomerRepository;
import com.sentinel.aml.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerProfileServiceImpl implements CustomerProfileService {

    private final CustomerRepository customerRepository;

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
                .onboardedAt(LocalDateTime.now())
                .build();

        return customerRepository.save(customer);
    }
}