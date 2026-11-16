package com.navan.repository;

import com.navan.entity.InvoiceTransactionSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceSnapshotRepository extends JpaRepository<InvoiceTransactionSnapshotEntity, String> {

    boolean existsByTransactionId(String transactionId);
}
