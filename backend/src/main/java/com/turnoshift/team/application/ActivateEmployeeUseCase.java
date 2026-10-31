package com.turnoshift.team.application;

import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeLicenseLimitExceededException;
import com.turnoshift.team.domain.EmployeeNotFoundException;
import com.turnoshift.team.domain.EmployeeOptimisticLockException;
import com.turnoshift.team.domain.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ActivateEmployeeUseCase {

    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    public ActivateEmployeeUseCase(EmployeeRepository employeeRepository, Clock clock) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public Employee execute(UUID organizationId, UUID employeeId, long expectedLockVersion) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");

        Employee existing = employeeRepository.findById(organizationId, employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        if (existing.lockVersion() != expectedLockVersion) {
            throw new EmployeeOptimisticLockException(employeeId, expectedLockVersion);
        }

        // Idempotencia semántica: si ya está activo y la versión esperada es válida, no-op exitoso
        if (existing.isActive()) {
            return existing;
        }

        // Lock de licencia transaccional
        employeeRepository.acquireOrganizationLicenseLock(organizationId);

        int maxActiveEmployees = employeeRepository.getMaxActiveEmployees(organizationId);
        long currentActive = employeeRepository.countActiveEmployees(organizationId);

        if (currentActive >= maxActiveEmployees) {
            throw new EmployeeLicenseLimitExceededException(organizationId, maxActiveEmployees);
        }

        Instant now = clock.instant();
        Employee candidate = existing.changeStatus(true, now);

        return employeeRepository.update(candidate, expectedLockVersion)
                .orElseThrow(() -> new EmployeeOptimisticLockException(employeeId, expectedLockVersion));
    }
}
