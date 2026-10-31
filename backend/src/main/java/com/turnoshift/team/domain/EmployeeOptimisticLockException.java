package com.turnoshift.team.domain;

import java.util.UUID;

public class EmployeeOptimisticLockException extends RuntimeException {
    private final UUID employeeId;
    private final long expectedLockVersion;

    public EmployeeOptimisticLockException(UUID employeeId, long expectedLockVersion) {
        super("Conflicto de concurrencia al actualizar el colaborador " + employeeId + " con versión " + expectedLockVersion);
        this.employeeId = employeeId;
        this.expectedLockVersion = expectedLockVersion;
    }

    public UUID employeeId() {
        return employeeId;
    }

    public long expectedLockVersion() {
        return expectedLockVersion;
    }
}
