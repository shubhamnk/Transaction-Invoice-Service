package com.navan.exception;

public class NotFoundException extends ApiException {

    public NotFoundException(String error, String message) {
        super(404, error, message);
    }
}
