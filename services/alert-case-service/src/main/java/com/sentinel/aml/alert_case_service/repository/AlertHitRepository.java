package com.sentinel.aml.alert_case_service.repository;

import com.sentinel.aml.alert_case_service.entity.AlertHit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertHitRepository extends JpaRepository<AlertHit, Long> {

    List<AlertHit> findByAlertIdOrderByTxnTimestampAsc(String alertId);

    long countByAlertId(String alertId);
}
