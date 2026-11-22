package com.sentinel.aml.alert_case_service.repository;

import com.sentinel.aml.alert_case_service.entity.Alert;
import com.sentinel.aml.alert_case_service.entity.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, String> {

    /** Every filter is optional (null = any). @Param names keep this working without -parameters. */
    @Query("""
            select a from Alert a
            where (:status is null or a.status = :status)
              and (:accountId is null or a.accountId = :accountId)
              and (:ruleCode is null or a.ruleCode = :ruleCode)
            """)
    Page<Alert> search(@Param("status") AlertStatus status,
                       @Param("accountId") String accountId,
                       @Param("ruleCode") String ruleCode,
                       Pageable pageable);

    Optional<Alert> findByDedupKey(String dedupKey);

    @Query("select a.status as status, count(a) as alerts, sum(a.hitCount) as hits from Alert a group by a.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        AlertStatus getStatus();
        long getAlerts();
        Long getHits();
    }
}
