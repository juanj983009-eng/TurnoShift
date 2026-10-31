package com.turnoshift.operator.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Comando inmutable y normalizado para la emisión de enlace inicial de contraseña.
 */
public record EmitInitialAccessLinkCommand(
        UUID organizationId,
        UUID adminUserId,
        long expectedVersion
) {
    public EmitInitialAccessLinkCommand {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(adminUserId, "adminUserId no puede ser nulo");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion debe ser mayor o igual a 0");
        }
    }
}
