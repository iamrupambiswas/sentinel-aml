package com.sentinel.aml.detection_service.repository;

import com.sentinel.aml.detection_service.entity.AmlRuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AmlRuleVersionRepository extends JpaRepository<AmlRuleVersion, Long> {

    List<AmlRuleVersion> findByRuleCodeOrderByVersionDesc(String ruleCode);
}
