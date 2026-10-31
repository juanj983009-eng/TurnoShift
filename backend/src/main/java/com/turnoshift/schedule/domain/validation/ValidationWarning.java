package com.turnoshift.schedule.domain.validation;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Advertencia operativa que señala un umbral excedido pero permite publicar si es reconocida.
 */
public record ValidationWarning(
        WarningCode code,
        UUID employeeId,
        String employeeName,
        int plannedMinutes,
        int thresholdMinutes,
        int excessMinutes,
        byte[] warningFingerprint
) {

    public ValidationWarning {
        Objects.requireNonNull(code, "code no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(employeeName, "employeeName no puede ser nulo");
        Objects.requireNonNull(warningFingerprint, "warningFingerprint no puede ser nulo");
        if (warningFingerprint.length != 32) {
            throw new IllegalArgumentException("warningFingerprint debe tener 32 bytes (SHA-256)");
        }

        warningFingerprint = warningFingerprint.clone();
    }

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
        ValidationWarning that = (ValidationWarning) o;
        return plannedMinutes == that.plannedMinutes &&
                thresholdMinutes == that.thresholdMinutes &&
                excessMinutes == that.excessMinutes &&
                code == that.code &&
                Objects.equals(employeeId, that.employeeId) &&
                Objects.equals(employeeName, that.employeeName) &&
                Arrays.equals(warningFingerprint, that.warningFingerprint);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(code, employeeId, employeeName, plannedMinutes, thresholdMinutes, excessMinutes);
        result = 31 * result + Arrays.hashCode(warningFingerprint);
        return result;
    }
}
