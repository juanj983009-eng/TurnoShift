package com.turnoshift.policy.domain;

public class PolicyDataAccessException extends RuntimeException {

    public PolicyDataAccessException(String message) {
        super(message);
    }

    public PolicyDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
