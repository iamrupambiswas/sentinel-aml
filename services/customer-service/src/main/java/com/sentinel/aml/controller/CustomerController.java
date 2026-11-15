package com.sentinel.aml.controller;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.dto.CustomerRegistrationRequest;
import com.sentinel.aml.dto.CustomerResponse;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.service.CustomerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiRoutes.CUSTOMERS)
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerProfileService customerProfileService;

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> registerCustomer(
            @Valid @RequestBody CustomerRegistrationRequest request) { // @Valid triggers the DTO checks!

        Customer savedCustomer = customerProfileService.onboardCustomer(request);

        ApiResponse<CustomerResponse> response = ApiResponse.<CustomerResponse>builder()
                .success(true)
                .message("Customer onboarded successfully")
                .data(CustomerResponse.from(savedCustomer))
                .build();

        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
    }
}