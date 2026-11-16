package com.navan.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.navan.enums.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceSnapshotResponse(
        String transactionId,
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        JsonNode metadata,
        List<TaxLineResponse> taxLines,
        List<FeeLineResponse> feeLines) {
}
