package com.turnoshift.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Datos vigentes recuperados de base de datos para revalidar una sesión activa.
 */
public record SessionRevalidationCandidate(
        UUID userId,
        UUID organizationId,
        boolean isActive,
        long authVersion,
        String roleCode,
        String displayName,
        UUID employeeId,
        String organizationStatus,
        String organizationDisplayName,
        String organizationTimeZone
) {
    public SessionRevalidationCandidate {
        Objects.requireNonNull(userId, "userId no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(roleCode, "roleCode no puede ser nulo");
        Objects.requireNonNull(displayName, "displayName no puede ser nulo");
        Objects.requireNonNull(organizationStatus, "organizationStatus no puede ser nulo");
        Objects.requireNonNull(organizationDisplayName, "organizationDisplayName no puede ser nulo");
        Objects.requireNonNull(organizationTimeZone, "organizationTimeZone no puede ser nulo");
    }
}
