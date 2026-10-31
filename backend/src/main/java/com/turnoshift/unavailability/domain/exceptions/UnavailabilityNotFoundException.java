package com.turnoshift.unavailability.domain.exceptions;

public class UnavailabilityNotFoundException extends RuntimeException {

    public static final String ERROR_CODE = "UNAVAILABILITY_NOT_FOUND";

    public UnavailabilityNotFoundException(String message) {
        super(message);
    }

    public UnavailabilityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
