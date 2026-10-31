package com.turnoshift.operator.infrastructure.web;

import com.turnoshift.identity.domain.EmailNormalizer;
import com.turnoshift.identity.infrastructure.web.FieldErrorDto;
import com.turnoshift.operator.application.IncorporateOrganization;
import com.turnoshift.operator.domain.IncorporateOrganizationCommand;
import com.turnoshift.operator.domain.IncorporateOrganizationInputValidator;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.OperatorValidationError;
import com.turnoshift.operator.domain.OrganizationOnboardingResult;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Controlador REST para la incorporación asistida de organizaciones por el operador de plataforma.
 *
 * <p>Expone {@code POST /api/v1/operator/setup/organizations} conforme al contrato OpenAPI 3.1.
 */
@RestController
@RequestMapping("/api/v1/operator/setup")
public class OperatorOrganizationSetupController {

    private final IncorporateOrganization incorporateOrganization;

    public OperatorOrganizationSetupController(IncorporateOrganization incorporateOrganization) {
        this.incorporateOrganization = Objects.requireNonNull(incorporateOrganization, "incorporateOrganization no puede ser nulo");
    }

    @PostMapping(
            value = "/organizations",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE}
    )
    public ResponseEntity<?> incorporateOrganization(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) IncorporateOrganizationRequest body,
            HttpServletRequest request
    ) {
        String orgCode = body != null && body.organization() != null ? body.organization().code() : null;
        String orgDisplayName = body != null && body.organization() != null ? body.organization().displayName() : null;
        String timeZone = body != null && body.organization() != null ? body.organization().timeZone() : null;
        String siteDisplayName = body != null && body.site() != null ? body.site().displayName() : null;
        String adminEmail = body != null && body.firstAdmin() != null ? body.firstAdmin().email() : null;
        String adminDisplayName = body != null && body.firstAdmin() != null ? body.firstAdmin().displayName() : null;

        List<OperatorValidationError> errors = IncorporateOrganizationInputValidator.validate(
                idempotencyKey,
                orgCode,
                orgDisplayName,
                timeZone,
                siteDisplayName,
                adminEmail,
                adminDisplayName
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

        String normalizedCode = orgCode.strip().toUpperCase(Locale.ROOT);
        String normalizedEmail;
        try {
            normalizedEmail = EmailNormalizer.normalizar(adminEmail);
        } catch (Exception e) {
            return validationFailedResponse(List.of(new OperatorValidationError("firstAdmin.email", "INVALID_FORMAT", "El correo electrónico no tiene un formato válido.")));
        }

        IncorporateOrganizationCommand command = new IncorporateOrganizationCommand(
                normalizedCode,
                orgDisplayName.strip(),
                ZoneId.of(timeZone.strip()),
                siteDisplayName.strip(),
                normalizedEmail,
                adminDisplayName.strip()
        );

        byte[] fingerprint = RequestFingerprintCalculator.calculate(
                IncorporateOrganization.METHOD,
                IncorporateOrganization.PATH,
                IncorporateOrganization.SCHEMA_VERSION,
                command
        );

        OrganizationOnboardingResult result = incorporateOrganization.execute(operatorId, key, command, fingerprint);

        return switch (result) {
            case OrganizationOnboardingResult.Success success -> {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Request-ID", UUID.randomUUID().toString());
                if (success.replayed()) {
                    headers.set("Idempotency-Replayed", "true");
                }
                headers.setCacheControl("no-store");
                yield new ResponseEntity<>(success.response(), headers, HttpStatus.CREATED);
            }
            case OrganizationOnboardingResult.CodeConflict conflict -> {
                HttpHeaders headers = new HttpHeaders();
                String reqId = UUID.randomUUID().toString();
                headers.set("X-Request-ID", reqId);
                if (conflict.replayed()) {
                    headers.set("Idempotency-Replayed", "true");
                }
                headers.setCacheControl("no-store");
                Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("type", "/problems/organization-code-conflict");
                problem.put("title", "Código de organización en uso");
                problem.put("status", 409);
                problem.put("detail", conflict.message());
                problem.put("code", "ORGANIZATION_CODE_CONFLICT");
                problem.put("requestId", reqId);
                problem.put("errors", List.of());
                yield new ResponseEntity<>(problem, headers, HttpStatus.CONFLICT);
            }
            case OrganizationOnboardingResult.IdempotencyKeyReused ignored -> {
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
            case OrganizationOnboardingResult.IdempotencyKeyExpired ignored -> {
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
            case OrganizationOnboardingResult.DataAccessError ignored -> serviceUnavailableResponse();
        };
    }

    @ExceptionHandler(OperatorDataAccessException.class)
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
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "/problems/service-unavailable");
        problem.put("title", "Servicio no disponible");
        problem.put("status", 503);
        problem.put("detail", "El servicio no está disponible temporalmente. Intente más tarde.");
        problem.put("code", "SERVICE_UNAVAILABLE");
        problem.put("requestId", UUID.randomUUID().toString());
        problem.put("errors", List.of());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(problem);
    }
}
