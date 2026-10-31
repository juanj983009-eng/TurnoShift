package com.turnoshift.team.application;

import com.turnoshift.team.domain.DeactivationPreview;
import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeNotFoundException;
import com.turnoshift.team.domain.EmployeeOptimisticLockException;
import com.turnoshift.team.domain.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DeactivateEmployeeUseCase {

    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    public DeactivateEmployeeUseCase(EmployeeRepository employeeRepository, Clock clock) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public DeactivationPreview preview(UUID organizationId, UUID employeeId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");

        Employee existing = employeeRepository.findById(organizationId, employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        // En I2.2 no existen aún publicaciones ni borradores, por lo que blockers siempre está vacío
        return new DeactivationPreview(existing.id(), existing.displayName(), true, List.of());
    }

    public Employee execute(UUID organizationId, UUID employeeId, long expectedLockVersion) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");

        Employee existing = employeeRepository.findById(organizationId, employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        if (existing.lockVersion() != expectedLockVersion) {
            throw new EmployeeOptimisticLockException(employeeId, expectedLockVersion);
        }

        // Idempotencia semántica: si ya está inactivo y la versión esperada es válida, no-op exitoso
        if (!existing.isActive()) {
            return existing;
        }

        Instant now = clock.instant();
        Employee candidate = existing.changeStatus(false, now);

        return employeeRepository.update(candidate, expectedLockVersion)
                .orElseThrow(() -> new EmployeeOptimisticLockException(employeeId, expectedLockVersion));
    }
}
