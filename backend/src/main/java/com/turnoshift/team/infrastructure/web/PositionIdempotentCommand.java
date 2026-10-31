package com.turnoshift.team.infrastructure.web;

import com.turnoshift.security.idempotency.BusinessIdempotentCommand;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.UUID;

/**
 * Comando idempotente empresarial para mutaciones sobre el catálogo de puestos.
 * Soporta registro y replay seguro tanto para éxitos (200/201 application/json)
 * como para rechazos deterministas (404/409 application/problem+json).
 */
public class PositionIdempotentCommand implements BusinessIdempotentCommand<PositionExecutionResult> {

    private final UUID organizationId;
    private final UUID actorUserId;
    private final UUID idempotencyKey;
    private final String method;
    private final String path;
    private final String canonicalPayload;
    private final boolean isCreate;
    private final ObjectMapper objectMapper;

    public PositionIdempotentCommand(
            UUID organizationId,
            UUID actorUserId,
            UUID idempotencyKey,
            String method,
            String path,
            String canonicalPayload,
            boolean isCreate,
            ObjectMapper objectMapper
    ) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey no puede ser nulo");
        this.method = Objects.requireNonNull(method, "method no puede ser nulo");
        this.path = Objects.requireNonNull(path, "path no puede ser nulo");
        this.canonicalPayload = Objects.requireNonNull(canonicalPayload, "canonicalPayload no puede ser nulo");
        this.isCreate = isCreate;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    @Override
    public UUID organizationId() {
        return organizationId;
    }

    @Override
    public UUID actorUserId() {
        return actorUserId;
    }

    @Override
    public UUID idempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String schemaVersion() {
        return "1.0";
    }

    @Override
    public String canonicalPayload() {
        return canonicalPayload;
    }

    @Override
    public int httpStatus(PositionExecutionResult response) {
        if (response instanceof PositionExecutionResult.Success) {
            return isCreate ? 201 : 200;
        } else if (response instanceof PositionExecutionResult.NotFound) {
            return 404;
        } else if (response instanceof PositionExecutionResult.CodeAlreadyExists) {
            return 409;
        } else if (response instanceof PositionExecutionResult.StaleVersion) {
            return 409;
        }
        return 500;
    }

    @Override
    public String outcome(PositionExecutionResult response) {
        return (response instanceof PositionExecutionResult.Success) ? "SUCCEEDED" : "REJECTED";
    }

    @Override
    public String responseContentType(PositionExecutionResult response) {
        return (response instanceof PositionExecutionResult.Success) ? "application/json" : "application/problem+json";
    }

    @Override
    public String sanitizeResponseBody(PositionExecutionResult response) {
        if (response instanceof PositionExecutionResult.Success(var pos)) {
            try {
                return objectMapper.writeValueAsString(pos);
            } catch (Exception e) {
                throw new RuntimeException("Error sanitizando respuesta de puesto", e);
            }
        } else if (response instanceof PositionExecutionResult.CodeAlreadyExists) {
            return """
                    {"type":"/problems/position-code-already-exists","title":"Código de puesto duplicado","status":409,"detail":"El código de puesto ya existe en la organización.","code":"POSITION_CODE_ALREADY_EXISTS","errors":[]}
                    """.strip();
        } else if (response instanceof PositionExecutionResult.NotFound) {
            return """
                    {"type":"/problems/position-not-found","title":"Puesto no encontrado","status":404,"detail":"El puesto solicitado no existe o no pertenece a la organización.","code":"POSITION_NOT_FOUND","errors":[]}
                    """.strip();
        } else if (response instanceof PositionExecutionResult.StaleVersion) {
            return """
                    {"type":"/problems/concurrent-modification","title":"Conflicto de concurrencia","status":409,"detail":"El recurso fue modificado por otra operación concurrente.","code":"CONCURRENT_MODIFICATION","errors":[]}
                    """.strip();
        }
        throw new IllegalStateException("Resultado no reconocido: " + response);
    }

    @Override
    public PositionExecutionResult deserializeResponseBody(String responseBody) {
        if (responseBody.contains("\"code\":\"POSITION_CODE_ALREADY_EXISTS\"")) {
            return new PositionExecutionResult.CodeAlreadyExists();
        } else if (responseBody.contains("\"code\":\"POSITION_NOT_FOUND\"")) {
            return new PositionExecutionResult.NotFound();
        } else if (responseBody.contains("\"code\":\"CONCURRENT_MODIFICATION\"")) {
            return new PositionExecutionResult.StaleVersion();
        } else {
            try {
                PositionResponse pos = objectMapper.readValue(responseBody, PositionResponse.class);
                return new PositionExecutionResult.Success(pos);
            } catch (Exception e) {
                throw new RuntimeException("Error deserializando respuesta persistida de puesto", e);
            }
        }
    }
}
