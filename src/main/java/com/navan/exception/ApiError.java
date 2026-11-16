package com.navan.exception;

public record ApiError(int status, String error, String message, String existingTransactionId) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, null);
    }

    public static ApiError duplicate(String existingTransactionId) {
        return new ApiError(
                409,
                "DUPLICATE_EXTERNAL_ID",
                "A transaction with this external_id already exists",
                existingTransactionId);
    }
}
