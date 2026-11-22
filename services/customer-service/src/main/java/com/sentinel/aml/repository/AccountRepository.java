package com.sentinel.aml.repository;

import com.sentinel.aml.entity.Account;
import com.sentinel.aml.enums.RiskRating;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    /** Only the three columns the Redis resync needs - no entity hydration for 8,000 rows. */
    @Query("select a.id as accountId, a.riskRating as accountRating, c.riskRating as customerRating "
            + "from Account a join a.customer c")
    Slice<AccountRiskRow> findRiskRows(Pageable pageable);

    List<Account> findByCustomerId(String customerId);

    interface AccountRiskRow {
        String getAccountId();
        RiskRating getAccountRating();
        RiskRating getCustomerRating();
    }
}
