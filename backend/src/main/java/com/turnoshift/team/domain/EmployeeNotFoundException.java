package com.turnoshift.team.domain;

import java.util.UUID;

public class EmployeeNotFoundException extends RuntimeException {
    private final UUID employeeId;

    public EmployeeNotFoundException(UUID employeeId) {
        super("No se encontró el colaborador con id: " + employeeId);
        this.employeeId = employeeId;
    }

    public UUID employeeId() {
        return employeeId;
    }
}
