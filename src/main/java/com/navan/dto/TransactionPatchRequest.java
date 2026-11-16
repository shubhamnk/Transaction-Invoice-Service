package com.navan.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class TransactionPatchRequest {

    private Instant occurredAt;

    @DecimalMin(value = "0.0")
    private BigDecimal total;

    @Valid
    private List<TaxLineRequest> taxLines;

    @Valid
    private List<FeeLineRequest> feeLines;

    private JsonNode metadata;

    public boolean hasUpdates() {
        return occurredAt != null || total != null || taxLines != null || feeLines != null || metadata != null;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public List<TaxLineRequest> getTaxLines() {
        return taxLines;
    }

    public void setTaxLines(List<TaxLineRequest> taxLines) {
        this.taxLines = taxLines;
    }

    public List<FeeLineRequest> getFeeLines() {
        return feeLines;
    }

    public void setFeeLines(List<FeeLineRequest> feeLines) {
        this.feeLines = feeLines;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }
}
