package com.turnoshift.schedule.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Contenido concreto e inmutable de un turno en una revisión publicada.
 */
public record AssignmentRevision(
        UUID id,
        UUID organizationId,
        UUID scheduleId,
        UUID assignmentId,
        int revisionNumber,
        UUID employeeId,
        UUID positionId,
        ShiftInterval interval,
        String employeeNameSnapshot,
        String positionNameSnapshot,
        UUID createdByUserId,
        Instant createdAt
) {

    public AssignmentRevision {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");
        Objects.requireNonNull(assignmentId, "assignmentId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(positionId, "positionId no puede ser nulo");
        Objects.requireNonNull(interval, "interval no puede ser nulo");
        Objects.requireNonNull(createdByUserId, "createdByUserId no puede ser nulo");

        if (revisionNumber <= 0) {
            throw new ScheduleDomainException("revisionNumber debe ser estrictamente positivo");
        }
        if (employeeNameSnapshot == null || employeeNameSnapshot.isBlank()) {
            throw new ScheduleDomainException("employeeNameSnapshot no puede ser nulo ni en blanco");
        }
        if (positionNameSnapshot == null || positionNameSnapshot.isBlank()) {
            throw new ScheduleDomainException("positionNameSnapshot no puede ser nulo ni en blanco");
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
}
