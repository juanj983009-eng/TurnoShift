package com.turnoshift.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para el ciclo de vida de tokens de configuración y restablecimiento.
 */
public interface PasswordResetTokenRepository {

    /**
     * Consulta el estado actual de aptitud de un usuario y su organización para la emisión de tokens.
     */
    Optional<UserSetupEligibility> findEligibility(UUID organizationId, UUID userId);

    /**
     * Consulta el estado actual de aptitud bloqueando la fila de usuario con UPDLOCK, HOLDLOCK
     * para serializar emisiones concurrentes del mismo usuario dentro de una transacción.
     */
    Optional<UserSetupEligibility> findEligibilityForEmission(UUID organizationId, UUID userId);

    /**
     * Revoca todos los tokens no consumidos y no revocados para la combinación (organizationId, userId, purpose).
     */
    void revokeUnconsumedTokens(UUID organizationId, UUID userId, ResetTokenPurpose purpose, Instant revokedAt);

    /**
     * Inserta un nuevo registro de token con su hash SHA-256 (nunca el texto plano).
     */
    void insertToken(
            UUID tokenId,
            UUID organizationId,
            UUID userId,
            byte[] tokenHash,
            ResetTokenPurpose purpose,
            Instant expiresAt,
            Instant createdAt
    );

    /**
     * Consulta el candidato de inspección para un hash de token determinado,
     * uniendo los datos del token con el estado vigente de su usuario y organización.
     */
    Optional<TokenInspectionCandidate> findCandidateForInspection(byte[] tokenHash);

    /**
     * Consume de forma condicional y atómica el token si cumple con:
     * token_hash coincidente, purpose coincidente, consumed_at IS NULL,
     * revoked_at IS NULL y expires_at > consumedAt.
     */
    Optional<ConsumedTokenInfo> consumeToken(byte[] tokenHash, ResetTokenPurpose purpose, Instant consumedAt);

    /**
     * Datos del token consumido exitosamente.
     */
    record ConsumedTokenInfo(UUID organizationId, UUID userId, ResetTokenPurpose purpose) {
        public ConsumedTokenInfo {
            Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
            Objects.requireNonNull(userId, "userId no puede ser nulo");
            Objects.requireNonNull(purpose, "purpose no puede ser nulo");
        }
    }
}
