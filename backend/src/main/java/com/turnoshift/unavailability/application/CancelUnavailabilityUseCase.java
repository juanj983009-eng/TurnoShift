package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.exceptions.PastIntervalModificationProhibitedException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityAlreadyCancelledException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityConcurrentModificationException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class CancelUnavailabilityUseCase {

    private final EmployeeUnavailabilityRepository repository;
    private final Clock clock;

    public CancelUnavailabilityUseCase(EmployeeUnavailabilityRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    public EmployeeUnavailability execute(UUID organizationId, UUID id, UUID actorUserId, long expectedLockVersion) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");

        Instant now = clock.instant();

        EmployeeUnavailability existing = repository.findById(organizationId, id)
                .orElseThrow(() -> new UnavailabilityNotFoundException("Indisponibilidad no encontrada"));

        if (existing.isCancelled()) {
            throw new UnavailabilityAlreadyCancelledException("La indisponibilidad ya se encuentra cancelada");
        }

        if (!existing.interval().startsAt().isAfter(now)) {
            throw new PastIntervalModificationProhibitedException("No se puede cancelar una indisponibilidad que ya inició o finalizó");
        }

        return repository.cancel(organizationId, id, actorUserId, expectedLockVersion)
                .orElseThrow(() -> new UnavailabilityConcurrentModificationException("Conflicto de concurrencia optimista al cancelar la indisponibilidad"));
    }
}
