package com.sentinel.aml.detection_service.repository;

import com.sentinel.aml.detection_service.entity.AmlAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AmlAlertRepository extends JpaRepository<AmlAlert, String> {
}