package com.turnoshift.schedule.domain.ports;

import com.turnoshift.schedule.domain.validation.BasePublicationSnapshot;
import com.turnoshift.schedule.domain.validation.EmployeeSnapshot;
import com.turnoshift.schedule.domain.validation.FrontierShiftsSnapshot;
import com.turnoshift.schedule.domain.validation.PolicyVersionSnapshot;
import com.turnoshift.schedule.domain.validation.PositionSnapshot;
import com.turnoshift.schedule.domain.validation.UnavailabilitySnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de consulta para obtener instantáneas del contexto operativo necesarias para la validación.
 */
public interface PlanningContextSnapshotPort {

    Optional<PolicyVersionSnapshot> findActivePolicySnapshot(UUID organizationId);

    Map<UUID, EmployeeSnapshot> findEmployeeSnapshots(UUID organizationId, Set<UUID> employeeIds);

    Map<UUID, PositionSnapshot> findPositionSnapshots(UUID organizationId, Set<UUID> positionIds);

    List<UnavailabilitySnapshot> findActiveUnavailabilities(UUID organizationId, Set<UUID> employeeIds, Instant windowStart, Instant windowEnd);

    FrontierShiftsSnapshot findFrontierShifts(UUID organizationId, UUID siteId, LocalDate weekStart, ZoneId orgZone, int minRestMinutes);

    Optional<BasePublicationSnapshot> findBasePublicationSnapshot(UUID organizationId, UUID publicationId);
}
