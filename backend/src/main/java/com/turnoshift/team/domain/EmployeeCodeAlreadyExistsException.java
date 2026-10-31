package com.turnoshift.team.domain;

public class EmployeeCodeAlreadyExistsException extends RuntimeException {
    private final String code;

    public EmployeeCodeAlreadyExistsException(String code) {
        super("Ya existe un colaborador con el código: " + code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
