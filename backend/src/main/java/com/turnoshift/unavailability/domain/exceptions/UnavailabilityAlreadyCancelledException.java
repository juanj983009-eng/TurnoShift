package com.turnoshift.unavailability.domain.exceptions;

public class UnavailabilityAlreadyCancelledException extends RuntimeException {

    public static final String ERROR_CODE = "UNAVAILABILITY_ALREADY_CANCELLED";

    public UnavailabilityAlreadyCancelledException(String message) {
        super(message);
    }

    public UnavailabilityAlreadyCancelledException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
