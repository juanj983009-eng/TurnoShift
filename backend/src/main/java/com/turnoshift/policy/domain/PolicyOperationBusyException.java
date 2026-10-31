package com.turnoshift.policy.domain;

public class PolicyOperationBusyException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
            "El recurso de políticas de la organización está ocupado por otra operación concurrente.";

    public PolicyOperationBusyException() {
        super(DEFAULT_MESSAGE);
    }

    public PolicyOperationBusyException(String message) {
        super(message);
    }
}
