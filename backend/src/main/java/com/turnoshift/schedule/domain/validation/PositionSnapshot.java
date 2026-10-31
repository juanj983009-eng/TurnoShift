package com.turnoshift.schedule.domain.validation;

import java.util.Objects;
import java.util.UUID;

/**
 * Instantánea del estado y nombre de un puesto de trabajo en la evaluación de validaciones.
 */
public record PositionSnapshot(
        UUID positionId,
        String name,
        boolean isActive
) {
    public PositionSnapshot {
        Objects.requireNonNull(positionId, "positionId no puede ser nulo");
        Objects.requireNonNull(name, "name no puede ser nulo");
    }
}
