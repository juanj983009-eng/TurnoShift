package com.turnoshift.team.application;

import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeNotFoundException;
import com.turnoshift.team.domain.EmployeeOptimisticLockException;
import com.turnoshift.team.domain.EmployeeRepository;
import com.turnoshift.team.domain.Position;
import com.turnoshift.team.domain.PositionInactiveException;
import com.turnoshift.team.domain.PositionNotFoundException;
import com.turnoshift.team.domain.PositionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class UpdateEmployeeUseCase {

    private final EmployeeRepository employeeRepository;
    private final PositionRepository positionRepository;
    private final Clock clock;

    public UpdateEmployeeUseCase(
            EmployeeRepository employeeRepository,
            PositionRepository positionRepository,
            Clock clock
    ) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public Employee execute(
            UUID organizationId,
            UUID employeeId,
            String newDisplayName,
            UUID newPositionId,
            long expectedLockVersion
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");

        if (newDisplayName == null && newPositionId == null) {
            throw new IllegalArgumentException("Al menos displayName o positionId debe estar presente");
        }

        Employee existing = employeeRepository.findById(organizationId, employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        if (existing.lockVersion() != expectedLockVersion) {
            throw new EmployeeOptimisticLockException(employeeId, expectedLockVersion);
        }

        UUID targetPositionId = existing.positionId();
        if (newPositionId != null) {
            Position position = positionRepository.findById(organizationId, newPositionId)
                    .orElseThrow(() -> new PositionNotFoundException(newPositionId));

            if (!position.isActive()) {
                throw new PositionInactiveException(newPositionId);
            }
            targetPositionId = newPositionId;
        }

        String targetDisplayName = newDisplayName != null ? newDisplayName : existing.displayName();

        Instant now = clock.instant();
        Employee updatedCandidate = existing.update(targetDisplayName, targetPositionId, now);

        return employeeRepository.update(updatedCandidate, expectedLockVersion)
                .orElseThrow(() -> new EmployeeOptimisticLockException(employeeId, expectedLockVersion));
    }
}
