package com.turnoshift.unavailability.domain.exceptions;

public class EmployeeNotFoundException extends RuntimeException {

    public static final String ERROR_CODE = "EMPLOYEE_NOT_FOUND";

    public EmployeeNotFoundException(String message) {
        super(message);
    }

    public EmployeeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
