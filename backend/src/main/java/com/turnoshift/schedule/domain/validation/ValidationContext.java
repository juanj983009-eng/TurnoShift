package com.turnoshift.schedule.domain.validation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Contexto inmutable de datos necesarios para la evaluación pura y determinista de un borrador de horario.
 */
public record ValidationContext(
        UUID organizationId,
        UUID siteId,
        UUID scheduleId,
        LocalDate weekStart,
        ZoneId orgZone,
        PolicyVersionSnapshot currentPolicy,
        BasePublicationSnapshot basePublication,
        Map<UUID, EmployeeSnapshot> employeeSnapshots,
        Map<UUID, PositionSnapshot> positionSnapshots,
        List<UnavailabilitySnapshot> unavailabilities,
        FrontierShiftsSnapshot frontierShifts,
        Instant now,
        long draftLockVersion
) {
    public ValidationContext {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(siteId, "siteId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");
        Objects.requireNonNull(weekStart, "weekStart no puede ser nulo");
        Objects.requireNonNull(orgZone, "orgZone no puede ser nulo");
        // currentPolicy y basePublication pueden ser nulos
        employeeSnapshots = employeeSnapshots == null ? Map.of() : Map.copyOf(employeeSnapshots);
        positionSnapshots = positionSnapshots == null ? Map.of() : Map.copyOf(positionSnapshots);
        unavailabilities = unavailabilities == null ? List.of() : List.copyOf(unavailabilities);
        frontierShifts = frontierShifts == null ? new FrontierShiftsSnapshot(List.of(), List.of()) : frontierShifts;
        Objects.requireNonNull(now, "now no puede ser nulo");
        if (draftLockVersion < 0) {
            throw new IllegalArgumentException("draftLockVersion no puede ser negativo");
        }
    }
}
