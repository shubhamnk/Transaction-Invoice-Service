package com.navan.entity;

import com.navan.config.InstantStringConverter;
import com.navan.config.MoneyStringConverter;
import com.navan.config.TransactionTypeConverter;
import com.navan.enums.TransactionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoice_transaction_snapshots")
public class InvoiceTransactionSnapshotEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private InvoiceEntity invoice;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

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

    @Column(name = "snapshot_order", nullable = false)
    private int snapshotOrder;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<InvoiceSnapshotTaxLineEntity> taxLines = new ArrayList<>();

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<InvoiceSnapshotFeeLineEntity> feeLines = new ArrayList<>();

    public void addTaxLine(InvoiceSnapshotTaxLineEntity line) {
        line.setSnapshot(this);
        taxLines.add(line);
    }

    public void addFeeLine(InvoiceSnapshotFeeLineEntity line) {
        line.setSnapshot(this);
        feeLines.add(line);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public InvoiceEntity getInvoice() {
        return invoice;
    }

    public void setInvoice(InvoiceEntity invoice) {
        this.invoice = invoice;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
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

    public int getSnapshotOrder() {
        return snapshotOrder;
    }

    public void setSnapshotOrder(int snapshotOrder) {
        this.snapshotOrder = snapshotOrder;
    }

    public List<InvoiceSnapshotTaxLineEntity> getTaxLines() {
        return taxLines;
    }

    public List<InvoiceSnapshotFeeLineEntity> getFeeLines() {
        return feeLines;
    }
}
