package com.navan.repository;

import com.navan.entity.InvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, String>, InvoiceRepositoryCustom {
}
