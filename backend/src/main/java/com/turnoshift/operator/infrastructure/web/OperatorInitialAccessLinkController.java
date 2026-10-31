package com.turnoshift.operator.infrastructure.web;

import com.turnoshift.identity.domain.TokenDataAccessException;
import com.turnoshift.identity.infrastructure.web.FieldErrorDto;
import com.turnoshift.operator.application.EmitInitialAccessLink;
import com.turnoshift.operator.domain.EmitInitialAccessLinkCommand;
import com.turnoshift.operator.domain.EmitInitialAccessLinkInputValidator;
import com.turnoshift.operator.domain.EmitInitialAccessLinkResult;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.OperatorValidationError;
import com.turnoshift.operator.domain.PlatformOperatorIdentity;
import com.turnoshift.operator.domain.RequestFingerprintCalculator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Controlador REST para la emisión del enlace de configuración inicial del primer administrador.
 *
 * <p>Expone {@code POST /api/v1/operator/setup/organizations/{organizationId}/initial-access-link}
 * conforme al contrato OpenAPI 3.1.
 */
@RestController
@RequestMapping("/api/v1/operator/setup")
public class OperatorInitialAccessLinkController {

    private final EmitInitialAccessLink emitInitialAccessLink;

    public OperatorInitialAccessLinkController(EmitInitialAccessLink emitInitialAccessLink) {
        this.emitInitialAccessLink = Objects.requireNonNull(emitInitialAccessLink, "emitInitialAccessLink no puede ser nulo");
    }

