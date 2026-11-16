package com.navan.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

public class TransactionRepositoryCustomImpl implements TransactionRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<String> findPageIds(int limit, int offset) {
        return entityManager.createQuery(
                        "select t.id from TransactionEntity t order by t.occurredAt desc, t.id desc",
                        String.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }
}
