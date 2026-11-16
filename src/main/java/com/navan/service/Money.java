package com.navan.service;

import com.navan.exception.BadRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final int RATE_SCALE = 6;

    private Money() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal normalizeAmount(BigDecimal value, String field) {
        if (value == null) {
            throw new BadRequestException("VALIDATION_ERROR", field + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("VALIDATION_ERROR", field + " cannot be negative");
        }
        return scale(value);
    }

    public static BigDecimal normalizeRate(BigDecimal rate) {
        if (rate == null) {
            return null;
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("VALIDATION_ERROR", "Tax rate cannot be negative");
        }
        BigDecimal scaled = rate.scale() > RATE_SCALE ? rate.setScale(RATE_SCALE, ROUNDING) : rate;
        BigDecimal stripped = scaled.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped;
    }
}
