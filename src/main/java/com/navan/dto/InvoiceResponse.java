package com.navan.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceResponse(
        String id,
        String currency,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal feeTotal,
        BigDecimal total,
        Instant createdAt,
        Instant updatedAt,
        List<InvoiceSnapshotResponse> transactions,
        List<TaxLineResponse> taxRollups,
        List<FeeLineResponse> feeRollups) {
}
