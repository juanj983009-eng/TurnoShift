package com.turnoshift.operator.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Resultado sellado del caso de uso de emisión de enlace inicial de contraseña.
 */
public sealed interface EmitInitialAccessLinkResult {

    /**
     * Emisión exitosa de nuevo enlace inicial.
     */
    record Success(
            String setupLink,
            Instant expiresAt
    ) implements EmitInitialAccessLinkResult {}

    /**
     * Intento de repetición con la misma clave de idempotencia tras una emisión previa.
     * Por motivos de seguridad el secreto no se almacena ni reproduce, y no se genera enlace duplicado.
     */
    record LinkAlreadyEmitted(
            String message
    ) implements EmitInitialAccessLinkResult {}

    /**
     * Clave de idempotencia reutilizada con distinta huella de comando.
     */
    record IdempotencyKeyReused() implements EmitInitialAccessLinkResult {}

    /**
     * Clave de idempotencia expirada (>24h).
     */
    record IdempotencyKeyExpired() implements EmitInitialAccessLinkResult {}

    /**
     * La organización especificada no existe (404).
     */
    record OrganizationNotFound(UUID organizationId) implements EmitInitialAccessLinkResult {}

    /**
     * El usuario administrador especificado no existe en la organización (404).
     */
    record AdminNotFound(UUID organizationId, UUID adminUserId) implements EmitInitialAccessLinkResult {}

    /**
     * Transición de estado inválida: admin inactivo, org inactiva, rol incorrecto o contraseña ya establecida (409).
     */
    record InvalidStateTransition(String message, boolean replayed) implements EmitInitialAccessLinkResult {}

    /**
     * Conflicto de versión optimista (409 CONCURRENT_MODIFICATION).
     */
    record VersionConflict(long expectedVersion, long currentVersion, String message, boolean replayed) implements EmitInitialAccessLinkResult {}

    /**
     * Replay de un rechazo determinista previamente registrado.
     */
    record ReplayedRejection(int httpStatus, String type, String title, String code, String detail) implements EmitInitialAccessLinkResult {}

    /**
     * Error técnico no controlado de acceso a datos (503).
     */
    record DataAccessError() implements EmitInitialAccessLinkResult {}
}
