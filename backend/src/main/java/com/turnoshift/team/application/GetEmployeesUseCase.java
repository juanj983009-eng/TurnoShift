package com.turnoshift.team.application;

import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GetEmployeesUseCase {

    private final EmployeeRepository employeeRepository;

    public GetEmployeesUseCase(EmployeeRepository employeeRepository) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
    }

    public List<Employee> execute(UUID organizationId, Boolean activeFilter) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        return employeeRepository.findAll(organizationId, activeFilter);
    }
}
