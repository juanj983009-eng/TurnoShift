package com.turnoshift.team.application;

import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeNotFoundException;
import com.turnoshift.team.domain.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class GetEmployeeUseCase {

    private final EmployeeRepository employeeRepository;

    public GetEmployeeUseCase(EmployeeRepository employeeRepository) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
    }

    public Employee execute(UUID organizationId, UUID employeeId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");

        return employeeRepository.findById(organizationId, employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
