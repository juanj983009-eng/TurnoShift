package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ListOrganizationUnavailabilitiesUseCase {

    private final EmployeeUnavailabilityRepository repository;

    public ListOrganizationUnavailabilitiesUseCase(EmployeeUnavailabilityRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
    }

    public List<EmployeeUnavailability> execute(UUID organizationId, Instant from, Instant to, boolean includeCancelled) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(from, "from no puede ser nulo");
        Objects.requireNonNull(to, "to no puede ser nulo");

        if (!from.isBefore(to)) {
            throw new InvalidUnavailabilityDataException("from debe ser anterior a to");
        }

        return repository.findByOrganizationAndInterval(organizationId, from, to, includeCancelled);
    }
}
