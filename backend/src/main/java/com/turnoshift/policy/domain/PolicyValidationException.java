package com.turnoshift.policy.domain;

public class PolicyValidationException extends RuntimeException {

    private final String errorCode;

    public PolicyValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
