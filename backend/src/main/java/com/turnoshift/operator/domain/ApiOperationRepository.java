package com.turnoshift.operator.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia y coordinación transaccional para operaciones de idempotencia.
 */
public interface ApiOperationRepository {

    /**
     * Adquiere un bloqueo de aplicación con ámbito transaccional en SQL Server.
     *
     * @param resourceName nombre del recurso a bloquear
     * @param timeoutMs    tiempo máximo de espera en milisegundos
     */
    void acquireLock(String resourceName, int timeoutMs);

    /**
     * Busca una operación de idempotencia previa para el operador y clave indicados.
     *
     * @param actorOperatorId identificador del operador
     * @param idempotencyKey  clave de idempotencia
     * @return operación si existe, vacía si no existe
     */
    Optional<ApiOperationRecord> findOperation(UUID actorOperatorId, UUID idempotencyKey);

    /**
     * Registra una operación terminal completada (COMPLETED con SUCCEEDED o REJECTED).
     */
    void saveCompletedOperation(
            UUID actorOperatorId,
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
