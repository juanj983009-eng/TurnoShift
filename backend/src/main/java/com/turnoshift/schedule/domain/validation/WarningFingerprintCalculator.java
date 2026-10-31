package com.turnoshift.schedule.domain.validation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Calculador determinista de la huella criptográfica SHA-256 de una advertencia por exceso de horas semanales.
 */
public final class WarningFingerprintCalculator {

    private WarningFingerprintCalculator() {
    }

    public static byte[] calculate(
            UUID organizationId,
            UUID employeeId,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            int plannedMinutes,
            int thresholdMinutes
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(periodStart, "periodStart no puede ser nulo");
        Objects.requireNonNull(periodEndExclusive, "periodEndExclusive no puede ser nulo");

        String canonical = String.format(
                "w1|%s|%s|%s|%s|%d|%d",
                organizationId,
                employeeId,
                periodStart,
                periodEndExclusive,
                plannedMinutes,
                thresholdMinutes
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM", e);
        }
    }
}
