package com.turnoshift.schedule.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Evidencia estructurada e inmutable del reconocimiento individual de una advertencia operativa.
 */
public record PlanningWarningAcknowledgement(
        UUID id,
        UUID organizationId,
        UUID changeSetId,
        UUID employeeId,
        String warningCode,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        ZoneId timezoneIdSnapshot,
        int plannedMinutes,
        int thresholdMinutes,
        byte[] warningFingerprint,
        int contextSchemaVersion,
        String contextData,
        Instant createdAt
) {

    public PlanningWarningAcknowledgement {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(changeSetId, "changeSetId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(warningCode, "warningCode no puede ser nulo");
        Objects.requireNonNull(periodStart, "periodStart no puede ser nulo");
        Objects.requireNonNull(periodEndExclusive, "periodEndExclusive no puede ser nulo");
        Objects.requireNonNull(timezoneIdSnapshot, "timezoneIdSnapshot no puede ser nulo");
        Objects.requireNonNull(warningFingerprint, "warningFingerprint no puede ser nulo");

        if (warningFingerprint.length != 32) {
            throw new ScheduleDomainException("warningFingerprint debe tener exactamente 32 bytes (SHA-256)");
        }
        if (thresholdMinutes <= 0) {
            throw new ScheduleDomainException("thresholdMinutes debe ser estrictamente positivo");
        }
        if (plannedMinutes <= thresholdMinutes) {
            throw new ScheduleDomainException("plannedMinutes debe superar a thresholdMinutes para requerir reconocimiento");
        }
        if (!periodEndExclusive.equals(periodStart.plusDays(7))) {
            throw new ScheduleDomainException("El periodo de advertencia debe ser de exactamente 7 días");
        }

        // Copia defensiva en constructor
        warningFingerprint = warningFingerprint.clone();
    }

    // Copia defensiva en accessor
    @Override
    public byte[] warningFingerprint() {
        return warningFingerprint.clone();
    }

    public String warningFingerprintHex() {
        return HexFormat.of().formatHex(warningFingerprint);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanningWarningAcknowledgement that = (PlanningWarningAcknowledgement) o;
        return plannedMinutes == that.plannedMinutes &&
                thresholdMinutes == that.thresholdMinutes &&
                contextSchemaVersion == that.contextSchemaVersion &&
                Objects.equals(id, that.id) &&
                Objects.equals(organizationId, that.organizationId) &&
                Objects.equals(changeSetId, that.changeSetId) &&
                Objects.equals(employeeId, that.employeeId) &&
                Objects.equals(warningCode, that.warningCode) &&
                Objects.equals(periodStart, that.periodStart) &&
                Objects.equals(periodEndExclusive, that.periodEndExclusive) &&
                Objects.equals(timezoneIdSnapshot, that.timezoneIdSnapshot) &&
                Arrays.equals(warningFingerprint, that.warningFingerprint) &&
                Objects.equals(contextData, that.contextData) &&
                Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, organizationId, changeSetId, employeeId, warningCode,
                periodStart, periodEndExclusive, timezoneIdSnapshot, plannedMinutes,
                thresholdMinutes, contextSchemaVersion, contextData, createdAt);
        result = 31 * result + Arrays.hashCode(warningFingerprint);
        return result;
    }
}
