package com.navan.repository;

import com.navan.entity.TransactionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String>, TransactionRepositoryCustom {

    Optional<TransactionEntity> findByExternalId(String externalId);
}
