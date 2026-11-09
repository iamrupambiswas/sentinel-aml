package com.sentinel.aml.ingestion_service.repository;

import com.sentinel.aml.ingestion_service.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

}
