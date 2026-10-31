package com.turnoshift.operator.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de una operación técnica de idempotencia consultada de base de datos.
 */
public record ApiOperationRecord(
        UUID id,
        UUID actorOperatorId,
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
