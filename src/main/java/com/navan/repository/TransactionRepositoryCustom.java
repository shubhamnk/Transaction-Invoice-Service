package com.navan.repository;

import java.util.List;

public interface TransactionRepositoryCustom {

    List<String> findPageIds(int limit, int offset);
}
