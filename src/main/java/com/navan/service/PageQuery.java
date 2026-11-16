package com.navan.service;

import com.navan.exception.BadRequestException;

public record PageQuery(int limit, int offset) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public static PageQuery parse(Integer limit, Integer offset) {
        int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
        int resolvedOffset = offset == null ? 0 : offset;
        if (resolvedLimit < 1 || resolvedLimit > MAX_LIMIT) {
            throw new BadRequestException("INVALID_PAGINATION", "limit must be between 1 and 100");
        }
        if (resolvedOffset < 0) {
            throw new BadRequestException("INVALID_PAGINATION", "offset must be zero or greater");
        }
        return new PageQuery(resolvedLimit, resolvedOffset);
    }
}
