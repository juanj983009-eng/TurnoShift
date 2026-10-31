package com.turnoshift.schedule.domain.validation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Instantánea de una indisponibilidad de colaborador para verificar solapamientos.
 * No expone ni almacena restricted_note para preservar la confidencialidad.
 */
public record UnavailabilitySnapshot(
        UUID unavailabilityId,
        UUID employeeId,
        Instant startsAt,
        Instant endsAt,
        boolean isCancelled
) {
    public UnavailabilitySnapshot {
        Objects.requireNonNull(unavailabilityId, "unavailabilityId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(startsAt, "startsAt no puede ser nulo");
        Objects.requireNonNull(endsAt, "endsAt no puede ser nulo");
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt debe ser anterior a endsAt");
        }
    }
}
