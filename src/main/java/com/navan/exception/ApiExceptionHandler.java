package com.navan.exception;

import com.navan.repository.TransactionRepository;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final TransactionRepository transactionRepository;

    public ApiExceptionHandler(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @ExceptionHandler(DuplicateExternalIdException.class)
    public ResponseEntity<ApiError> duplicate(DuplicateExternalIdException exception) {
        String existingId = transactionRepository.findByExternalId(exception.getExternalId())
                .map(transaction -> transaction.getId())
                .orElse(null);
        return ResponseEntity.status(409).body(ApiError.duplicate(existingId));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiError.of(exception.getStatus(), exception.getError(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> snake(error.getField()) + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Request validation failed";
        }
        return ResponseEntity.badRequest().body(ApiError.of(400, "VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null || cause.getMessage() == null
                ? "Request body is invalid"
                : firstLine(cause.getMessage());
        return ResponseEntity.badRequest().body(ApiError.of(400, "VALIDATION_ERROR", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(
                400,
                "INVALID_PAGINATION",
                exception.getName() + " must be an integer"));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> optimisticLock(ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(409).body(ApiError.of(
                409,
                "CONCURRENT_MODIFICATION",
                "The resource was updated by another request"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException exception) {
        String chain = chain(exception);
        if (chain.contains("FOREIGN KEY")) {
            return ResponseEntity.status(409).body(ApiError.of(
                    409,
                    "TRANSACTION_INVOICED",
                    "The transaction is referenced by an invoice and cannot be removed"));
        }
        if (chain.contains("external_id")) {
            return ResponseEntity.status(409).body(ApiError.of(
                    409,
                    "DUPLICATE_EXTERNAL_ID",
                    "A transaction with this external_id already exists"));
        }
        return ResponseEntity.status(409).body(ApiError.of(409, "CONFLICT", "The request conflicts with existing data"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Unhandled API error", exception);
        return ResponseEntity.internalServerError()
                .body(ApiError.of(500, "INTERNAL_ERROR", "Unexpected server error"));
    }

    private static String snake(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String firstLine(String message) {
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static String chain(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return builder.toString();
    }
}
