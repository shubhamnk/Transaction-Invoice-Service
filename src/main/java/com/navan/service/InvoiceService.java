package com.navan.service;

import com.navan.dto.InvoiceRequest;
import com.navan.dto.InvoiceResponse;
import com.navan.dto.PageResponse;
import com.navan.entity.InvoiceEntity;
import com.navan.entity.InvoiceFeeRollupEntity;
import com.navan.entity.InvoiceSnapshotFeeLineEntity;
import com.navan.entity.InvoiceSnapshotTaxLineEntity;
import com.navan.entity.InvoiceTaxRollupEntity;
import com.navan.entity.InvoiceTransactionSnapshotEntity;
import com.navan.entity.TransactionEntity;
import com.navan.entity.TransactionFeeLineEntity;
import com.navan.entity.TransactionTaxLineEntity;
import com.navan.exception.BadRequestException;
import com.navan.exception.ConflictException;
import com.navan.exception.NotFoundException;
import com.navan.mapper.ApiMapper;
import com.navan.repository.InvoiceRepository;
import com.navan.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final ApiMapper mapper;
    private final Clock clock;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            TransactionRepository transactionRepository,
            ApiMapper mapper,
            Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.transactionRepository = transactionRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        List<TransactionEntity> transactions = loadTransactions(request);
        Instant now = Instant.now(clock);
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setId(Ids.newId("inv"));
        invoice.setCreatedAt(now);
        invoice.setUpdatedAt(now);
        applyContents(invoice, transactions);
        return mapper.toResponse(invoiceRepository.saveAndFlush(invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(String id) {
        return mapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(PageQuery page) {
        List<String> ids = invoiceRepository.findPageIds(page.limit(), page.offset());
        Map<String, InvoiceEntity> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            for (InvoiceEntity entity : invoiceRepository.findAllById(ids)) {
                byId.put(entity.getId(), entity);
            }
        }
        List<InvoiceResponse> items = new ArrayList<>();
        for (String id : ids) {
            InvoiceEntity entity = byId.get(id);
            if (entity != null) {
                items.add(mapper.toResponse(entity));
            }
        }
        return new PageResponse<>(items, page.limit(), page.offset(), invoiceRepository.count());
    }

    @Transactional
    public InvoiceResponse update(String id, InvoiceRequest request) {
        InvoiceEntity invoice = find(id);
        List<TransactionEntity> transactions = loadTransactions(request);
        invoice.getSnapshots().clear();
        invoice.getTaxRollups().clear();
        invoice.getFeeRollups().clear();
        invoiceRepository.flush();
        applyContents(invoice, transactions);
        invoice.setUpdatedAt(Instant.now(clock));
        return mapper.toResponse(invoiceRepository.saveAndFlush(invoice));
    }

    @Transactional
    public void delete(String id) {
        InvoiceEntity invoice = find(id);
        invoiceRepository.delete(invoice);
        invoiceRepository.flush();
    }

    private void applyContents(InvoiceEntity invoice, List<TransactionEntity> transactions) {
        String currency = transactions.get(0).getCurrency();
        invoice.setCurrency(currency);

        int snapshotOrder = 0;
        Map<String, TaxGroup> taxGroups = new LinkedHashMap<>();
        Map<String, BigDecimal> feeGroups = new LinkedHashMap<>();
        BigDecimal total = Money.zero();

        for (TransactionEntity transaction : transactions) {
            total = Money.scale(total.add(transaction.getTotal()));
            InvoiceTransactionSnapshotEntity snapshot = new InvoiceTransactionSnapshotEntity();
            snapshot.setId(Ids.newId("snap"));
            snapshot.setTransactionId(transaction.getId());
            snapshot.setType(transaction.getType());
            snapshot.setExternalId(transaction.getExternalId());
            snapshot.setCurrency(transaction.getCurrency());
            snapshot.setOccurredAt(transaction.getOccurredAt());
            snapshot.setTotal(transaction.getTotal());
            snapshot.setMetadata(transaction.getMetadata());
            snapshot.setSnapshotOrder(snapshotOrder++);

            int taxOrder = 0;
            for (TransactionTaxLineEntity line : transaction.getTaxLines()) {
                InvoiceSnapshotTaxLineEntity copy = new InvoiceSnapshotTaxLineEntity();
                copy.setId(Ids.newId("stx"));
                copy.setName(line.getName());
                copy.setRate(line.getRate());
                copy.setAmount(line.getAmount());
                copy.setLineOrder(taxOrder++);
                snapshot.addTaxLine(copy);
                String taxKey = taxKey(line.getName(), line.getRate());
                taxGroups.merge(
                        taxKey,
                        new TaxGroup(line.getName(), line.getRate(), line.getAmount()),
                        (left, right) -> left.add(right.amount()));
            }

            int feeOrder = 0;
            for (TransactionFeeLineEntity line : transaction.getFeeLines()) {
                InvoiceSnapshotFeeLineEntity copy = new InvoiceSnapshotFeeLineEntity();
                copy.setId(Ids.newId("sfe"));
                copy.setName(line.getName());
                copy.setAmount(line.getAmount());
                copy.setLineOrder(feeOrder++);
                snapshot.addFeeLine(copy);
                feeGroups.merge(line.getName(), line.getAmount(), (left, right) -> Money.scale(left.add(right)));
            }
            invoice.addSnapshot(snapshot);
        }

        BigDecimal taxTotal = Money.zero();
        int rollupOrder = 0;
        for (TaxGroup group : taxGroups.values()) {
            BigDecimal amount = Money.scale(group.amount());
            taxTotal = Money.scale(taxTotal.add(amount));
            InvoiceTaxRollupEntity rollup = new InvoiceTaxRollupEntity();
            rollup.setId(Ids.newId("rtx"));
            rollup.setName(group.name());
            rollup.setRate(Money.normalizeRate(group.rate()));
            rollup.setAmount(amount);
            rollup.setLineOrder(rollupOrder++);
            invoice.addTaxRollup(rollup);
        }

        BigDecimal feeTotal = Money.zero();
        rollupOrder = 0;
        for (Map.Entry<String, BigDecimal> entry : feeGroups.entrySet()) {
            BigDecimal amount = Money.scale(entry.getValue());
            feeTotal = Money.scale(feeTotal.add(amount));
            InvoiceFeeRollupEntity rollup = new InvoiceFeeRollupEntity();
            rollup.setId(Ids.newId("rfe"));
            rollup.setName(entry.getKey());
            rollup.setAmount(amount);
            rollup.setLineOrder(rollupOrder++);
            invoice.addFeeRollup(rollup);
        }

        invoice.setTotal(total);
        invoice.setTaxTotal(taxTotal);
        invoice.setFeeTotal(feeTotal);
        invoice.setSubtotal(Money.scale(total.subtract(taxTotal).subtract(feeTotal)));
    }

    private List<TransactionEntity> loadTransactions(InvoiceRequest request) {
        List<String> ids = normalizeIds(request);
        Map<String, TransactionEntity> byId = new HashMap<>();
        for (TransactionEntity entity : transactionRepository.findAllById(ids)) {
            byId.put(entity.getId(), entity);
        }
        List<String> missing = ids.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new NotFoundException("TRANSACTION_NOT_FOUND", "Transactions not found: " + String.join(", ", missing));
        }
        List<TransactionEntity> ordered = ids.stream().map(byId::get).toList();
        LinkedHashSet<String> currencies = new LinkedHashSet<>();
        for (TransactionEntity transaction : ordered) {
            currencies.add(transaction.getCurrency());
        }
        if (currencies.size() > 1) {
            throw new ConflictException(
                    "MIXED_CURRENCY",
                    "All transactions on an invoice must use the same currency");
        }
        for (TransactionEntity transaction : ordered) {
            transaction.getTaxLines().size();
            transaction.getFeeLines().size();
        }
        return ordered;
    }

    private List<String> normalizeIds(InvoiceRequest request) {
        if (request == null) {
            throw new BadRequestException("VALIDATION_ERROR", "At least one transaction id is required");
        }
        List<String> ids = new ArrayList<>();
        if (request.getTransactionIds() != null) {
            ids.addAll(request.getTransactionIds());
        }
        if (request.getTransactionId() != null) {
            ids.add(request.getTransactionId());
        }
        if (ids.isEmpty()) {
            throw new BadRequestException("VALIDATION_ERROR", "At least one transaction id is required");
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw new BadRequestException("VALIDATION_ERROR", "transaction ids cannot be blank");
            }
            String trimmed = id.trim();
            if (!seen.add(trimmed)) {
                throw new ConflictException(
                        "DUPLICATE_TRANSACTION_IDS",
                        "Duplicate transaction ids are not allowed");
            }
        }
        return new ArrayList<>(seen);
    }

    private InvoiceEntity find(String id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Invoice not found"));
    }

    private static String taxKey(String name, BigDecimal rate) {
        BigDecimal normalized = Money.normalizeRate(rate);
        return name + "\0" + (normalized == null ? "" : normalized.toPlainString());
    }

    private record TaxGroup(String name, BigDecimal rate, BigDecimal amount) {

        private TaxGroup add(BigDecimal extra) {
            return new TaxGroup(name, rate, Money.scale(amount.add(extra)));
        }
    }
}
