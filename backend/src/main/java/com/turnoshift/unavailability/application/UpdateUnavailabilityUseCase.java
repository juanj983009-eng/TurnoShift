package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.RestrictedNote;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import com.turnoshift.unavailability.domain.exceptions.PastIntervalModificationProhibitedException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityAlreadyCancelledException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityConcurrentModificationException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class UpdateUnavailabilityUseCase {

    private final EmployeeUnavailabilityRepository repository;
    private final Clock clock;

    public UpdateUnavailabilityUseCase(EmployeeUnavailabilityRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    public EmployeeUnavailability execute(
            UUID organizationId,
            UUID id,
            Instant newStartsAt,
            Instant newEndsAt,
            UnavailabilityReason newReason,
            String newRawNote,
            long expectedLockVersion
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(newStartsAt, "newStartsAt no puede ser nulo");
        Objects.requireNonNull(newEndsAt, "newEndsAt no puede ser nulo");

        Instant now = clock.instant();

        EmployeeUnavailability existing = repository.findById(organizationId, id)
                .orElseThrow(() -> new UnavailabilityNotFoundException("Indisponibilidad no encontrada"));

        if (existing.isCancelled()) {
            throw new UnavailabilityAlreadyCancelledException("No se puede modificar una indisponibilidad cancelada");
        }

        if (!existing.interval().startsAt().isAfter(now)) {
            throw new PastIntervalModificationProhibitedException("No se puede modificar una indisponibilidad que ya inició o finalizó");
        }

        if (!newStartsAt.isAfter(now)) {
            throw new PastIntervalModificationProhibitedException("El nuevo intervalo debe iniciar estrictamente en el futuro");
        }

        TimeInterval newInterval = TimeInterval.of(newStartsAt, newEndsAt);
        RestrictedNote note = newRawNote != null ? RestrictedNote.of(newRawNote) : null;

        EmployeeUnavailability toUpdate = existing.update(newInterval, newReason, note, now);

        return repository.update(toUpdate, expectedLockVersion)
                .orElseThrow(() -> new UnavailabilityConcurrentModificationException("Conflicto de concurrencia optimista al modificar la indisponibilidad"));
    }
}
