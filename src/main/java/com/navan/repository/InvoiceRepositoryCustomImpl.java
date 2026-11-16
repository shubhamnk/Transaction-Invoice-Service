package com.navan.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

public class InvoiceRepositoryCustomImpl implements InvoiceRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<String> findPageIds(int limit, int offset) {
        return entityManager.createQuery(
                        "select i.id from InvoiceEntity i order by i.createdAt desc, i.id desc",
                        String.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }
}