    @PostMapping(
            value = "/organizations/{organizationId}/initial-access-link",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE}
    )
    public ResponseEntity<?> emitInitialAccessLink(
            @PathVariable("organizationId") String organizationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) EmitInitialLinkRequest body,
            HttpServletRequest request
    ) {
        String adminUserIdStr = body != null && body.adminUserId() != null ? body.adminUserId().toString() : null;
        Long expectedVersion = body != null ? body.expectedVersion() : null;

        List<OperatorValidationError> errors = EmitInitialAccessLinkInputValidator.validate(
                idempotencyKey,
                organizationId,
                adminUserIdStr,
                expectedVersion
        );

        if (!errors.isEmpty()) {
            return validationFailedResponse(errors);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof PlatformOperatorIdentity identity)) {
            return sessionRequiredResponse();
        }

        UUID operatorId = identity.id();
        UUID key = UUID.fromString(idempotencyKey.trim());
        UUID orgId = UUID.fromString(organizationId.trim());
        UUID adminUserId = UUID.fromString(adminUserIdStr.trim());

        EmitInitialAccessLinkCommand command = new EmitInitialAccessLinkCommand(
                orgId,
                adminUserId,
                expectedVersion
        );

        String canonicalPath = String.format(EmitInitialAccessLink.PATH_TEMPLATE, orgId);

        byte[] fingerprint = RequestFingerprintCalculator.calculate(
                EmitInitialAccessLink.METHOD,
                canonicalPath,
                EmitInitialAccessLink.SCHEMA_VERSION,
                command
        );

        EmitInitialAccessLinkResult result = emitInitialAccessLink.execute(operatorId, key, command, fingerprint);

        return switch (result) {
            case EmitInitialAccessLinkResult.Success success -> {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Request-ID", UUID.randomUUID().toString());
                headers.setCacheControl("no-store");
                EmitInitialLinkResponse response = new EmitInitialLinkResponse(
                        success.setupLink(),
                        DateTimeFormatter.ISO_INSTANT.format(success.expiresAt())
                );
                yield new ResponseEntity<>(response, headers, HttpStatus.OK);
            }
            case EmitInitialAccessLinkResult.LinkAlreadyEmitted emitted -> {
                HttpHeaders headers = new HttpHeaders();
                String reqId = UUID.randomUUID().toString();
                headers.set("X-Request-ID", reqId);
                headers.set("Idempotency-Replayed", "true");
                headers.setCacheControl("no-store");
                Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("type", "/problems/initial-link-already-emitted");
                problem.put("title", "Enlace inicial ya emitido para esta clave de idempotencia");
                problem.put("status", 409);
                problem.put("detail", emitted.message());
                problem.put("code", "INITIAL_LINK_ALREADY_EMITTED");
                problem.put("requestId", reqId);
                problem.put("errors", List.of());
                yield new ResponseEntity<>(problem, headers, HttpStatus.CONFLICT);
            }
            case EmitInitialAccessLinkResult.IdempotencyKeyReused ignored -> {
                HttpHeaders headers = new HttpHeaders();
                String reqId = UUID.randomUUID().toString();
                headers.set("X-Request-ID", reqId);
                headers.setCacheControl("no-store");
                Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("type", "/problems/idempotency-key-reused");
                problem.put("title", "Clave de idempotencia reutilizada");
                problem.put("status", 409);
                problem.put("detail", "La clave de idempotencia fue usada previamente con un contenido diferente.");
                problem.put("code", "IDEMPOTENCY_KEY_REUSED");
                problem.put("requestId", reqId);
                problem.put("errors", List.of());
                yield new ResponseEntity<>(problem, headers, HttpStatus.CONFLICT);
            }
            case EmitInitialAccessLinkResult.IdempotencyKeyExpired ignored -> {
                HttpHeaders headers = new HttpHeaders();
                String reqId = UUID.randomUUID().toString();
                headers.set("X-Request-ID", reqId);
                headers.setCacheControl("no-store");
                Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("type", "/problems/idempotency-key-expired");
                problem.put("title", "Clave de idempotencia expirada");
                problem.put("status", 409);
                problem.put("detail", "La clave de idempotencia ha superado el periodo de vigencia permitido (24 horas).");
                problem.put("code", "IDEMPOTENCY_KEY_EXPIRED");
                problem.put("requestId", reqId);
                problem.put("errors", List.of());
                yield new ResponseEntity<>(problem, headers, HttpStatus.CONFLICT);
            }
            case EmitInitialAccessLinkResult.OrganizationNotFound ignored -> {
                HttpHeaders headers = new HttpHeaders();
                String reqId = UUID.randomUUID().toString();
                headers.set("X-Request-ID", reqId);
                headers.setCacheControl("no-store");
                Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("type", "/problems/resource-not-found");
                problem.put("title", "Recurso no encontrado");
                problem.put("status", 404);
                problem.put("detail", "La organización solicitada no existe.");
                problem.put("code", "RESOURCE_NOT_FOUND");
                problem.put("requestId", reqId);
                problem.put("errors", List.of());
                yield new ResponseEntity<>(problem, headers, HttpStatus.NOT_FOUND);
            }
            case EmitInitialAccessLinkResult.AdminNotFound ignored -> {
                HttpHeaders headers = new HttpHeaders();
                String reqId = UUID.randomUUID().toString();
                headers.set("X-Request-ID", reqId);
                headers.setCacheControl("no-store");
                Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("type", "/problems/resource-not-found");
                problem.put("title", "Recurso no encontrado");
                problem.put("status", 404);
                problem.put("detail", "El usuario administrador solicitado no existe en la organización especificada.");
                problem.put("code", "RESOURCE_NOT_FOUND");
                problem.put("requestId", reqId);
                problem.put("errors", List.of());
                yield new ResponseEntity<>(problem, headers, HttpStatus.NOT_FOUND);
            }
            case EmitInitialAccessLinkResult.InvalidStateTransition invalid -> {
                HttpHeaders headers = new HttpHeaders();
                String reqId = UUID.randomUUID().toString();
                headers.set("X-Request-ID", reqId);
                if (invalid.replayed()) {
                    headers.set("Idempotency-Replayed", "true");
                }
                headers.setCacheControl("no-store");
                Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("type", "/problems/invalid-state-transition");
                problem.put("title", "Transición de estado inválida");
                problem.put("status", 409);
                problem.put("detail", invalid.message());
                problem.put("code", "INVALID_STATE_TRANSITION");
                problem.put("requestId", reqId);
                problem.put("errors", List.of());
                yield new ResponseEntity<>(problem, headers, HttpStatus.CONFLICT);
            }
            case EmitInitialAccessLinkResult.VersionConflict conflict -> {
                HttpHeaders headers = new HttpHeaders();
                String reqId = UUID.randomUUID().toString();
                headers.set("X-Request-ID", reqId);
                if (conflict.replayed()) {
                    headers.set("Idempotency-Replayed", "true");
                }
                headers.setCacheControl("no-store");
                Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("type", "/problems/concurrent-modification");
                problem.put("title", "Conflicto de versión del administrador");
                problem.put("status", 409);
                problem.put("detail", conflict.message());
                problem.put("code", "CONCURRENT_MODIFICATION");
                problem.put("requestId", reqId);
                problem.put("errors", List.of());
                yield new ResponseEntity<>(problem, headers, HttpStatus.CONFLICT);
            }
            case EmitInitialAccessLinkResult.ReplayedRejection replayed -> {
                HttpHeaders headers = new HttpHeaders();
                String reqId = UUID.randomUUID().toString();
                headers.set("X-Request-ID", reqId);
                headers.set("Idempotency-Replayed", "true");
                headers.setCacheControl("no-store");
                Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("type", replayed.type());
                problem.put("title", replayed.title());
                problem.put("status", replayed.httpStatus());
                problem.put("detail", replayed.detail());
                problem.put("code", replayed.code());
                problem.put("requestId", reqId);
                problem.put("errors", List.of());
                yield new ResponseEntity<>(problem, headers, HttpStatus.valueOf(replayed.httpStatus()));
            }
            case EmitInitialAccessLinkResult.DataAccessError ignored -> serviceUnavailableResponse();
        };
    }

    @ExceptionHandler({OperatorDataAccessException.class, TokenDataAccessException.class})
    public ResponseEntity<?> handleDataAccessException() {
        return serviceUnavailableResponse();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleNotReadableException() {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "/problems/validation-failed");
        problem.put("title", "Falló la validación");
        problem.put("status", 400);
        problem.put("detail", "El cuerpo de la solicitud no es un JSON válido o tiene un formato incorrecto.");
        problem.put("code", "VALIDATION_FAILED");
        problem.put("requestId", UUID.randomUUID().toString());
        problem.put("errors", List.of());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(problem);
    }

    private ResponseEntity<?> validationFailedResponse(List<OperatorValidationError> errors) {
        List<FieldErrorDto> dtos = errors.stream()
                .map(e -> new FieldErrorDto(e.field(), e.code(), e.message()))
                .toList();

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "/problems/validation-failed");
        problem.put("title", "Falló la validación");
        problem.put("status", 400);
        problem.put("detail", "La solicitud contiene parámetros inválidos o faltantes.");
        problem.put("code", "VALIDATION_FAILED");
        problem.put("requestId", UUID.randomUUID().toString());
        problem.put("errors", dtos);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(problem);
    }

    private ResponseEntity<?> sessionRequiredResponse() {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "/problems/session-required");
        problem.put("title", "Sesión requerida");
        problem.put("status", 401);
        problem.put("detail", "Se requiere una sesión activa de operador para acceder a este recurso.");
        problem.put("code", "SESSION_REQUIRED");
        problem.put("requestId", UUID.randomUUID().toString());
        problem.put("errors", List.of());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(problem);
    }

    private ResponseEntity<?> serviceUnavailableResponse() {
        String reqId = UUID.randomUUID().toString();
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "/problems/service-unavailable");
        problem.put("title", "Servicio no disponible");
        problem.put("status", 503);
        problem.put("detail", "El servicio no está disponible temporalmente. Intente más tarde.");
        problem.put("code", "SERVICE_UNAVAILABLE");
        problem.put("requestId", reqId);
        problem.put("errors", List.of());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Request-ID", reqId)
                .body(problem);
    }
}
