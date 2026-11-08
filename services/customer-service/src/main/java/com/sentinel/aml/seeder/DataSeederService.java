package com.sentinel.aml.seeder;

import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.enums.CustomerType;
import com.sentinel.aml.enums.RiskRating;
import com.sentinel.aml.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSeederService {

    private final CustomerRepository customerRepository;
    private final Faker faker = new Faker();

    @Transactional
    public String seedDatabase() {
        if (customerRepository.count() > 0) {
            return "Database is already seeded. Skipping generation to prevent duplicates.";
        }

        log.info("Starting synthetic data generation for 5000 Customers and ~8000 Accounts...");

        List<Customer> batch = new ArrayList<>();
        int totalAccounts = 0;
        int batchSize = 1000;

        for (int i = 1; i <= 5000; i++) {
            Customer customer = generateFakeCustomer();

            // Generate between 1 and 3 accounts per customer to reach ~8000 total
            int accountCount = faker.number().numberBetween(1, 4);
            List<Account> accounts = new ArrayList<>();

            for (int j = 0; j < accountCount; j++) {
                accounts.add(generateFakeAccount(customer));
                totalAccounts++;
            }

            customer.setAccounts(accounts);
            batch.add(customer);

            // Save in chunks to prevent OutOfMemory exceptions and clear the L1 Cache
            if (i % batchSize == 0) {
                customerRepository.saveAll(batch);
                log.info("Inserted {} customers...", i);
                batch.clear();
            }
        }

        // Save any remaining records
        if (!batch.isEmpty()) {
            customerRepository.saveAll(batch);
        }

        log.info("Successfully seeded 5000 Customers and {} Accounts.", totalAccounts);
        return String.format("Successfully seeded 5000 Customers and %d Accounts.", totalAccounts);
    }

    private Customer generateFakeCustomer() {
        return Customer.builder()
                .id(UUID.randomUUID().toString())
                .type(faker.options().option(CustomerType.class))
                .fullName(faker.name().fullName())
                // Faker generates a random ID string. The Converter will automatically AES encrypt it!
                .idNumber(faker.idNumber().valid())
                .dob(faker.date().birthday(18, 80).toInstant().atZone(ZoneId.systemDefault()).toLocalDate())
                .nationality(faker.options().option("IND", "USA", "GBR", "SGP", "ARE"))
                .riskRating(faker.options().option(RiskRating.class))
                .onboardedAt(faker.date().past(1000, TimeUnit.DAYS).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
    }

    private Account generateFakeAccount(Customer customer) {
        return Account.builder()
                .id(UUID.randomUUID().toString())
                .customer(customer)
                .type(faker.options().option("SAVINGS", "CURRENT", "LOAN", "CREDIT_CARD"))
                .currency(faker.options().option("INR", "USD", "GBP", "EUR", "AED"))
                .openedAt(faker.date().past(500, TimeUnit.DAYS).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .riskRating(faker.options().option(RiskRating.class))
                .status(faker.options().option("ACTIVE", "DORMANT", "SUSPENDED"))
                .build();
    }
}