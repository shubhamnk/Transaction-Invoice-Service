package com.navan.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.navan.enums.TransactionType;
import com.navan.exception.BadRequestException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MetadataValidator {

    private static final Set<String> FEE_KINDS = Set.of("trip_fee", "agent_call_fee");

    public void validate(TransactionType type, JsonNode metadata) {
        if (type == null) {
            throw new BadRequestException("VALIDATION_ERROR", "type is required");
        }
        if (metadata == null || metadata.isNull() || !metadata.isObject() || metadata.isEmpty()) {
            throw new BadRequestException("VALIDATION_ERROR", "metadata must be an object with the fields required for " + type.value());
        }
        switch (type) {
            case FLIGHT -> validateFlight(metadata);
            case HOTEL -> validateHotel(metadata);
            case RAIL -> validateRail(metadata);
            case NAVAN_FEE -> validateNavanFee(metadata);
            default -> throw new BadRequestException("VALIDATION_ERROR", "Unknown transaction type: " + type.value());
        }
    }

    private void validateFlight(JsonNode metadata) {
        requireText(metadata, "origin", "metadata.origin");
        requireText(metadata, "destination", "metadata.destination");
        requireNumber(metadata, "fare", "metadata.fare");
        JsonNode coupons = requireArray(metadata, "coupons", "metadata.coupons");
        for (int i = 0; i < coupons.size(); i++) {
            JsonNode coupon = coupons.get(i);
            String path = "metadata.coupons[" + i + "]";
            if (coupon == null || !coupon.isObject()) {
                throw new BadRequestException("VALIDATION_ERROR", path + " must be an object");
            }
            requireText(coupon, "flight_number", path + ".flight_number");
            requireText(coupon, "from", path + ".from");
            requireText(coupon, "to", path + ".to");
            requireInstant(coupon, "departure", path + ".departure");
            requireInstant(coupon, "arrival", path + ".arrival");
            optionalText(coupon, "carrier", path + ".carrier");
        }
    }

    private void validateHotel(JsonNode metadata) {
        requireText(metadata, "name", "metadata.name");
        JsonNode address = metadata.get("address");
        if (address == null || !address.isObject()) {
            throw new BadRequestException("VALIDATION_ERROR", "metadata.address is required");
        }
        requireText(address, "street", "metadata.address.street");
        requireText(address, "city", "metadata.address.city");
        requireText(address, "country", "metadata.address.country");
        LocalDate checkIn = requireDate(metadata, "check_in", "metadata.check_in");
        LocalDate checkOut = requireDate(metadata, "check_out", "metadata.check_out");
        if (checkOut.isBefore(checkIn)) {
            throw new BadRequestException("VALIDATION_ERROR", "metadata.check_out cannot be before metadata.check_in");
        }
        JsonNode nights = requireNumber(metadata, "nights", "metadata.nights");
        if (!nights.isIntegralNumber() || nights.longValue() <= 0) {
            throw new BadRequestException("VALIDATION_ERROR", "metadata.nights must be a positive integer");
        }
        JsonNode rate = requireNumber(metadata, "room_rate_per_night", "metadata.room_rate_per_night");
        if (rate.decimalValue().signum() < 0) {
            throw new BadRequestException("VALIDATION_ERROR", "metadata.room_rate_per_night cannot be negative");
        }
        if (metadata.has("extras") && !metadata.get("extras").isNull()) {
            JsonNode extras = metadata.get("extras");
            if (!extras.isArray()) {
                throw new BadRequestException("VALIDATION_ERROR", "metadata.extras must be an array");
            }
            for (int i = 0; i < extras.size(); i++) {
                JsonNode extra = extras.get(i);
                if (extra == null || !extra.isObject()) {
                    throw new BadRequestException("VALIDATION_ERROR", "metadata.extras[" + i + "] must be an object");
                }
            }
        }
    }

    private void validateRail(JsonNode metadata) {
        JsonNode legs = requireArray(metadata, "legs", "metadata.legs");
        if (legs.isEmpty()) {
            throw new BadRequestException("VALIDATION_ERROR", "metadata.legs must contain at least one leg");
        }
        for (int i = 0; i < legs.size(); i++) {
            JsonNode leg = legs.get(i);
            String path = "metadata.legs[" + i + "]";
            if (leg == null || !leg.isObject()) {
                throw new BadRequestException("VALIDATION_ERROR", path + " must be an object");
            }
            requireText(leg, "origin", path + ".origin");
            requireText(leg, "destination", path + ".destination");
            requireInstant(leg, "departure", path + ".departure");
            requireInstant(leg, "arrival", path + ".arrival");
            optionalText(leg, "train_number", path + ".train_number");
            optionalText(leg, "class", path + ".class");
        }
    }

    private void validateNavanFee(JsonNode metadata) {
        String feeKind = requireText(metadata, "fee_kind", "metadata.fee_kind");
        if (!FEE_KINDS.contains(feeKind)) {
            throw new BadRequestException("VALIDATION_ERROR", "metadata.fee_kind must be trip_fee or agent_call_fee");
        }
        optionalText(metadata, "description", "metadata.description");
        optionalText(metadata, "related_external_id", "metadata.related_external_id");
    }

    private static String requireText(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            throw new BadRequestException("VALIDATION_ERROR", path + " is required");
        }
        return node.asText();
    }

    private static void optionalText(JsonNode parent, String field, String path) {
        if (!parent.has(field) || parent.get(field).isNull()) {
            return;
        }
        JsonNode node = parent.get(field);
        if (!node.isTextual()) {
            throw new BadRequestException("VALIDATION_ERROR", path + " must be a string");
        }
    }

    private static JsonNode requireNumber(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            throw new BadRequestException("VALIDATION_ERROR", path + " is required");
        }
        return node;
    }

    private static JsonNode requireArray(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isArray()) {
            throw new BadRequestException("VALIDATION_ERROR", path + " is required");
        }
        return node;
    }

    private static void requireInstant(JsonNode parent, String field, String path) {
        String value = requireText(parent, field, path);
        try {
            Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException("VALIDATION_ERROR", path + " must be an ISO-8601 timestamp");
        }
    }

    private static LocalDate requireDate(JsonNode parent, String field, String path) {
        String value = requireText(parent, field, path);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException("VALIDATION_ERROR", path + " must be an ISO-8601 date");
        }
    }
}
