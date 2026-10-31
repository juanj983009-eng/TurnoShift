package com.turnoshift.schedule.domain.validation;

import com.turnoshift.schedule.domain.DraftAssignment;
import com.turnoshift.schedule.domain.PublishedShiftRecord;
import com.turnoshift.schedule.domain.ShiftInterval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit: ScheduleValidationEngine — motor determinista de validación de cuadrantes")
class ScheduleValidationEngineTest {

    private UUID orgId;
    private UUID siteId;
    private UUID scheduleId;
    private LocalDate weekStart;
    private ZoneId orgZone;
    private PolicyVersionSnapshot policy;
    private UUID employeeId;
    private UUID positionId;
    private Instant now;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        scheduleId = UUID.randomUUID();
        weekStart = LocalDate.of(2026, 11, 2); // Lunes
        orgZone = ZoneId.of("America/Bogota");
        policy = new PolicyVersionSnapshot(UUID.randomUUID(), 1, 660, 2400); // 11h descanso, 40h límite semanal
        employeeId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        now = weekStart.atStartOfDay(orgZone).toInstant().minus(Duration.ofDays(1)); // Domingo previo
    }

    private ValidationContext createContext(
            PolicyVersionSnapshot policyVersion,
            BasePublicationSnapshot basePublication,
            Map<UUID, EmployeeSnapshot> employees,
            Map<UUID, PositionSnapshot> positions,
            List<UnavailabilitySnapshot> unavailabilities,
            FrontierShiftsSnapshot frontierShifts,
            Instant testNow
    ) {
        return new ValidationContext(
                orgId,
                siteId,
                scheduleId,
                weekStart,
                orgZone,
                policyVersion,
                basePublication,
                employees != null ? employees : Map.of(employeeId, new EmployeeSnapshot(employeeId, "Juan Pérez", positionId, true)),
                positions != null ? positions : Map.of(positionId, new PositionSnapshot(positionId, "Cajero", true)),
                unavailabilities,
                frontierShifts,
                testNow != null ? testNow : now,
                0L
        );
    }

    private DraftAssignment createAssignment(
            UUID draftAssignmentId,
            UUID assignId,
            UUID baseRevId,
            UUID empId,
            UUID posId,
            Instant startsAt,
            Instant endsAt
    ) {
        return new DraftAssignment(
                draftAssignmentId != null ? draftAssignmentId : UUID.randomUUID(),
                orgId,
                scheduleId,
                UUID.randomUUID(),
                assignId,
                baseRevId,
                empId != null ? empId : employeeId,
                posId != null ? posId : positionId,
                ShiftInterval.of(startsAt, endsAt),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Sin política operativa configurada: emite error bloqueante POLICY_NOT_CONFIGURED")
    void sinPoliticaConfigurada_bloqueaPublicacion() {
        ValidationContext ctx = createContext(null, null, null, null, null, null, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of());

        assertThat(result.canPublish()).isFalse();
        assertThat(result.blockingErrors()).hasSize(1);
        assertThat(result.blockingErrors().get(0).code()).isEqualTo(BlockingErrorCode.POLICY_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("Horario vacío con política: permite publicar según DEC-03")
    void horarioVacio_permitePublicacion() {
        ValidationContext ctx = createContext(policy, null, null, null, null, null, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of());

        assertThat(result.canPublish()).isTrue();
        assertThat(result.emptySchedule()).isTrue();
        assertThat(result.blockingErrors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.validationFingerprint()).hasSize(32);
    }

    @Test
    @DisplayName("Turno nuevo en el pasado: emite error bloqueante PAST_SHIFT_MODIFICATION")
    void turnoNuevoEnPasado_bloqueaPublicacion() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        Instant currentNow = mondayStart.plus(Duration.ofHours(10)); // Lunes 10:00

        // Turno nuevo de lunes 08:00 a 16:00 (inició a las 08:00, antes de now)
        DraftAssignment pastShift = createAssignment(
                null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(8)),
                mondayStart.plus(Duration.ofHours(16))
        );

        ValidationContext ctx = createContext(policy, null, null, null, null, null, currentNow);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(pastShift));

        assertThat(result.canPublish()).isFalse();
        assertThat(result.blockingErrors()).anyMatch(e -> e.code() == BlockingErrorCode.PAST_SHIFT_MODIFICATION);
    }

    @Test
    @DisplayName("Turno base publicado ya iniciado que permanece idéntico: se permite sin falsos positivos")
    void turnoBaseIniciadoIdentico_sePermite() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        Instant currentNow = mondayStart.plus(Duration.ofHours(10)); // Lunes 10:00

        UUID assignmentId = UUID.randomUUID();
        UUID baseRevId = UUID.randomUUID();
        Instant shiftStart = mondayStart.plus(Duration.ofHours(8));
        Instant shiftEnd = mondayStart.plus(Duration.ofHours(16));

        // Turno idéntico en borrador
        DraftAssignment draftShift = createAssignment(
                null, assignmentId, baseRevId, employeeId, positionId, shiftStart, shiftEnd
        );

        // Turno en publicación base
        PublishedShiftRecord baseRecord = new PublishedShiftRecord(
                assignmentId, baseRevId, employeeId, positionId, shiftStart, shiftEnd,
                "Juan Pérez", "Cajero", scheduleId, weekStart
        );
        BasePublicationSnapshot basePub = new BasePublicationSnapshot(UUID.randomUUID(), List.of(baseRecord));

        ValidationContext ctx = createContext(policy, basePub, null, null, null, null, currentNow);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(draftShift));

        assertThat(result.canPublish()).isTrue();
        assertThat(result.blockingErrors()).isEmpty();
    }

    @Test
    @DisplayName("Turno base publicado ya iniciado que fue modificado: emite PAST_SHIFT_MODIFICATION")
    void turnoBaseIniciadoModificado_bloqueaPublicacion() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        Instant currentNow = mondayStart.plus(Duration.ofHours(10)); // Lunes 10:00

        UUID assignmentId = UUID.randomUUID();
        UUID baseRevId = UUID.randomUUID();
        Instant shiftStart = mondayStart.plus(Duration.ofHours(8));
        Instant shiftEnd = mondayStart.plus(Duration.ofHours(16));

        // En borrador se modificó la hora de fin a 17:00
        DraftAssignment alteredDraftShift = createAssignment(
                null, assignmentId, baseRevId, employeeId, positionId,
                shiftStart, shiftEnd.plus(Duration.ofHours(1))
        );

        PublishedShiftRecord baseRecord = new PublishedShiftRecord(
                assignmentId, baseRevId, employeeId, positionId, shiftStart, shiftEnd,
                "Juan Pérez", "Cajero", scheduleId, weekStart
        );
        BasePublicationSnapshot basePub = new BasePublicationSnapshot(UUID.randomUUID(), List.of(baseRecord));

        ValidationContext ctx = createContext(policy, basePub, null, null, null, null, currentNow);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(alteredDraftShift));

        assertThat(result.canPublish()).isFalse();
        assertThat(result.blockingErrors()).anyMatch(e -> e.code() == BlockingErrorCode.PAST_SHIFT_MODIFICATION);
    }

    @Test
    @DisplayName("Turno base publicado ya iniciado que fue eliminado: emite PAST_SHIFT_MODIFICATION")
    void turnoBaseIniciadoEliminado_bloqueaPublicacion() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        Instant currentNow = mondayStart.plus(Duration.ofHours(10)); // Lunes 10:00

        UUID assignmentId = UUID.randomUUID();
        UUID baseRevId = UUID.randomUUID();
        Instant shiftStart = mondayStart.plus(Duration.ofHours(8));
        Instant shiftEnd = mondayStart.plus(Duration.ofHours(16));

        PublishedShiftRecord baseRecord = new PublishedShiftRecord(
                assignmentId, baseRevId, employeeId, positionId, shiftStart, shiftEnd,
                "Juan Pérez", "Cajero", scheduleId, weekStart
        );
        BasePublicationSnapshot basePub = new BasePublicationSnapshot(UUID.randomUUID(), List.of(baseRecord));

        // Borrador vacío (se eliminó el turno que ya empezó)
        ValidationContext ctx = createContext(policy, basePub, null, null, null, null, currentNow);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of());

        assertThat(result.canPublish()).isFalse();
        assertThat(result.blockingErrors()).anyMatch(e -> e.code() == BlockingErrorCode.PAST_SHIFT_MODIFICATION);
    }

    @Test
    @DisplayName("Colaborador inactivo: emite error bloqueante EMPLOYEE_INACTIVE")
    void colaboradorInactivo_bloqueaPublicacion() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        DraftAssignment shift = createAssignment(
                null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(8)), mondayStart.plus(Duration.ofHours(16))
        );

        Map<UUID, EmployeeSnapshot> inactiveEmployees = Map.of(
                employeeId, new EmployeeSnapshot(employeeId, "Juan Pérez", positionId, false)
        );

        ValidationContext ctx = createContext(policy, null, inactiveEmployees, null, null, null, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(shift));

        assertThat(result.canPublish()).isFalse();
        assertThat(result.blockingErrors()).anyMatch(e -> e.code() == BlockingErrorCode.EMPLOYEE_INACTIVE);
    }

    @Test
    @DisplayName("Puesto inactivo: emite error bloqueante POSITION_INACTIVE")
    void puestoInactivo_bloqueaPublicacion() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        DraftAssignment shift = createAssignment(
                null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(8)), mondayStart.plus(Duration.ofHours(16))
        );

        Map<UUID, PositionSnapshot> inactivePositions = Map.of(
                positionId, new PositionSnapshot(positionId, "Cajero", false)
        );

        ValidationContext ctx = createContext(policy, null, null, inactivePositions, null, null, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(shift));

        assertThat(result.canPublish()).isFalse();
        assertThat(result.blockingErrors()).anyMatch(e -> e.code() == BlockingErrorCode.POSITION_INACTIVE);
    }

    @Test
    @DisplayName("Puesto asignado distinto al puesto principal del colaborador: emite POSITION_MISMATCH")
    void puestoDistintoAlPrincipal_bloqueaPublicacion() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        UUID otherPositionId = UUID.randomUUID();

        DraftAssignment shift = createAssignment(
                null, null, null, employeeId, otherPositionId,
                mondayStart.plus(Duration.ofHours(8)), mondayStart.plus(Duration.ofHours(16))
        );

        Map<UUID, PositionSnapshot> positions = Map.of(
                positionId, new PositionSnapshot(positionId, "Cajero", true),
                otherPositionId, new PositionSnapshot(otherPositionId, "Supervisor", true)
        );

        ValidationContext ctx = createContext(policy, null, null, positions, null, null, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(shift));

        assertThat(result.canPublish()).isFalse();
        assertThat(result.blockingErrors()).anyMatch(e -> e.code() == BlockingErrorCode.POSITION_MISMATCH);
    }

    @Test
    @DisplayName("Indisponibilidad activa solapada con turno: emite EMPLOYEE_UNAVAILABLE sin restricted_note")
    void indisponibilidadActivaSolapada_bloqueaPublicacion() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        Instant shiftStart = mondayStart.plus(Duration.ofHours(8));
        Instant shiftEnd = mondayStart.plus(Duration.ofHours(16));

        DraftAssignment shift = createAssignment(null, null, null, employeeId, positionId, shiftStart, shiftEnd);

        UnavailabilitySnapshot unavail = new UnavailabilitySnapshot(
                UUID.randomUUID(), employeeId,
                mondayStart.plus(Duration.ofHours(10)),
                mondayStart.plus(Duration.ofHours(14)),
                false // no cancelada
        );

        ValidationContext ctx = createContext(policy, null, null, null, List.of(unavail), null, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(shift));

        assertThat(result.canPublish()).isFalse();
        ValidationBlockingError error = result.blockingErrors().stream()
                .filter(e -> e.code() == BlockingErrorCode.EMPLOYEE_UNAVAILABLE)
                .findFirst()
                .orElseThrow();

        assertThat(error.details()).doesNotContainKey("restricted_note");
        assertThat(error.details()).containsKey("conflictStartsAt");
    }

    @Test
    @DisplayName("Indisponibilidad cancelada: no bloquea la publicación")
    void indisponibilidadCancelada_sePermite() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        DraftAssignment shift = createAssignment(
                null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(8)), mondayStart.plus(Duration.ofHours(16))
        );

        UnavailabilitySnapshot cancelled = new UnavailabilitySnapshot(
                UUID.randomUUID(), employeeId,
                mondayStart.plus(Duration.ofHours(10)),
                mondayStart.plus(Duration.ofHours(14)),
                true // cancelada
        );

        ValidationContext ctx = createContext(policy, null, null, null, List.of(cancelled), null, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(shift));

        assertThat(result.canPublish()).isTrue();
        assertThat(result.blockingErrors()).isEmpty();
    }

    @Test
    @DisplayName("Dos turnos del mismo colaborador que se solapan en la semana: emite SHIFT_OVERLAP")
    void dosTurnosMismoColaboradorSolapados_bloqueaPublicacion() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        DraftAssignment shift1 = createAssignment(
                null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(8)), mondayStart.plus(Duration.ofHours(14))
        );
        DraftAssignment shift2 = createAssignment(
                null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(12)), mondayStart.plus(Duration.ofHours(18))
        );

        ValidationContext ctx = createContext(policy, null, null, null, null, null, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(shift1, shift2));

        assertThat(result.canPublish()).isFalse();
        assertThat(result.blockingErrors()).anyMatch(e -> e.code() == BlockingErrorCode.SHIFT_OVERLAP);
    }

    @Test
    @DisplayName("Turno de borrador que solapa con turno de frontera de W-1: emite SHIFT_OVERLAP")
    void solapamientoConFronteraWMinus1_bloqueaPublicacion() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();

        // Turno de W-1 domingo noche que termina el lunes a las 06:00
        PublishedShiftRecord prevShift = new PublishedShiftRecord(
                UUID.randomUUID(), UUID.randomUUID(), employeeId, positionId,
                mondayStart.minus(Duration.ofHours(4)), mondayStart.plus(Duration.ofHours(4)),
                "Juan Pérez", "Cajero", UUID.randomUUID(), weekStart.minusWeeks(1)
        );
        FrontierShiftsSnapshot frontier = new FrontierShiftsSnapshot(List.of(prevShift), List.of());

        // Turno de borrador lunes que inicia a las 02:00 (solapado con W-1)
        DraftAssignment draftShift = createAssignment(
                null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(2)), mondayStart.plus(Duration.ofHours(10))
        );

        ValidationContext ctx = createContext(policy, null, null, null, null, frontier, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(draftShift));

        assertThat(result.canPublish()).isFalse();
        assertThat(result.blockingErrors()).anyMatch(e -> e.code() == BlockingErrorCode.SHIFT_OVERLAP);
    }

    @Test
    @DisplayName("Descanso dentro de la semana menor a 11 horas (660 min): emite MINIMUM_REST_VIOLATION")
    void descansoInsuficienteEntreTurnos_bloqueaPublicacion() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        // Turno 1: lunes 08:00 a 16:00
        DraftAssignment shift1 = createAssignment(
                null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(8)), mondayStart.plus(Duration.ofHours(16))
        );
        // Turno 2: lunes 22:00 a martes 06:00 (solo 6 horas de descanso entre 16:00 y 22:00)
        DraftAssignment shift2 = createAssignment(
                null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(22)), mondayStart.plus(Duration.ofHours(30))
        );

        ValidationContext ctx = createContext(policy, null, null, null, null, null, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(shift1, shift2));

        assertThat(result.canPublish()).isFalse();
        assertThat(result.blockingErrors()).anyMatch(e -> e.code() == BlockingErrorCode.MINIMUM_REST_VIOLATION);
    }

    @Test
    @DisplayName("Descanso insuficiente contra turno publicado en W+1: emite MINIMUM_REST_VIOLATION")
    void descansoInsuficienteContraFronteraWPlus1_bloqueaPublicacion() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();
        Instant sundayEndOfWeek = mondayStart.plus(Duration.ofDays(6)).plus(Duration.ofHours(22)); // Domingo 22:00

        // Turno que cruza medianoche de domingo a lunes de W+1, termina a las 06:00 del lunes
        DraftAssignment sundayNightShift = createAssignment(
                null, null, null, employeeId, positionId,
                sundayEndOfWeek, sundayEndOfWeek.plus(Duration.ofHours(8)) // termina lunes W+1 a las 06:00
        );

        // Turno publicado de lunes en W+1 a las 10:00 (solo 4 horas de descanso)
        Instant mondayWPlus1 = mondayStart.plus(Duration.ofDays(7)).plus(Duration.ofHours(10));
        PublishedShiftRecord nextShift = new PublishedShiftRecord(
                UUID.randomUUID(), UUID.randomUUID(), employeeId, positionId,
                mondayWPlus1, mondayWPlus1.plus(Duration.ofHours(8)),
                "Juan Pérez", "Cajero", UUID.randomUUID(), weekStart.plusWeeks(1)
        );
        FrontierShiftsSnapshot frontier = new FrontierShiftsSnapshot(List.of(), List.of(nextShift));

        ValidationContext ctx = createContext(policy, null, null, null, null, frontier, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(sundayNightShift));

        assertThat(result.canPublish()).isFalse();
        assertThat(result.blockingErrors()).anyMatch(e -> e.code() == BlockingErrorCode.MINIMUM_REST_VIOLATION);
    }

    @Test
    @DisplayName("Exceso de horas semanales (> 40h / 2400 min): genera advertencia con warningFingerprint pero NO bloquea")
    void excesoHorasSemanales_generaAdvertenciaYPermitePublicar() {
        Instant mondayStart = weekStart.atStartOfDay(orgZone).toInstant();

        // 5 turnos de 9 horas (540 min cada uno) = 2700 min (> 2400 min umbral)
        // Descanso entre turnos: 15 horas (> 11h requerido)
        DraftAssignment d1 = createAssignment(null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(8)), mondayStart.plus(Duration.ofHours(17)));
        DraftAssignment d2 = createAssignment(null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(32)), mondayStart.plus(Duration.ofHours(41)));
        DraftAssignment d3 = createAssignment(null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(56)), mondayStart.plus(Duration.ofHours(65)));
        DraftAssignment d4 = createAssignment(null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(80)), mondayStart.plus(Duration.ofHours(89)));
        DraftAssignment d5 = createAssignment(null, null, null, employeeId, positionId,
                mondayStart.plus(Duration.ofHours(104)), mondayStart.plus(Duration.ofHours(113)));

        ValidationContext ctx = createContext(policy, null, null, null, null, null, null);
        ScheduleValidationResult result = ScheduleValidationEngine.validate(ctx, List.of(d1, d2, d3, d4, d5));

        assertThat(result.canPublish()).isTrue();
        assertThat(result.blockingErrors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);

        ValidationWarning warning = result.warnings().get(0);
        assertThat(warning.code()).isEqualTo(WarningCode.WEEKLY_PLANNED_MINUTES_EXCEEDED);
        assertThat(warning.plannedMinutes()).isEqualTo(2700);
        assertThat(warning.thresholdMinutes()).isEqualTo(2400);
        assertThat(warning.excessMinutes()).isEqualTo(300);
        assertThat(warning.warningFingerprint()).hasSize(32);
        assertThat(warning.warningFingerprintHex()).hasSize(64);

        assertThat(result.employeeSummaries()).hasSize(1);
        assertThat(result.employeeSummaries().get(0).exceeded()).isTrue();
    }
}
