package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnavailabilityConflictQueryServiceTest {

    @Mock
    private EmployeeUnavailabilityRepository repository;

    private UnavailabilityConflictQueryService queryService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        queryService = new UnavailabilityConflictQueryService(repository);
    }

    @Test
    @DisplayName("hasConflict retorna true cuando existe al menos un intervalo solapado")
    void hasConflictRetornaTrueSiSolapa() {
        Instant shiftStart = Instant.parse("2026-10-15T08:00:00Z");
        Instant shiftEnd = Instant.parse("2026-10-15T16:00:00Z");

        TimeInterval unavInterval = TimeInterval.of(
                Instant.parse("2026-10-15T12:00:00Z"),
                Instant.parse("2026-10-15T18:00:00Z")
        );
        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                UUID.randomUUID(), orgId, empId, unavInterval,
                UnavailabilityReason.MEDICO, null, UUID.randomUUID(),
                Instant.now(), Instant.now(), 0L, null
        );

        when(repository.findByEmployeeAndInterval(orgId, empId, shiftStart, shiftEnd, false))
                .thenReturn(List.of(unav));

        assertThat(queryService.hasConflict(orgId, empId, shiftStart, shiftEnd)).isTrue();
    }

    @Test
    @DisplayName("hasConflict retorna false cuando no existen intervalos solapados")
    void hasConflictRetornaFalseSiNoSolapa() {
        Instant shiftStart = Instant.parse("2026-10-15T08:00:00Z");
        Instant shiftEnd = Instant.parse("2026-10-15T16:00:00Z");

        when(repository.findByEmployeeAndInterval(orgId, empId, shiftStart, shiftEnd, false))
                .thenReturn(List.of());

        assertThat(queryService.hasConflict(orgId, empId, shiftStart, shiftEnd)).isFalse();
    }

    @Test
    @DisplayName("findConflictingIntervals retorna la lista de intervalos en conflicto")
    void findConflictingIntervalsRetornaLista() {
        Instant shiftStart = Instant.parse("2026-10-15T08:00:00Z");
        Instant shiftEnd = Instant.parse("2026-10-15T16:00:00Z");

        TimeInterval unavInterval = TimeInterval.of(
                Instant.parse("2026-10-15T12:00:00Z"),
                Instant.parse("2026-10-15T18:00:00Z")
        );
        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                UUID.randomUUID(), orgId, empId, unavInterval,
                UnavailabilityReason.MEDICO, null, UUID.randomUUID(),
                Instant.now(), Instant.now(), 0L, null
        );

        when(repository.findByEmployeeAndInterval(orgId, empId, shiftStart, shiftEnd, false))
                .thenReturn(List.of(unav));

        List<TimeInterval> conflicts = queryService.findConflictingIntervals(orgId, empId, shiftStart, shiftEnd);

        assertThat(conflicts).containsExactly(unavInterval);
    }

    @Test
    @DisplayName("findActiveIntervalsByEmployees agrupa intervalos activos por empleado")
    void findActiveIntervalsByEmployeesAgrupaPorEmpleado() {
        UUID emp1 = UUID.randomUUID();
        UUID emp2 = UUID.randomUUID();
        Instant rangeStart = Instant.parse("2026-10-01T00:00:00Z");
        Instant rangeEnd = Instant.parse("2026-10-31T23:59:59Z");

        TimeInterval interval1 = TimeInterval.of(
                Instant.parse("2026-10-05T08:00:00Z"),
                Instant.parse("2026-10-05T16:00:00Z")
        );
        EmployeeUnavailability unav1 = EmployeeUnavailability.reconstruct(
                UUID.randomUUID(), orgId, emp1, interval1,
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                Instant.now(), Instant.now(), 0L, null
        );

        when(repository.findByOrganizationAndInterval(orgId, rangeStart, rangeEnd, false))
                .thenReturn(List.of(unav1));

        Map<UUID, List<TimeInterval>> result = queryService.findActiveIntervalsByEmployees(
                orgId, Set.of(emp1, emp2), rangeStart, rangeEnd
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(emp1)).containsExactly(interval1);
        assertThat(result.get(emp2)).isEmpty();
    }
}
