package com.turnoshift.team.domain;

import java.util.UUID;

public class EmployeeLicenseLimitExceededException extends RuntimeException {
    private final UUID organizationId;
    private final int maxActiveEmployees;

    public EmployeeLicenseLimitExceededException(UUID organizationId, int maxActiveEmployees) {
        super("Se ha alcanzado el límite de colaboradores activos (" + maxActiveEmployees + ") para la organización " + organizationId);
        this.organizationId = organizationId;
        this.maxActiveEmployees = maxActiveEmployees;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public int maxActiveEmployees() {
        return maxActiveEmployees;
    }
}
