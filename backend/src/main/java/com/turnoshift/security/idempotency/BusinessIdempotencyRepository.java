package com.turnoshift.security.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia y coordinación transaccional para operaciones empresariales de idempotencia.
 */
public interface BusinessIdempotencyRepository {

    /**
     * Adquiere un bloqueo de aplicación con ámbito transaccional en SQL Server.
     *
     * @param resourceName nombre del recurso a bloquear
     * @param timeoutMs    tiempo máximo de espera en milisegundos
     */
    void acquireLock(String resourceName, int timeoutMs);

    /**
     * Busca una operación de idempotencia previa para el actor empresarial y clave indicados.
     *
     * @param organizationId identificador de la organización
     * @param actorUserId    identificador del usuario empresarial
     * @param idempotencyKey clave de idempotencia
     * @return operación si existe, vacía en caso contrario
     */
    Optional<BusinessApiOperationRecord> findOperation(UUID organizationId, UUID actorUserId, UUID idempotencyKey);

    /**
     * Registra una operación terminal completada (COMPLETED con SUCCEEDED o REJECTED).
     */
    void saveCompletedOperation(
            UUID organizationId,
            UUID actorUserId,
            UUID idempotencyKey,
            String method,
            String path,
            byte[] fingerprint,
            String schemaVersion,
            String outcome,
            int httpStatus,
            String responseContentType,
            String responseBody,
            Instant completedAt,
            Instant expiresAt
    );
}
