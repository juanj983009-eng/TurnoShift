package com.turnoshift.schedule.domain.validation;

import java.util.Objects;
import java.util.UUID;

/**
 * Instantánea inmutable de los parámetros relevantes de la versión de política activa.
 */
public record PolicyVersionSnapshot(
        UUID policyVersionId,
        int versionNumber,
        int minimumRestMinutes,
        int weeklyWarningMinutes
) {
    public PolicyVersionSnapshot {
        Objects.requireNonNull(policyVersionId, "policyVersionId no puede ser nulo");
        if (versionNumber <= 0) {
            throw new IllegalArgumentException("versionNumber debe ser mayor a 0");
        }
        if (minimumRestMinutes <= 0) {
            throw new IllegalArgumentException("minimumRestMinutes debe ser mayor a 0");
        }
        if (weeklyWarningMinutes <= 0) {
            throw new IllegalArgumentException("weeklyWarningMinutes debe ser mayor a 0");
        }
    }
}
