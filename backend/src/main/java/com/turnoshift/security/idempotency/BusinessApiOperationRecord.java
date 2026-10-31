package com.turnoshift.security.idempotency;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de una operación técnica de idempotencia consultada de base de datos para actores empresariales.
 */
public record BusinessApiOperationRecord(
        UUID id,
        UUID organizationId,
        UUID actorUserId,
        UUID idempotencyKey,
        byte[] requestFingerprint,
        String status,
        String outcome,
        Integer httpStatus,
        String responseContentType,
        String responseBody,
        Instant createdAt,
        Instant completedAt,
        Instant expiresAt
) {}
