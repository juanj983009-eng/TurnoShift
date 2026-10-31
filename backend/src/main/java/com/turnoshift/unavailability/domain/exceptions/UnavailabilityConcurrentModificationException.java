package com.turnoshift.unavailability.domain.exceptions;

public class UnavailabilityConcurrentModificationException extends RuntimeException {

    public static final String ERROR_CODE = "CONCURRENT_MODIFICATION";

    public UnavailabilityConcurrentModificationException(String message) {
        super(message);
    }

    public UnavailabilityConcurrentModificationException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
