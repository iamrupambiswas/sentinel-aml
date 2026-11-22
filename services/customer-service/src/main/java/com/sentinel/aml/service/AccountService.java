package com.sentinel.aml.service;

import com.sentinel.aml.dto.AccountDtos.AccountResponse;
import com.sentinel.aml.dto.AccountDtos.OpenAccountRequest;
import com.sentinel.aml.dto.AccountDtos.RiskRatingChangeRequest;
import com.sentinel.aml.dto.CustomerResponse;
import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.enums.RiskRating;
import com.sentinel.aml.repository.AccountRepository;
import com.sentinel.aml.repository.CustomerRepository;
import com.sentinel.aml.risk.AccountRiskPublisher;
import com.sentinel.aml.risk.RiskRatings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Accounts and risk ratings. Every rating change is mirrored to Redis after commit. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final AccountRiskPublisher riskPublisher;

    public record CustomerWithAccounts(CustomerResponse customer, List<AccountResponse> accounts) {}

    @Transactional(readOnly = true)
    public CustomerWithAccounts getCustomer(String customerId) {
        Customer c = findCustomer(customerId);
        return new CustomerWithAccounts(CustomerResponse.from(c),
                accountRepository.findByCustomerId(customerId).stream().map(AccountResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountId) {
        return AccountResponse.from(findAccount(accountId));
    }

    @Transactional
    public AccountResponse openAccount(String customerId, OpenAccountRequest req) {
        Customer customer = findCustomer(customerId);
        String id = req.accountId() == null || req.accountId().isBlank() ? UUID.randomUUID().toString() : req.accountId().trim();
        if (accountRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account " + id + " already exists");
        }
        Account account = accountRepository.save(Account.builder()
                .id(id)
                .customer(customer)
                .type(req.type().trim().toUpperCase())
                .currency(req.currency())
                .openedAt(LocalDateTime.now())
                .riskRating(req.riskRating() != null ? req.riskRating() : RiskRating.MEDIUM)
                .status("ACTIVE")
                .build());
        RiskRating effective = RiskRatings.effective(customer.getRiskRating(), account.getRiskRating());
        riskPublisher.publishAfterCommit(Map.of(id, effective));
        log.info("Opened account {} for customer {} (effective risk {})", id, customerId, effective);
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse changeAccountRisk(String accountId, RiskRatingChangeRequest req) {
        Account account = findAccount(accountId);
        RiskRating old = account.getRiskRating();
        account.setRiskRating(req.riskRating());
        RiskRating effective = RiskRatings.effective(account.getCustomer().getRiskRating(), req.riskRating());
        riskPublisher.publishAfterCommit(Map.of(accountId, effective));
        log.info("Account {} risk {} -> {} (effective {}): {}", accountId, old, req.riskRating(), effective, req.reason());
        return AccountResponse.from(account);
    }

    /** A customer's rating changes the effective rating of ALL their accounts. */
    @Transactional
    public CustomerWithAccounts changeCustomerRisk(String customerId, RiskRatingChangeRequest req) {
        Customer customer = findCustomer(customerId);
        RiskRating old = customer.getRiskRating();
        customer.setRiskRating(req.riskRating());
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        Map<String, RiskRating> effective = new LinkedHashMap<>();
        accounts.forEach(a -> effective.put(a.getId(), RiskRatings.effective(req.riskRating(), a.getRiskRating())));
        riskPublisher.publishAfterCommit(effective);
        log.info("Customer {} risk {} -> {} ({} account(s) republished): {}", customerId, old, req.riskRating(),
                accounts.size(), req.reason());
        return new CustomerWithAccounts(CustomerResponse.from(customer), accounts.stream().map(AccountResponse::from).toList());
    }

    private Customer findCustomer(String id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + id));
    }

    private Account findAccount(String id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + id));
    }
}
