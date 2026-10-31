package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.EmployeeValidationPort;
import com.turnoshift.unavailability.domain.RestrictedNote;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import com.turnoshift.unavailability.domain.exceptions.EmployeeNotFoundException;
import com.turnoshift.unavailability.domain.exceptions.PastIntervalModificationProhibitedException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class CreateUnavailabilityUseCase {

    private final EmployeeUnavailabilityRepository repository;
    private final EmployeeValidationPort employeeValidationPort;
    private final Clock clock;

    public CreateUnavailabilityUseCase(
            EmployeeUnavailabilityRepository repository,
            EmployeeValidationPort employeeValidationPort,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.employeeValidationPort = Objects.requireNonNull(employeeValidationPort, "employeeValidationPort no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    public EmployeeUnavailability execute(
            UUID organizationId,
            UUID employeeId,
            Instant startsAt,
            Instant endsAt,
            UnavailabilityReason reason,
            String rawNote,
            UUID actorUserId
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(startsAt, "startsAt no puede ser nulo");
        Objects.requireNonNull(endsAt, "endsAt no puede ser nulo");
        Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");

        Instant now = clock.instant();

        if (!employeeValidationPort.isEmployeeActive(organizationId, employeeId)) {
            throw new EmployeeNotFoundException("Colaborador no encontrado o inactivo");
        }

        if (!endsAt.isAfter(now)) {
            throw new PastIntervalModificationProhibitedException("No se puede registrar una indisponibilidad en un periodo ya finalizado");
        }

        TimeInterval interval = TimeInterval.of(startsAt, endsAt);
        RestrictedNote note = rawNote != null ? RestrictedNote.of(rawNote) : null;

        EmployeeUnavailability unavailability = EmployeeUnavailability.create(
                organizationId, employeeId, interval, reason, note, actorUserId, now
        );

        return repository.insert(unavailability);
    }
}
