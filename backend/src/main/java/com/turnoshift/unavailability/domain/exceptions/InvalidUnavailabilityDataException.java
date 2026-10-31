package com.turnoshift.unavailability.domain.exceptions;

public class InvalidUnavailabilityDataException extends RuntimeException {

    public static final String ERROR_CODE = "VALIDATION_FAILED";

    public InvalidUnavailabilityDataException(String message) {
        super(message);
    }

    public InvalidUnavailabilityDataException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
