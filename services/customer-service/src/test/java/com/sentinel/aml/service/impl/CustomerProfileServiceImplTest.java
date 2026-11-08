package com.sentinel.aml.service.impl;

import com.sentinel.aml.dto.CustomerRegistrationRequest;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.enums.CustomerType;
import com.sentinel.aml.enums.RiskRating;
import com.sentinel.aml.repository.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerProfileServiceImpl customerProfileService;

    @Test
    @DisplayName("Should successfully map request to entity and save with default AML values")
    void onboardCustomer_Success() {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                CustomerType.RETAIL, "Rupam Biswas", "PAN-ABCD1234E", LocalDate.of(2004, 4, 5), "IND"
        );

        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer savedCustomer = customerProfileService.onboardCustomer(request);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository, times(1)).save(customerCaptor.capture());

        Customer capturedCustomer = customerCaptor.getValue();

        assertNotNull(capturedCustomer.getId());
        assertEquals("Rupam Biswas", capturedCustomer.getFullName());
        assertEquals("PAN-ABCD1234E", capturedCustomer.getIdNumber());
        assertEquals(RiskRating.MEDIUM, capturedCustomer.getRiskRating());
    }
}