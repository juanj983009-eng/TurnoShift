package com.turnoshift.schedule.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Identidad permanente y estable de un turno publicado dentro de un schedule.
 */
public record Assignment(
        UUID id,
        UUID organizationId,
        UUID scheduleId,
        Instant createdAt
) {

    public Assignment {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");
    }
}
