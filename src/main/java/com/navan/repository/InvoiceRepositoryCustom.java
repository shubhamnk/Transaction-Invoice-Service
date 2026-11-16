package com.navan.repository;

import java.util.List;

public interface InvoiceRepositoryCustom {

    List<String> findPageIds(int limit, int offset);
}
