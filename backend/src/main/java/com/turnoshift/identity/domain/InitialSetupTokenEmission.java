package com.turnoshift.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Resultado de la emisión exitosa de un token de primer acceso (INITIAL_SETUP).
 *
 * <p>Contiene el secreto en texto plano (rawToken) únicamente en memoria
 * para su entrega inmediata al flujo de incorporación asistida.
 *
 * <p>Por seguridad estricta, {@link #toString()} redacta completamente el secreto
 * para impedir su exposición en registros de log o trazas de diagnóstico.
 */
public record InitialSetupTokenEmission(
        UUID tokenId,
        UUID organizationId,
        UUID userId,
        String rawToken,
        Instant expiresAt
) {
    public InitialSetupTokenEmission {
        Objects.requireNonNull(tokenId, "tokenId no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(userId, "userId no puede ser nulo");
        Objects.requireNonNull(rawToken, "rawToken no puede ser nulo");
        Objects.requireNonNull(expiresAt, "expiresAt no puede ser nulo");
    }

    @Override
    public String toString() {
        return "InitialSetupTokenEmission["
                + "tokenId=[PROTECTED]"
                + ", organizationId=[PROTECTED]"
                + ", userId=[PROTECTED]"
                + ", rawToken=[PROTECTED]"
                + ", expiresAt=" + expiresAt
                + "]";
    }
}
