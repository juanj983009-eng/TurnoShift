package com.turnoshift.schedule.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Value object inmutable que representa el intervalo semiabierto [startsAt, endsAt) de un turno.
 * Garantiza que startsAt < endsAt y que la duración esté dentro de los límites operativos (15 a 960 minutos).
 */
public record ShiftInterval(Instant startsAt, Instant endsAt) {

    public static final int MIN_DURATION_MINUTES = 15;
    public static final int MAX_DURATION_MINUTES = 960; // 16 horas

    public ShiftInterval {
        if (startsAt == null) {
            throw new InvalidShiftIntervalException("startsAt no puede ser nulo");
        }
        if (endsAt == null) {
            throw new InvalidShiftIntervalException("endsAt no puede ser nulo");
        }
        if (!startsAt.isBefore(endsAt)) {
            throw new InvalidShiftIntervalException("startsAt debe ser estrictamente anterior a endsAt");
        }
        long minutes = Duration.between(startsAt, endsAt).toMinutes();
        if (minutes < MIN_DURATION_MINUTES || minutes > MAX_DURATION_MINUTES) {
            throw new InvalidShiftIntervalException(
                    "La duración del turno (" + minutes + " min) debe estar entre " +
                    MIN_DURATION_MINUTES + " y " + MAX_DURATION_MINUTES + " minutos"
            );
        }
    }

    public static ShiftInterval of(Instant startsAt, Instant endsAt) {
        return new ShiftInterval(startsAt, endsAt);
    }

    public int durationMinutes() {
        return (int) Duration.between(startsAt, endsAt).toMinutes();
    }

    public boolean overlaps(ShiftInterval other) {
        Objects.requireNonNull(other, "other no puede ser nulo");
        return overlaps(other.startsAt(), other.endsAt());
    }

    public boolean overlaps(Instant otherStartsAt, Instant otherEndsAt) {
        Objects.requireNonNull(otherStartsAt, "otherStartsAt no puede ser nulo");
        Objects.requireNonNull(otherEndsAt, "otherEndsAt no puede ser nulo");
        return this.startsAt.isBefore(otherEndsAt) && otherStartsAt.isBefore(this.endsAt);
    }
}
