package com.navan.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.dto.FeeLineResponse;
import com.navan.dto.InvoiceResponse;
import com.navan.dto.InvoiceSnapshotResponse;
import com.navan.dto.TaxLineResponse;
import com.navan.dto.TransactionResponse;
import com.navan.entity.InvoiceEntity;
import com.navan.entity.InvoiceFeeRollupEntity;
import com.navan.entity.InvoiceSnapshotFeeLineEntity;
import com.navan.entity.InvoiceSnapshotTaxLineEntity;
import com.navan.entity.InvoiceTaxRollupEntity;
import com.navan.entity.InvoiceTransactionSnapshotEntity;
import com.navan.entity.TransactionEntity;
import com.navan.entity.TransactionFeeLineEntity;
import com.navan.entity.TransactionTaxLineEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApiMapper {

    private final ObjectMapper objectMapper;

    public ApiMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TransactionResponse toResponse(TransactionEntity entity) {
        return new TransactionResponse(
                entity.getId(),
                entity.getType(),
                entity.getExternalId(),
                entity.getCurrency(),
                entity.getOccurredAt(),
                entity.getTotal(),
                entity.getTaxLines().stream().map(this::toTax).toList(),
                entity.getFeeLines().stream().map(this::toFee).toList(),
                readMetadata(entity.getMetadata()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public InvoiceResponse toResponse(InvoiceEntity entity) {
        return new InvoiceResponse(
                entity.getId(),
                entity.getCurrency(),
                entity.getSubtotal(),
                entity.getTaxTotal(),
                entity.getFeeTotal(),
                entity.getTotal(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getSnapshots().stream().map(this::toSnapshot).toList(),
                entity.getTaxRollups().stream().map(this::toTaxRollup).toList(),
                entity.getFeeRollups().stream().map(this::toFeeRollup).toList());
    }

    public String writeMetadata(JsonNode metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("metadata could not be stored", exception);
        }
    }

    private InvoiceSnapshotResponse toSnapshot(InvoiceTransactionSnapshotEntity snapshot) {
        return new InvoiceSnapshotResponse(
                snapshot.getTransactionId(),
                snapshot.getType(),
                snapshot.getExternalId(),
                snapshot.getCurrency(),
                snapshot.getOccurredAt(),
                snapshot.getTotal(),
                readMetadata(snapshot.getMetadata()),
                snapshot.getTaxLines().stream().map(this::toSnapshotTax).toList(),
                snapshot.getFeeLines().stream().map(this::toSnapshotFee).toList());
    }

    private TaxLineResponse toTax(TransactionTaxLineEntity line) {
        return new TaxLineResponse(line.getName(), line.getRate(), line.getAmount());
    }

    private FeeLineResponse toFee(TransactionFeeLineEntity line) {
        return new FeeLineResponse(line.getName(), line.getAmount());
    }

    private TaxLineResponse toSnapshotTax(InvoiceSnapshotTaxLineEntity line) {
        return new TaxLineResponse(line.getName(), line.getRate(), line.getAmount());
    }

    private FeeLineResponse toSnapshotFee(InvoiceSnapshotFeeLineEntity line) {
        return new FeeLineResponse(line.getName(), line.getAmount());
    }

    private TaxLineResponse toTaxRollup(InvoiceTaxRollupEntity line) {
        return new TaxLineResponse(line.getName(), line.getRate(), line.getAmount());
    }

    private FeeLineResponse toFeeRollup(InvoiceFeeRollupEntity line) {
        return new FeeLineResponse(line.getName(), line.getAmount());
    }

    private JsonNode readMetadata(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored metadata is not valid JSON", exception);
        }
    }

    public List<TransactionResponse> transactions(List<TransactionEntity> entities) {
        return entities.stream().map(this::toResponse).toList();
    }
}
