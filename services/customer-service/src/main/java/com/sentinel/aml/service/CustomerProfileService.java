package com.sentinel.aml.service;

import com.sentinel.aml.dto.CustomerRegistrationRequest;
import com.sentinel.aml.entity.Customer;

public interface CustomerProfileService {
    Customer onboardCustomer(CustomerRegistrationRequest request);
}