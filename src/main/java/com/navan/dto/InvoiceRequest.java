package com.navan.dto;

import java.util.List;

public class InvoiceRequest {

    private String transactionId;
    private List<String> transactionIds;

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public List<String> getTransactionIds() {
        return transactionIds;
    }

    public void setTransactionIds(List<String> transactionIds) {
        this.transactionIds = transactionIds;
    }
}
