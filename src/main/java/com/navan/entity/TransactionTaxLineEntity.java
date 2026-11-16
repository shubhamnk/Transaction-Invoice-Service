package com.navan.entity;

import com.navan.config.MoneyStringConverter;
import com.navan.config.RateStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "transaction_tax_lines")
public class TransactionTaxLineEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;

    @Column(nullable = false)
    private String name;

    @Convert(converter = RateStringConverter.class)
    private BigDecimal rate;

    @Convert(converter = MoneyStringConverter.class)
    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TransactionEntity getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionEntity transaction) {
        this.transaction = transaction;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public int getLineOrder() {
        return lineOrder;
    }

    public void setLineOrder(int lineOrder) {
        this.lineOrder = lineOrder;
    }
}
