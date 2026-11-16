package com.navan.service;

import com.navan.dto.FeeLineRequest;
import com.navan.dto.PageResponse;
import com.navan.dto.TaxLineRequest;
import com.navan.dto.TransactionPatchRequest;
import com.navan.dto.TransactionRequest;
import com.navan.dto.TransactionResponse;
import com.navan.entity.TransactionEntity;
import com.navan.entity.TransactionFeeLineEntity;
import com.navan.entity.TransactionTaxLineEntity;
import com.navan.exception.BadRequestException;
import com.navan.exception.ConflictException;
import com.navan.exception.DuplicateExternalIdException;
import com.navan.exception.NotFoundException;
import com.navan.mapper.ApiMapper;
import com.navan.repository.InvoiceSnapshotRepository;
import com.navan.repository.TransactionRepository;
import com.navan.validation.MetadataValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransactionService {

    private static final Pattern CURRENCY = Pattern.compile("^[A-Za-z]{3}$");
    private static final int WRITE_ATTEMPTS = 5;

    private final TransactionRepository transactionRepository;
    private final InvoiceSnapshotRepository snapshotRepository;
    private final MetadataValidator metadataValidator;
    private final ApiMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public TransactionService(
            TransactionRepository transactionRepository,
            InvoiceSnapshotRepository snapshotRepository,
            MetadataValidator metadataValidator,
            ApiMapper mapper,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.transactionRepository = transactionRepository;
        this.snapshotRepository = snapshotRepository;
        this.metadataValidator = metadataValidator;
        this.mapper = mapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TransactionResponse create(TransactionRequest request) {
        PreparedTransaction prepared = prepareCreate(request);
        RuntimeException lastBusy = null;
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> insert(prepared));
            } catch (RuntimeException exception) {
                if (isUniqueExternalId(exception)) {
                    throw new DuplicateExternalIdException(prepared.externalId());
                }
                if (isBusy(exception) && attempt < WRITE_ATTEMPTS) {
                    lastBusy = exception;
                    continue;
                }
                throw exception;
            }
        }
        throw lastBusy == null ? new IllegalStateException("Transaction create did not complete") : lastBusy;
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(String id) {
        return mapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(PageQuery page) {
        List<String> ids = transactionRepository.findPageIds(page.limit(), page.offset());
        Map<String, TransactionEntity> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            for (TransactionEntity entity : transactionRepository.findAllById(ids)) {
                byId.put(entity.getId(), entity);
            }
        }
        List<TransactionEntity> ordered = new ArrayList<>();
        for (String id : ids) {
            TransactionEntity entity = byId.get(id);
            if (entity != null) {
                ordered.add(entity);
            }
        }
        return new PageResponse<>(
                mapper.transactions(ordered),
                page.limit(),
                page.offset(),
                transactionRepository.count());
    }

    @Transactional
    public TransactionResponse update(String id, TransactionPatchRequest request) {
        if (request == null || !request.hasUpdates()) {
            throw new BadRequestException("VALIDATION_ERROR", "At least one field is required");
        }
        TransactionEntity entity = find(id);
        if (snapshotRepository.existsByTransactionId(id)) {
            throw new ConflictException(
                    "TRANSACTION_INVOICED",
                    "A transaction included on an invoice cannot be changed");
        }
        if (request.getOccurredAt() != null) {
            entity.setOccurredAt(request.getOccurredAt());
        }
        if (request.getTotal() != null) {
            entity.setTotal(Money.normalizeAmount(request.getTotal(), "total"));
        }
        if (request.getMetadata() != null) {
            metadataValidator.validate(entity.getType(), request.getMetadata());
            entity.setMetadata(mapper.writeMetadata(request.getMetadata()));
        }
        if (request.getTaxLines() != null) {
            entity.getTaxLines().clear();
            addTaxLines(entity, request.getTaxLines());
        }
        if (request.getFeeLines() != null) {
            entity.getFeeLines().clear();
            addFeeLines(entity, request.getFeeLines());
        }
        entity.setUpdatedAt(Instant.now(clock));
        return mapper.toResponse(transactionRepository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(String id) {
        TransactionEntity entity = find(id);
        if (snapshotRepository.existsByTransactionId(id)) {
            throw new ConflictException(
                    "TRANSACTION_INVOICED",
                    "A transaction included on an invoice cannot be deleted");
        }
        transactionRepository.delete(entity);
        transactionRepository.flush();
    }

    private TransactionResponse insert(PreparedTransaction prepared) {
        Instant now = Instant.now(clock);
        TransactionEntity entity = new TransactionEntity();
        entity.setId(Ids.newId("txn"));
        entity.setType(prepared.type());
        entity.setExternalId(prepared.externalId());
        entity.setCurrency(prepared.currency());
        entity.setOccurredAt(prepared.occurredAt());
        entity.setTotal(prepared.total());
        entity.setMetadata(prepared.metadata());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        addTaxLines(entity, prepared.taxLines());
        addFeeLines(entity, prepared.feeLines());
        try {
            return mapper.toResponse(transactionRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        }
    }

    private PreparedTransaction prepareCreate(TransactionRequest request) {
        metadataValidator.validate(request.getType(), request.getMetadata());
        return new PreparedTransaction(
                request.getType(),
                normalizeExternalId(request.getExternalId()),
                normalizeCurrency(request.getCurrency()),
                request.getOccurredAt(),
                Money.normalizeAmount(request.getTotal(), "total"),
                mapper.writeMetadata(request.getMetadata()),
                request.getTaxLines(),
                request.getFeeLines());
    }

    private void addTaxLines(TransactionEntity entity, List<TaxLineRequest> lines) {
        int order = 0;
        for (TaxLineRequest line : lines) {
            TransactionTaxLineEntity child = new TransactionTaxLineEntity();
            child.setId(Ids.newId("tax"));
            child.setName(line.getName().trim());
            child.setRate(Money.normalizeRate(line.getRate()));
            child.setAmount(Money.normalizeAmount(line.getAmount(), "tax_lines.amount"));
            child.setLineOrder(order++);
            entity.addTaxLine(child);
        }
    }

    private void addFeeLines(TransactionEntity entity, List<FeeLineRequest> lines) {
        int order = 0;
        for (FeeLineRequest line : lines) {
            TransactionFeeLineEntity child = new TransactionFeeLineEntity();
            child.setId(Ids.newId("fee"));
            child.setName(line.getName().trim());
            child.setAmount(Money.normalizeAmount(line.getAmount(), "fee_lines.amount"));
            child.setLineOrder(order++);
            entity.addFeeLine(child);
        }
    }

    private TransactionEntity find(String id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("TRANSACTION_NOT_FOUND", "Transaction not found"));
    }

    private static String normalizeExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            throw new BadRequestException("VALIDATION_ERROR", "external_id is required");
        }
        String trimmed = externalId.trim();
        if (trimmed.length() > 200) {
            throw new BadRequestException("VALIDATION_ERROR", "external_id must be at most 200 characters");
        }
        return trimmed;
    }

    static String normalizeCurrency(String currency) {
        if (currency == null || !CURRENCY.matcher(currency.trim()).matches()) {
            throw new BadRequestException("VALIDATION_ERROR", "currency must be a 3-letter ISO code");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isUniqueExternalId(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("external_id") && message.toLowerCase(Locale.ROOT).contains("unique")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isBusy(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("sqlite_busy") || lower.contains("database is locked")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private record PreparedTransaction(
            com.navan.enums.TransactionType type,
            String externalId,
            String currency,
            Instant occurredAt,
            java.math.BigDecimal total,
            String metadata,
            List<TaxLineRequest> taxLines,
            List<FeeLineRequest> feeLines) {
    }
}
