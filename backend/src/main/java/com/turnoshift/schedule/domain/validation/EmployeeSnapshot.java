package com.turnoshift.schedule.domain.validation;

import java.util.Objects;
import java.util.UUID;

/**
 * Instantánea del estado de un colaborador utilizada en la evaluación de validaciones.
 */
public record EmployeeSnapshot(
        UUID employeeId,
        String displayName,
        UUID currentPositionId,
        boolean isActive
) {

    public EmployeeSnapshot {
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(displayName, "displayName no puede ser nulo");
    }
}
