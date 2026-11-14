package com.sentinel.aml.ingestion_service.repository;

import com.sentinel.aml.ingestion_service.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /** Bounded page so a 10k-row batch doesn't load every pending row into memory at once. */
    @Query("select o from OutboxEvent o where o.status = :status order by o.createdAt asc")
    List<OutboxEvent> findPage(String status, Pageable pageable);
}
