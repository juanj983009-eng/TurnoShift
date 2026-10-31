package com.turnoshift.team.domain;

public class InvalidEmployeeDataException extends RuntimeException {
    public InvalidEmployeeDataException(String message) {
        super(message);
    }
}
