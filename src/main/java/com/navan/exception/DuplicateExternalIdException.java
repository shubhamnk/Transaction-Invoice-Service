package com.navan.exception;

public class DuplicateExternalIdException extends ApiException {

    private final String externalId;

    public DuplicateExternalIdException(String externalId) {
        super(409, "DUPLICATE_EXTERNAL_ID", "A transaction with this external_id already exists");
        this.externalId = externalId;
    }

    public String getExternalId() {
        return externalId;
    }
}
