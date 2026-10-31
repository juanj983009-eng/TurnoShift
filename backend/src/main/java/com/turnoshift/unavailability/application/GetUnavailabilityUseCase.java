package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityNotFoundException;

import java.util.Objects;
import java.util.UUID;

public class GetUnavailabilityUseCase {

    private final EmployeeUnavailabilityRepository repository;

    public GetUnavailabilityUseCase(EmployeeUnavailabilityRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
    }

    public EmployeeUnavailability execute(UUID organizationId, UUID id) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(id, "id no puede ser nulo");

        return repository.findById(organizationId, id)
                .orElseThrow(() -> new UnavailabilityNotFoundException("Indisponibilidad no encontrada"));
    }
}
