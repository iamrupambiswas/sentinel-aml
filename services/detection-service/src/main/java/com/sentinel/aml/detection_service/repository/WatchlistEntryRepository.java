package com.sentinel.aml.detection_service.repository;

import com.sentinel.aml.detection_service.entity.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry, Long> {
    List<WatchlistEntry> findByActiveTrue();
}
