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
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit: ValidationFingerprintCalculator — cálculo determinista y canónico de huella SHA-256")
class ValidationFingerprintCalculatorTest {

    private UUID orgId;
    private UUID scheduleId;
    private UUID policyVersionId;
    private long draftLockVersion;
    private UUID emp1;
    private UUID emp2;
    private UUID pos1;
    private Instant t1;
    private Instant t2;
    private Instant t3;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        scheduleId = UUID.randomUUID();
        policyVersionId = UUID.randomUUID();
        draftLockVersion = 1L;
        emp1 = UUID.randomUUID();
        emp2 = UUID.randomUUID();
        pos1 = UUID.randomUUID();
        t1 = Instant.parse("2026-11-02T08:00:00Z");
        t2 = Instant.parse("2026-11-02T16:00:00Z");
        t3 = Instant.parse("2026-11-03T08:00:00Z");
    }

    private DraftAssignment createAssignment(UUID employeeId, Instant startsAt, Instant endsAt) {
        return new DraftAssignment(
                UUID.randomUUID(),
                orgId,
                scheduleId,
                UUID.randomUUID(),
                null,
                null,
                employeeId,
                pos1,
                ShiftInterval.of(startsAt, endsAt),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Determinismo estricto: idénticos datos generan idéntico hash SHA-256")
    void determinismoEstricto() {
        DraftAssignment a1 = createAssignment(emp1, t1, t2);
        DraftAssignment a2 = createAssignment(emp2, t2, t3);

        byte[] fp1 = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, policyVersionId, draftLockVersion, List.of(a1, a2), null, List.of()
        );
        byte[] fp2 = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, policyVersionId, draftLockVersion, List.of(a1, a2), null, List.of()
        );

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).hasSize(32);
        assertThat(HexFormat.of().formatHex(fp1)).isEqualTo(HexFormat.of().formatHex(fp2));
    }

    @Test
    @DisplayName("Invarianza frente al orden de las asignaciones en la lista de entrada")
    void invarianzaFrenteAlOrden() {
        DraftAssignment a1 = createAssignment(emp1, t1, t2);
        DraftAssignment a2 = createAssignment(emp2, t2, t3);

        byte[] fpOrder1 = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, policyVersionId, draftLockVersion, List.of(a1, a2), null, List.of()
        );
        byte[] fpOrder2 = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, policyVersionId, draftLockVersion, List.of(a2, a1), null, List.of()
        );

        assertThat(fpOrder1).isEqualTo(fpOrder2);
    }

    @Test
    @DisplayName("Invarianza frente al orden de turnos de frontera")
    void invarianzaFrenteAlOrdenDeFrontera() {
        PublishedShiftRecord fs1 = new PublishedShiftRecord(
                UUID.randomUUID(), UUID.randomUUID(), emp1, pos1,
                t1.minus(Duration.ofDays(1)), t1.minus(Duration.ofHours(16)),
                "Emp1", "Pos1", UUID.randomUUID(), LocalDate.of(2026, 10, 26)
        );
        PublishedShiftRecord fs2 = new PublishedShiftRecord(
                UUID.randomUUID(), UUID.randomUUID(), emp2, pos1,
                t3.plus(Duration.ofDays(7)), t3.plus(Duration.ofDays(7)).plus(Duration.ofHours(8)),
                "Emp2", "Pos1", UUID.randomUUID(), LocalDate.of(2026, 11, 9)
        );

        FrontierShiftsSnapshot f1 = new FrontierShiftsSnapshot(List.of(fs1), List.of(fs2));
        FrontierShiftsSnapshot f2 = new FrontierShiftsSnapshot(List.of(), List.of(fs2, fs1));

        byte[] fp1 = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, policyVersionId, draftLockVersion, List.of(), f1, List.of()
        );
        byte[] fp2 = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, policyVersionId, draftLockVersion, List.of(), f2, List.of()
        );

        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    @DisplayName("Sensibilidad a cambios: alterar un minuto en startsAt cambia la huella")
    void sensibilidadACambiosHorarios() {
        DraftAssignment a1 = createAssignment(emp1, t1, t2);
        DraftAssignment aAltered = createAssignment(emp1, t1.plusSeconds(60), t2);

        byte[] original = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, policyVersionId, draftLockVersion, List.of(a1), null, List.of()
        );
        byte[] altered = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, policyVersionId, draftLockVersion, List.of(aAltered), null, List.of()
        );

        assertThat(original).isNotEqualTo(altered);
    }

    @Test
    @DisplayName("Sensibilidad a cambios: alterar lockVersion del borrador cambia la huella")
    void sensibilidadALockVersion() {
        DraftAssignment a1 = createAssignment(emp1, t1, t2);

        byte[] v1 = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, policyVersionId, 1L, List.of(a1), null, List.of()
        );
        byte[] v2 = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, policyVersionId, 2L, List.of(a1), null, List.of()
        );

        assertThat(v1).isNotEqualTo(v2);
    }

    @Test
    @DisplayName("Sensibilidad a cambios: alterar la versión de política cambia la huella")
    void sensibilidadAPolitica() {
        DraftAssignment a1 = createAssignment(emp1, t1, t2);

        byte[] p1 = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, policyVersionId, draftLockVersion, List.of(a1), null, List.of()
        );
        byte[] p2 = ValidationFingerprintCalculator.calculate(
                orgId, scheduleId, UUID.randomUUID(), draftLockVersion, List.of(a1), null, List.of()
        );

        assertThat(p1).isNotEqualTo(p2);
    }
}
