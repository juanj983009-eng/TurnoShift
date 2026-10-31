package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityConflictQueryPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class UnavailabilityConflictQueryService implements UnavailabilityConflictQueryPort {

    private final EmployeeUnavailabilityRepository repository;

    public UnavailabilityConflictQueryService(EmployeeUnavailabilityRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
    }

    @Override
    public boolean hasConflict(UUID organizationId, UUID employeeId, Instant shiftStart, Instant shiftEnd) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(shiftStart, "shiftStart no puede ser nulo");
        Objects.requireNonNull(shiftEnd, "shiftEnd no puede ser nulo");

        List<EmployeeUnavailability> unavailabilities = repository.findByEmployeeAndInterval(
                organizationId, employeeId, shiftStart, shiftEnd, false
        );

        return unavailabilities.stream().anyMatch(u -> u.interval().overlaps(shiftStart, shiftEnd));
    }

    @Override
    public List<TimeInterval> findConflictingIntervals(
            UUID organizationId, UUID employeeId, Instant shiftStart, Instant shiftEnd
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(shiftStart, "shiftStart no puede ser nulo");
        Objects.requireNonNull(shiftEnd, "shiftEnd no puede ser nulo");

        List<EmployeeUnavailability> unavailabilities = repository.findByEmployeeAndInterval(
                organizationId, employeeId, shiftStart, shiftEnd, false
        );

        return unavailabilities.stream()
                .filter(u -> u.interval().overlaps(shiftStart, shiftEnd))
                .map(EmployeeUnavailability::interval)
                .toList();
    }

    @Override
    public Map<UUID, List<TimeInterval>> findActiveIntervalsByEmployees(
            UUID organizationId, Set<UUID> employeeIds, Instant rangeStart, Instant rangeEnd
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeIds, "employeeIds no puede ser nulo");
        Objects.requireNonNull(rangeStart, "rangeStart no puede ser nulo");
        Objects.requireNonNull(rangeEnd, "rangeEnd no puede ser nulo");

        Map<UUID, List<TimeInterval>> result = new LinkedHashMap<>();
        for (UUID empId : employeeIds) {
            result.put(empId, new ArrayList<>());
        }

        if (employeeIds.isEmpty()) {
            return result;
        }

        List<EmployeeUnavailability> unavailabilities = repository.findByOrganizationAndInterval(
                organizationId, rangeStart, rangeEnd, false
        );

        for (EmployeeUnavailability u : unavailabilities) {
            if (employeeIds.contains(u.employeeId()) && u.interval().overlaps(rangeStart, rangeEnd)) {
                result.get(u.employeeId()).add(u.interval());
            }
        }

        return result;
    }
}
