package com.navan.entity;

import com.navan.config.InstantStringConverter;
import com.navan.config.MoneyStringConverter;
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
@Table(name = "invoices")
public class InvoiceEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 3)
    private String currency;

    @Convert(converter = MoneyStringConverter.class)
    @Column(nullable = false)
    private BigDecimal subtotal;

    @Convert(converter = MoneyStringConverter.class)
    @Column(name = "tax_total", nullable = false)
    private BigDecimal taxTotal;

    @Convert(converter = MoneyStringConverter.class)
    @Column(name = "fee_total", nullable = false)
    private BigDecimal feeTotal;

    @Convert(converter = MoneyStringConverter.class)
    @Column(nullable = false)
    private BigDecimal total;

    @Convert(converter = InstantStringConverter.class)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Convert(converter = InstantStringConverter.class)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("snapshotOrder ASC")
    private List<InvoiceTransactionSnapshotEntity> snapshots = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<InvoiceTaxRollupEntity> taxRollups = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<InvoiceFeeRollupEntity> feeRollups = new ArrayList<>();

    public void addSnapshot(InvoiceTransactionSnapshotEntity snapshot) {
        snapshot.setInvoice(this);
        snapshots.add(snapshot);
    }

    public void addTaxRollup(InvoiceTaxRollupEntity rollup) {
        rollup.setInvoice(this);
        taxRollups.add(rollup);
    }

    public void addFeeRollup(InvoiceFeeRollupEntity rollup) {
        rollup.setInvoice(this);
        feeRollups.add(rollup);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public void setTaxTotal(BigDecimal taxTotal) {
        this.taxTotal = taxTotal;
    }

    public BigDecimal getFeeTotal() {
        return feeTotal;
    }

    public void setFeeTotal(BigDecimal feeTotal) {
        this.feeTotal = feeTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
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

    public List<InvoiceTransactionSnapshotEntity> getSnapshots() {
        return snapshots;
    }

    public List<InvoiceTaxRollupEntity> getTaxRollups() {
        return taxRollups;
    }

    public List<InvoiceFeeRollupEntity> getFeeRollups() {
        return feeRollups;
    }
}
