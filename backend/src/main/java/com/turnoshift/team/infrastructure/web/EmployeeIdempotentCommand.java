package com.turnoshift.team.infrastructure.web;

import com.turnoshift.security.idempotency.BusinessIdempotentCommand;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando idempotente empresarial para mutaciones sobre colaboradores / empleados.
 * Soporta registro y replay seguro tanto para éxitos (200/201 application/json)
 * como para rechazos deterministas (404/409 application/problem+json).
 */
public class EmployeeIdempotentCommand implements BusinessIdempotentCommand<EmployeeExecutionResult> {

    private final UUID organizationId;
    private final UUID actorUserId;
    private final UUID idempotencyKey;
    private final String method;
    private final String path;
    private final String canonicalPayload;
    private final boolean isCreate;
    private final ObjectMapper objectMapper;

    public EmployeeIdempotentCommand(
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
    public int httpStatus(EmployeeExecutionResult response) {
        if (response instanceof EmployeeExecutionResult.Success) {
            return isCreate ? 201 : 200;
        } else if (response instanceof EmployeeExecutionResult.NotFound
                || response instanceof EmployeeExecutionResult.PositionNotFound) {
            return 404;
        } else if (response instanceof EmployeeExecutionResult.CodeAlreadyExists
                || response instanceof EmployeeExecutionResult.PositionInactive
                || response instanceof EmployeeExecutionResult.NoActiveSite
                || response instanceof EmployeeExecutionResult.LicenseLimitExceeded
                || response instanceof EmployeeExecutionResult.StaleVersion) {
            return 409;
        }
        return 500;
    }

    @Override
    public String outcome(EmployeeExecutionResult response) {
        return (response instanceof EmployeeExecutionResult.Success) ? "SUCCEEDED" : "REJECTED";
    }

    @Override
    public String responseContentType(EmployeeExecutionResult response) {
        return (response instanceof EmployeeExecutionResult.Success) ? "application/json" : "application/problem+json";
    }

    private record PersistedProblemDetail(
            String type,
            String title,
            int status,
            String detail,
            String code,
            List<String> errors
    ) {}

    @Override
    public String sanitizeResponseBody(EmployeeExecutionResult response) {
        if (response instanceof EmployeeExecutionResult.Success(var emp)) {
            try {
                return objectMapper.writeValueAsString(emp);
            } catch (Exception e) {
                throw new RuntimeException("Error sanitizando respuesta de empleado", e);
            }
        }

        PersistedProblemDetail problem;
        if (response instanceof EmployeeExecutionResult.NotFound) {
            problem = new PersistedProblemDetail(
                    "/problems/employee-not-found",
                    "Colaborador no encontrado",
                    404,
                    "El colaborador solicitado no existe o no pertenece a la organización.",
                    "EMPLOYEE_NOT_FOUND",
                    List.of()
            );
        } else if (response instanceof EmployeeExecutionResult.PositionNotFound) {
            problem = new PersistedProblemDetail(
                    "/problems/position-not-found",
                    "Puesto no encontrado",
                    404,
                    "El puesto especificado no existe en la organización.",
                    "POSITION_NOT_FOUND",
                    List.of()
            );
        } else if (response instanceof EmployeeExecutionResult.CodeAlreadyExists) {
            problem = new PersistedProblemDetail(
                    "/problems/employee-code-already-exists",
                    "Código de colaborador duplicado",
                    409,
                    "El código de colaborador ya existe en la organización.",
                    "EMPLOYEE_CODE_ALREADY_EXISTS",
                    List.of()
            );
        } else if (response instanceof EmployeeExecutionResult.PositionInactive) {
            problem = new PersistedProblemDetail(
                    "/problems/position-inactive",
                    "Puesto inactivo",
                    409,
                    "El puesto especificado se encuentra inactivo.",
                    "POSITION_INACTIVE",
                    List.of()
            );
        } else if (response instanceof EmployeeExecutionResult.NoActiveSite) {
            problem = new PersistedProblemDetail(
                    "/problems/no-active-site",
                    "Sin sede activa",
                    409,
                    "La organización no cuenta con una sede activa.",
                    "NO_ACTIVE_SITE",
                    List.of()
            );
        } else if (response instanceof EmployeeExecutionResult.LicenseLimitExceeded) {
            problem = new PersistedProblemDetail(
                    "/problems/employee-license-limit-reached",
                    "Límite de empleados alcanzado",
                    409,
                    "Se ha alcanzado el límite de empleados activos contratados para la organización.",
                    "EMPLOYEE_LICENSE_LIMIT_REACHED",
                    List.of()
            );
        } else if (response instanceof EmployeeExecutionResult.StaleVersion) {
            problem = new PersistedProblemDetail(
                    "/problems/concurrent-modification",
                    "Conflicto de concurrencia",
                    409,
                    "El recurso fue modificado por otra operación concurrente.",
                    "CONCURRENT_MODIFICATION",
                    List.of()
            );
        } else {
            throw new IllegalStateException("Resultado no reconocido: " + response);
        }

        try {
            return objectMapper.writeValueAsString(problem);
        } catch (Exception e) {
            throw new RuntimeException("Error sanitizando rechazo persistido de empleado", e);
        }
    }

    @Override
    public EmployeeExecutionResult deserializeResponseBody(String responseBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            if (rootNode != null && rootNode.isObject()) {
                JsonNode codeNode = rootNode.get("code");
                if (codeNode != null && codeNode.isTextual()) {
                    String code = codeNode.asText();
                    switch (code) {
                        case "EMPLOYEE_NOT_FOUND" -> {
                            return new EmployeeExecutionResult.NotFound();
                        }
                        case "POSITION_NOT_FOUND" -> {
                            return new EmployeeExecutionResult.PositionNotFound();
                        }
                        case "EMPLOYEE_CODE_ALREADY_EXISTS" -> {
                            return new EmployeeExecutionResult.CodeAlreadyExists();
                        }
                        case "POSITION_INACTIVE" -> {
                            return new EmployeeExecutionResult.PositionInactive();
                        }
                        case "NO_ACTIVE_SITE" -> {
                            return new EmployeeExecutionResult.NoActiveSite();
                        }
                        case "EMPLOYEE_LICENSE_LIMIT_REACHED" -> {
                            return new EmployeeExecutionResult.LicenseLimitExceeded();
                        }
                        case "CONCURRENT_MODIFICATION" -> {
                            return new EmployeeExecutionResult.StaleVersion();
                        }
                        default -> {
                            if (rootNode.has("type") || rootNode.has("status") || rootNode.has("title")) {
                                throw new IllegalArgumentException("Código de rechazo de empleado no soportado");
                            }
                        }
                    }
                }

                if (!rootNode.has("type")) {
                    EmployeeResponse emp = objectMapper.treeToValue(rootNode, EmployeeResponse.class);
                    if (emp != null && emp.id() != null && emp.code() != null) {
                        return new EmployeeExecutionResult.Success(emp);
                    }
                }
            }
            throw new IllegalArgumentException("Estructura JSON no reconocida para respuesta de empleado");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error deserializando respuesta persistida de empleado", e);
        }
    }
}
