package com.navan.exception;

public class ConflictException extends ApiException {

    public ConflictException(String error, String message) {
        super(409, error, message);
    }
}
