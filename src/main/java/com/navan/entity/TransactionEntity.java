package com.navan.entity;

import com.navan.config.InstantStringConverter;
import com.navan.config.MoneyStringConverter;
import com.navan.config.TransactionTypeConverter;
import com.navan.enums.TransactionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    private String id;

    @Convert(converter = TransactionTypeConverter.class)
    @Column(nullable = false)
    private TransactionType type;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Convert(converter = InstantStringConverter.class)
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Convert(converter = MoneyStringConverter.class)
    @Column(nullable = false)
    private BigDecimal total;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String metadata;

    @Convert(converter = InstantStringConverter.class)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Convert(converter = InstantStringConverter.class)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<TransactionTaxLineEntity> taxLines = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<TransactionFeeLineEntity> feeLines = new ArrayList<>();

    public void addTaxLine(TransactionTaxLineEntity line) {
        line.setTransaction(this);
        taxLines.add(line);
    }

    public void addFeeLine(TransactionFeeLineEntity line) {
        line.setTransaction(this);
        feeLines.add(line);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<TransactionTaxLineEntity> getTaxLines() {
        return taxLines;
    }

    public List<TransactionFeeLineEntity> getFeeLines() {
        return feeLines;
    }
}
