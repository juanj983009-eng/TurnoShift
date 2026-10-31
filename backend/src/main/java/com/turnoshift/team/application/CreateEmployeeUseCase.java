package com.turnoshift.team.application;

import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeCodeAlreadyExistsException;
import com.turnoshift.team.domain.EmployeeLicenseLimitExceededException;
import com.turnoshift.team.domain.EmployeeRepository;
import com.turnoshift.team.domain.NoActiveSiteException;
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
public class CreateEmployeeUseCase {

    private final EmployeeRepository employeeRepository;
    private final PositionRepository positionRepository;
    private final OrganizationConfigurationRepository organizationConfigurationRepository;
    private final Clock clock;

    public CreateEmployeeUseCase(
            EmployeeRepository employeeRepository,
            PositionRepository positionRepository,
            OrganizationConfigurationRepository organizationConfigurationRepository,
            Clock clock
    ) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.organizationConfigurationRepository = Objects.requireNonNull(organizationConfigurationRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public Employee execute(UUID organizationId, String rawCode, String rawDisplayName, UUID positionId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(positionId, "positionId no puede ser nulo");

        Position position = positionRepository.findById(organizationId, positionId)
                .orElseThrow(() -> new PositionNotFoundException(positionId));

        if (!position.isActive()) {
            throw new PositionInactiveException(positionId);
        }

        ActiveSiteView activeSite = organizationConfigurationRepository.findActiveSiteByOrganizationId(organizationId)
                .orElseThrow(() -> new NoActiveSiteException(organizationId));

        Instant now = clock.instant();
        Employee employee = Employee.create(organizationId, rawCode, rawDisplayName, positionId, activeSite.id(), now);

        if (employeeRepository.findByCode(organizationId, employee.code()).isPresent()) {
            throw new EmployeeCodeAlreadyExistsException(employee.code());
        }

        // Lock de licencia transaccional
        employeeRepository.acquireOrganizationLicenseLock(organizationId);

        int maxActiveEmployees = employeeRepository.getMaxActiveEmployees(organizationId);
        long currentActive = employeeRepository.countActiveEmployees(organizationId);

        if (currentActive >= maxActiveEmployees) {
            throw new EmployeeLicenseLimitExceededException(organizationId, maxActiveEmployees);
        }

        return employeeRepository.insert(employee);
    }
}
