package com.sentinel.aml.controller;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.dto.AccountDtos.AccountResponse;
import com.sentinel.aml.dto.AccountDtos.OpenAccountRequest;
import com.sentinel.aml.dto.AccountDtos.RiskRatingChangeRequest;
import com.sentinel.aml.service.AccountService;
import com.sentinel.aml.service.AccountService.CustomerWithAccounts;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Accounts and risk ratings (Phase 5). Phase 6: KYC / SUPERVISOR roles for rating changes. */
@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping(ApiRoutes.CUSTOMERS + "/{customerId}")
    public ResponseEntity<ApiResponse<CustomerWithAccounts>> getCustomer(@PathVariable("customerId") String customerId) {
        return ok(HttpStatus.OK, accountService.getCustomer(customerId), null);
    }

    @PostMapping(ApiRoutes.CUSTOMERS + "/{customerId}/accounts")
    public ResponseEntity<ApiResponse<AccountResponse>> openAccount(@PathVariable("customerId") String customerId,
                                                                    @Valid @RequestBody OpenAccountRequest request) {
        return ok(HttpStatus.CREATED, accountService.openAccount(customerId, request), "Account opened");
    }

    @PutMapping(ApiRoutes.CUSTOMERS + "/{customerId}/risk-rating")
    public ResponseEntity<ApiResponse<CustomerWithAccounts>> changeCustomerRisk(@PathVariable("customerId") String customerId,
                                                                                @Valid @RequestBody RiskRatingChangeRequest request) {
        return ok(HttpStatus.OK, accountService.changeCustomerRisk(customerId, request),
                "Customer risk updated; effective rating of every account republished to Redis");
    }

    @GetMapping(ApiRoutes.ACCOUNTS + "/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable("accountId") String accountId) {
        return ok(HttpStatus.OK, accountService.getAccount(accountId), null);
    }

    @PutMapping(ApiRoutes.ACCOUNTS + "/{accountId}/risk-rating")
    public ResponseEntity<ApiResponse<AccountResponse>> changeAccountRisk(@PathVariable("accountId") String accountId,
                                                                          @Valid @RequestBody RiskRatingChangeRequest request) {
        return ok(HttpStatus.OK, accountService.changeAccountRisk(accountId, request),
                "Account risk updated; effective rating published to Redis");
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(HttpStatus status, T data, String message) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder().success(true).message(message).data(data).build());
    }
}
