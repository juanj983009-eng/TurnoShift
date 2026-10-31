package com.turnoshift.unavailability.domain;

import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;

import java.time.Instant;
import java.util.Objects;

public record TimeInterval(Instant startsAt, Instant endsAt) {

    public TimeInterval {
        if (startsAt == null) {
            throw new InvalidUnavailabilityDataException("startsAt no puede ser nulo");
        }
        if (endsAt == null) {
            throw new InvalidUnavailabilityDataException("endsAt no puede ser nulo");
        }
        if (!startsAt.isBefore(endsAt)) {
            throw new InvalidUnavailabilityDataException("startsAt debe ser anterior a endsAt");
        }
    }

    public static TimeInterval of(Instant startsAt, Instant endsAt) {
        return new TimeInterval(startsAt, endsAt);
    }

    public boolean overlaps(TimeInterval other) {
        Objects.requireNonNull(other, "other no puede ser nulo");
        return overlaps(other.startsAt(), other.endsAt());
    }

    public boolean overlaps(Instant otherStartsAt, Instant otherEndsAt) {
        Objects.requireNonNull(otherStartsAt, "otherStartsAt no puede ser nulo");
        Objects.requireNonNull(otherEndsAt, "otherEndsAt no puede ser nulo");
        return this.startsAt.isBefore(otherEndsAt) && otherStartsAt.isBefore(this.endsAt);
    }
}
