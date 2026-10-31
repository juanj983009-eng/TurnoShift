package com.turnoshift.schedule.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Línea de asignación de turno editable dentro de un borrador semanal.
 */
public record DraftAssignment(
        UUID id,
        UUID organizationId,
        UUID scheduleId,
        UUID draftId,
        UUID assignmentId,
        UUID baseRevisionId,
        UUID employeeId,
        UUID positionId,
        ShiftInterval interval,
        Instant createdAt,
        Instant updatedAt
) {

    public DraftAssignment {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");
        Objects.requireNonNull(draftId, "draftId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(positionId, "positionId no puede ser nulo");
        Objects.requireNonNull(interval, "interval no puede ser nulo");

        boolean hasAssignment = assignmentId != null;
        boolean hasBaseRev = baseRevisionId != null;
        if (hasAssignment != hasBaseRev) {
            throw new ScheduleDomainException(
                    "assignmentId y baseRevisionId deben ser ambos nulos (turno nuevo) o ambos no nulos (turno existente)"
            );
        }
    }

    public Instant startsAt() {
        return interval.startsAt();
    }

    public Instant endsAt() {
        return interval.endsAt();
    }

    public int durationMinutes() {
        return interval.durationMinutes();
    }

    public ShiftInterval timeInterval() {
        return interval;
    }

    public boolean overlaps(DraftAssignment other) {
        Objects.requireNonNull(other, "other no puede ser nulo");
        return this.interval.overlaps(other.interval());
    }
}
