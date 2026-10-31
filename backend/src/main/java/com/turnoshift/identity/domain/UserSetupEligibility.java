package com.turnoshift.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Estado de aptitud de un usuario y su organización para la emisión de tokens INITIAL_SETUP.
 */
public record UserSetupEligibility(
        UUID organizationId,
        UUID userId,
        String orgServiceStatus,
        boolean isUserActive,
        boolean hasPasswordSet
) {
    public UserSetupEligibility {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(userId, "userId no puede ser nulo");
        Objects.requireNonNull(orgServiceStatus, "orgServiceStatus no puede ser nulo");
    }

    /**
     * Determina si el usuario es apto para emisión de token de primer acceso:
     * Organización ACTIVA, usuario activo y sin contraseña configurada.
     */
    public boolean isEligibleForInitialSetup() {
        return "ACTIVA".equalsIgnoreCase(orgServiceStatus)
                && isUserActive
                && !hasPasswordSet;
    }
}
