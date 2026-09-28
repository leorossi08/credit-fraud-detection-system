package com.gateway.repository;

import com.gateway.model.TransactionRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, String> {
    List<TransactionRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
}