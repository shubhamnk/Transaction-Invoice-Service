package com.navan.dto;

import java.math.BigDecimal;

public record TaxLineResponse(String name, BigDecimal rate, BigDecimal amount) {
}
