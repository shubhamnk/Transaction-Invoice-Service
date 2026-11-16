package com.navan.config;

import com.navan.service.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

@Converter
public class MoneyStringConverter implements AttributeConverter<BigDecimal, String> {

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) {
            return null;
        }
        return Money.scale(attribute).toPlainString();
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return Money.scale(new BigDecimal(dbData));
    }
}
