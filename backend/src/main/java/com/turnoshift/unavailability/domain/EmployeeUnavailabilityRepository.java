package com.turnoshift.unavailability.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeUnavailabilityRepository {

    EmployeeUnavailability insert(EmployeeUnavailability unavailability);

    Optional<EmployeeUnavailability> findById(UUID organizationId, UUID id);

    List<EmployeeUnavailability> findByEmployee(UUID organizationId, UUID employeeId, boolean includeCancelled);

    List<EmployeeUnavailability> findByEmployeeAndInterval(
            UUID organizationId, UUID employeeId, Instant from, Instant to, boolean includeCancelled
    );

    List<EmployeeUnavailability> findByOrganizationAndInterval(
            UUID organizationId, Instant from, Instant to, boolean includeCancelled
    );

    Optional<EmployeeUnavailability> update(EmployeeUnavailability unavailability, long expectedLockVersion);

    Optional<EmployeeUnavailability> cancel(UUID organizationId, UUID id, UUID actorUserId, long expectedLockVersion);
}
