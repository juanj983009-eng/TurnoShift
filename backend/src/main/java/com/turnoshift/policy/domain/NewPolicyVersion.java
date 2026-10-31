package com.turnoshift.policy.domain;

import java.util.Objects;
import java.util.UUID;

public record NewPolicyVersion(
        UUID organizationId,
        int versionNumber,
        PolicyThresholds thresholds,
        UUID createdByUserId,
        PolicyReason changeReason
) {
    public NewPolicyVersion {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(thresholds, "thresholds no puede ser nulo");
        Objects.requireNonNull(createdByUserId, "createdByUserId no puede ser nulo");
        Objects.requireNonNull(changeReason, "changeReason no puede ser nulo");
        if (versionNumber <= 0) {
            throw new IllegalArgumentException("versionNumber debe ser estrictamente positivo");
        }
    }
}
