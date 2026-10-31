package com.turnoshift.unavailability.infrastructure.adapter;

import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeRepository;
import com.turnoshift.unavailability.domain.EmployeeValidationPort;

import java.util.Objects;
import java.util.UUID;

public class TeamEmployeeValidationAdapter implements EmployeeValidationPort {

    private final EmployeeRepository employeeRepository;

    public TeamEmployeeValidationAdapter(EmployeeRepository employeeRepository) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository no puede ser nulo");
    }

    @Override
    public boolean isEmployeeActive(UUID organizationId, UUID employeeId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        return employeeRepository.findById(organizationId, employeeId)
                .map(Employee::isActive)
                .orElse(false);
    }
}
