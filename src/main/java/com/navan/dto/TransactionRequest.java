package com.navan.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.navan.enums.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class TransactionRequest {

    @NotNull
    private TransactionType type;

    @NotBlank
    @Size(max = 200)
    private String externalId;

    @NotBlank
    private String currency;

    @NotNull
    private Instant occurredAt;

    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal total;

    @NotNull
    @Valid
    private List<TaxLineRequest> taxLines;

    @NotNull
    @Valid
    private List<FeeLineRequest> feeLines;

    @NotNull
    private JsonNode metadata;

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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
