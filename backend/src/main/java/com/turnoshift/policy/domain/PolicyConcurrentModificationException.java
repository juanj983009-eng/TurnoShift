package com.turnoshift.policy.domain;

public class PolicyConcurrentModificationException extends RuntimeException {

    public PolicyConcurrentModificationException(String message) {
        super(message);
    }
}
