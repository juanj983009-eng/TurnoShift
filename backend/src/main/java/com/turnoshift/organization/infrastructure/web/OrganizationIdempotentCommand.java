package com.turnoshift.organization.infrastructure.web;

import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.OrganizationView;
import com.turnoshift.security.idempotency.BusinessIdempotentCommand;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.UUID;

/**
 * Comando idempotente empresarial para mutaciones sobre la configuración de organización y sede única.
 * Soporta registro y replay seguro tanto para éxitos (200 application/json)
 * como para rechazos deterministas (404/409 application/problem+json).
 */
public class OrganizationIdempotentCommand implements BusinessIdempotentCommand<OrganizationExecutionResult> {

    public enum TargetType {
        ORGANIZATION,
        SITE
    }

    private final UUID organizationId;
    private final UUID actorUserId;
    private final UUID idempotencyKey;
    private final String method;
    private final String path;
    private final String canonicalPayload;
    private final TargetType targetType;
    private final ObjectMapper objectMapper;

    public OrganizationIdempotentCommand(
            UUID organizationId,
            UUID actorUserId,
            UUID idempotencyKey,
            String method,
            String path,
            String canonicalPayload,
            TargetType targetType,
            ObjectMapper objectMapper
    ) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey no puede ser nulo");
        this.method = Objects.requireNonNull(method, "method no puede ser nulo");
        this.path = Objects.requireNonNull(path, "path no puede ser nulo");
        this.canonicalPayload = Objects.requireNonNull(canonicalPayload, "canonicalPayload no puede ser nulo");
        this.targetType = Objects.requireNonNull(targetType, "targetType no puede ser nulo");
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
    public int httpStatus(OrganizationExecutionResult response) {
        if (response instanceof OrganizationExecutionResult.OrganizationSuccess ||
            response instanceof OrganizationExecutionResult.SiteSuccess) {
            return 200;
        } else if (response instanceof OrganizationExecutionResult.ResourceNotFound) {
            return 404;
        } else if (response instanceof OrganizationExecutionResult.ConcurrentModification) {
            return 409;
        }
        return 500;
    }

    @Override
    public String outcome(OrganizationExecutionResult response) {
        return (response instanceof OrganizationExecutionResult.OrganizationSuccess ||
                response instanceof OrganizationExecutionResult.SiteSuccess) ? "SUCCEEDED" : "REJECTED";
    }

    @Override
    public String responseContentType(OrganizationExecutionResult response) {
        return (response instanceof OrganizationExecutionResult.OrganizationSuccess ||
                response instanceof OrganizationExecutionResult.SiteSuccess) ? "application/json" : "application/problem+json";
    }

    @Override
    public String sanitizeResponseBody(OrganizationExecutionResult response) {
        if (response instanceof OrganizationExecutionResult.OrganizationSuccess(var org)) {
            try {
                return objectMapper.writeValueAsString(org);
            } catch (Exception e) {
                throw new RuntimeException("Error sanitizando respuesta de organización", e);
            }
        } else if (response instanceof OrganizationExecutionResult.SiteSuccess(var site)) {
            try {
                return objectMapper.writeValueAsString(site);
            } catch (Exception e) {
                throw new RuntimeException("Error sanitizando respuesta de sede", e);
            }
        } else if (response instanceof OrganizationExecutionResult.ResourceNotFound) {
            return """
                    {"type":"/problems/resource-not-found","title":"Recurso no encontrado","status":404,"detail":"El recurso solicitado no existe o no pertenece a la organización.","code":"RESOURCE_NOT_FOUND","errors":[]}
                    """.strip();
        } else if (response instanceof OrganizationExecutionResult.ConcurrentModification) {
            return """
                    {"type":"/problems/concurrent-modification","title":"Conflicto de concurrencia","status":409,"detail":"El recurso fue modificado por otra operación concurrente.","code":"CONCURRENT_MODIFICATION","errors":[]}
                    """.strip();
        }
        throw new IllegalStateException("Resultado no reconocido: " + response);
    }

    @Override
    public OrganizationExecutionResult deserializeResponseBody(String responseBody) {
        if (responseBody.contains("\"code\":\"RESOURCE_NOT_FOUND\"")) {
            return new OrganizationExecutionResult.ResourceNotFound();
        } else if (responseBody.contains("\"code\":\"CONCURRENT_MODIFICATION\"")) {
            return new OrganizationExecutionResult.ConcurrentModification();
        } else if (targetType == TargetType.ORGANIZATION) {
            try {
                OrganizationView org = objectMapper.readValue(responseBody, OrganizationView.class);
                return new OrganizationExecutionResult.OrganizationSuccess(org);
            } catch (Exception e) {
                throw new RuntimeException("Error deserializando respuesta de organización", e);
            }
        } else if (targetType == TargetType.SITE) {
            try {
                ActiveSiteView site = objectMapper.readValue(responseBody, ActiveSiteView.class);
                return new OrganizationExecutionResult.SiteSuccess(site);
            } catch (Exception e) {
                throw new RuntimeException("Error deserializando respuesta de sede", e);
            }
        }
        throw new IllegalStateException("Cuerpo de respuesta no compatible: " + responseBody);
    }
}
