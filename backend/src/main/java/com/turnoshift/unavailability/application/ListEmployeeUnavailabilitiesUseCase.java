package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.EmployeeValidationPort;
import com.turnoshift.unavailability.domain.exceptions.EmployeeNotFoundException;
import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ListEmployeeUnavailabilitiesUseCase {

    private final EmployeeUnavailabilityRepository repository;
    private final EmployeeValidationPort employeeValidationPort;

    public ListEmployeeUnavailabilitiesUseCase(
            EmployeeUnavailabilityRepository repository,
            EmployeeValidationPort employeeValidationPort
    ) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.employeeValidationPort = Objects.requireNonNull(employeeValidationPort, "employeeValidationPort no puede ser nulo");
    }

    public List<EmployeeUnavailability> execute(UUID organizationId, UUID employeeId, boolean includeCancelled) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");

        if (!employeeValidationPort.isEmployeeActive(organizationId, employeeId)) {
            throw new EmployeeNotFoundException("Colaborador no encontrado o inactivo");
        }

        return repository.findByEmployee(organizationId, employeeId, includeCancelled);
    }

    public List<EmployeeUnavailability> execute(
            UUID organizationId, UUID employeeId, Instant from, Instant to, boolean includeCancelled
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(from, "from no puede ser nulo");
        Objects.requireNonNull(to, "to no puede ser nulo");

        if (!from.isBefore(to)) {
            throw new InvalidUnavailabilityDataException("from debe ser anterior a to");
        }

        if (!employeeValidationPort.isEmployeeActive(organizationId, employeeId)) {
            throw new EmployeeNotFoundException("Colaborador no encontrado o inactivo");
        }

        return repository.findByEmployeeAndInterval(organizationId, employeeId, from, to, includeCancelled);
    }
}
