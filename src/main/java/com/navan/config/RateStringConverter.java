package com.navan.config;

import com.navan.service.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

@Converter
public class RateStringConverter implements AttributeConverter<BigDecimal, String> {

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        BigDecimal normalized = Money.normalizeRate(attribute);
        return normalized == null ? null : normalized.toPlainString();
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return Money.normalizeRate(new BigDecimal(dbData));
    }
}
