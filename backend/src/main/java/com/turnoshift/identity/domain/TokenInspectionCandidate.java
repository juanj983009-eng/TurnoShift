package com.turnoshift.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Datos agregados de un token junto al estado vigente de su usuario y organización
 * para la inspección de solo lectura.
 */
public record TokenInspectionCandidate(
        UUID tokenId,
        UUID organizationId,
        UUID userId,
        ResetTokenPurpose purpose,
        Instant expiresAt,
        Instant consumedAt,
        Instant revokedAt,
        boolean isUserActive,
        boolean hasPasswordSet,
        String orgServiceStatus
) {
    public TokenInspectionCandidate {
        Objects.requireNonNull(tokenId, "tokenId no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(userId, "userId no puede ser nulo");
        Objects.requireNonNull(purpose, "purpose no puede ser nulo");
        Objects.requireNonNull(expiresAt, "expiresAt no puede ser nulo");
        Objects.requireNonNull(orgServiceStatus, "orgServiceStatus no puede ser nulo");
    }
}
