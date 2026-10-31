package com.turnoshift.team.infrastructure;

public class EmployeeDataAccessException extends RuntimeException {
    public EmployeeDataAccessException(String message) {
        super(message);
    }

    public EmployeeDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmployeeDataAccessException(Throwable cause) {
        super(cause);
    }
}
