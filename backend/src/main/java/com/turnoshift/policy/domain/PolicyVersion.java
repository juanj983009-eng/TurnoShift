package com.turnoshift.policy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PolicyVersion(
        UUID id,
        UUID organizationId,
        int versionNumber,
        PolicyThresholds thresholds,
        Instant effectiveAt,
        UUID createdByUserId,
        PolicyReason changeReason,
        Instant createdAt
) {
    public PolicyVersion {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(thresholds, "thresholds no puede ser nulo");
        Objects.requireNonNull(effectiveAt, "effectiveAt no puede ser nulo");
        Objects.requireNonNull(createdByUserId, "createdByUserId no puede ser nulo");
        Objects.requireNonNull(changeReason, "changeReason no puede ser nulo");
        Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        if (versionNumber <= 0) {
            throw new IllegalArgumentException("versionNumber debe ser estrictamente positivo");
        }
    }
}
