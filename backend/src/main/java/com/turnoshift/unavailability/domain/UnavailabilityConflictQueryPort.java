package com.turnoshift.unavailability.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface UnavailabilityConflictQueryPort {

    boolean hasConflict(UUID organizationId, UUID employeeId, Instant shiftStart, Instant shiftEnd);

    List<TimeInterval> findConflictingIntervals(UUID organizationId, UUID employeeId, Instant shiftStart, Instant shiftEnd);

    Map<UUID, List<TimeInterval>> findActiveIntervalsByEmployees(
            UUID organizationId, Set<UUID> employeeIds, Instant rangeStart, Instant rangeEnd
    );
}
