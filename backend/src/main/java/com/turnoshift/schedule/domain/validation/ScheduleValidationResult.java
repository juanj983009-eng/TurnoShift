package com.turnoshift.schedule.domain.validation;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resultado completo y determinista de la validación de un borrador de horario semanal.
 */
public record ScheduleValidationResult(
        boolean canPublish,
        boolean emptySchedule,
        UUID policyVersionId,
        List<ValidationBlockingError> blockingErrors,
        List<ValidationWarning> warnings,
        List<EmployeeWeeklySummary> employeeSummaries,
        byte[] validationFingerprint
) {
    public ScheduleValidationResult {
        blockingErrors = blockingErrors == null ? List.of() : List.copyOf(blockingErrors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        employeeSummaries = employeeSummaries == null ? List.of() : List.copyOf(employeeSummaries);
        Objects.requireNonNull(validationFingerprint, "validationFingerprint no puede ser nulo");
        if (validationFingerprint.length != 32) {
            throw new IllegalArgumentException("validationFingerprint debe tener 32 bytes (SHA-256)");
        }
        validationFingerprint = validationFingerprint.clone();
    }

    @Override
    public byte[] validationFingerprint() {
        return validationFingerprint.clone();
    }

    public String validationFingerprintHex() {
        return HexFormat.of().formatHex(validationFingerprint);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduleValidationResult that = (ScheduleValidationResult) o;
        return canPublish == that.canPublish &&
                emptySchedule == that.emptySchedule &&
                Objects.equals(policyVersionId, that.policyVersionId) &&
                Objects.equals(blockingErrors, that.blockingErrors) &&
                Objects.equals(warnings, that.warnings) &&
                Objects.equals(employeeSummaries, that.employeeSummaries) &&
                Arrays.equals(validationFingerprint, that.validationFingerprint);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(canPublish, emptySchedule, policyVersionId, blockingErrors, warnings, employeeSummaries);
        result = 31 * result + Arrays.hashCode(validationFingerprint);
        return result;
    }
}
