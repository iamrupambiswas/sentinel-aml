package com.sentinel.aml.detection_service.repository;

import com.sentinel.aml.detection_service.entity.AmlRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AmlRuleRepository extends JpaRepository<AmlRule, String> {
    List<AmlRule> findByActiveTrue();
}
