package com.turnoshift.unavailability.domain.exceptions;

public class PastIntervalModificationProhibitedException extends RuntimeException {

    public static final String ERROR_CODE = "PAST_INTERVAL_MODIFICATION_PROHIBITED";

    public PastIntervalModificationProhibitedException(String message) {
        super(message);
    }

    public PastIntervalModificationProhibitedException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
