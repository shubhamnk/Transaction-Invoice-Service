package com.navan.exception;

public class BadRequestException extends ApiException {

    public BadRequestException(String error, String message) {
        super(400, error, message);
    }
}
