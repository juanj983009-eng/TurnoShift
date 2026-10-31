package com.turnoshift.schedule.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro plano de un turno publicado vigente, utilizado en evaluaciones de frontera,
 * verificación de historial y consultas personales.
 */
public record PublishedShiftRecord(
        UUID assignmentId,
        UUID assignmentRevisionId,
        UUID scheduleId,
        LocalDate weekStart,
        UUID employeeId,
        UUID positionId,
        ShiftInterval interval,
        String employeeNameSnapshot,
        String positionNameSnapshot
) {

    public PublishedShiftRecord {
        Objects.requireNonNull(assignmentId, "assignmentId no puede ser nulo");
        Objects.requireNonNull(assignmentRevisionId, "assignmentRevisionId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");
        Objects.requireNonNull(weekStart, "weekStart no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(positionId, "positionId no puede ser nulo");
        Objects.requireNonNull(interval, "interval no puede ser nulo");
        Objects.requireNonNull(employeeNameSnapshot, "employeeNameSnapshot no puede ser nulo");
        Objects.requireNonNull(positionNameSnapshot, "positionNameSnapshot no puede ser nulo");
    }

    public PublishedShiftRecord(
            UUID assignmentId,
            UUID assignmentRevisionId,
            UUID employeeId,
            UUID positionId,
            Instant startsAt,
            Instant endsAt,
            String employeeNameSnapshot,
            String positionNameSnapshot,
            UUID scheduleId,
            LocalDate weekStart
    ) {
        this(
                assignmentId,
                assignmentRevisionId,
                scheduleId,
                weekStart,
                employeeId,
                positionId,
                ShiftInterval.of(startsAt, endsAt),
                employeeNameSnapshot,
                positionNameSnapshot
        );
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
