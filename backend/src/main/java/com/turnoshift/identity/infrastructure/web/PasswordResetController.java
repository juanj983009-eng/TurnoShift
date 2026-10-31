package com.turnoshift.identity.infrastructure.web;

import com.turnoshift.identity.application.CompleteInitialPasswordSetup;
import com.turnoshift.identity.application.InspectResetToken;
import com.turnoshift.identity.domain.PasswordBlocklistUnavailableException;
import com.turnoshift.identity.domain.PasswordSetupDataAccessException;
import com.turnoshift.identity.domain.PasswordSetupResult;
import com.turnoshift.identity.domain.TokenDataAccessException;
import com.turnoshift.identity.domain.TokenInspectionResult;
import com.turnoshift.identity.domain.ViolacionPolitica;
import com.turnoshift.identity.infrastructure.security.PasswordResetRateLimiter;
import com.turnoshift.security.SecurityProblemHandlers.ProblemDetailDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Controlador HTTP para inspección y completado de configuración de contraseña inicial.
 *
 * <p>Expone:
 * <ul>
 *   <li>{@code POST /api/v1/auth/password-reset/inspect} — inspección no destructiva de enlace.</li>
 *   <li>{@code POST /api/v1/auth/password-reset/complete} — establecimiento de contraseña y consumo de enlace.</li>
 * </ul>
 *
 * <p>Ambos endpoints:
 * <ul>
 *   <li>Requieren token CSRF válido (cabecera {@code X-CSRF-TOKEN}).</li>
 *   <li>Aplican rate limiting en memoria por IP antes de invocar lógica de dominio/criptográfica.</li>
 *   <li>Validan Content-Type compatible con {@code application/json} y cuerpo no vacío.</li>
 *   <li>Emiten respuestas Problem Details (RFC 9457) ante fallos y cabecera {@code Cache-Control: no-store}.</li>
 *   <li>No crean sesión ni alteran el contexto de autenticación en caso de éxito o error.</li>
 * </ul>
 */
@RestController
public class PasswordResetController {

    private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
            MediaType.parseMediaType("application/problem+json;charset=UTF-8");

    private final InspectResetToken inspectResetToken;
    private final CompleteInitialPasswordSetup completeInitialPasswordSetup;
    private final PasswordResetRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Autowired
    public PasswordResetController(
            InspectResetToken inspectResetToken,
            CompleteInitialPasswordSetup completeInitialPasswordSetup,
            PasswordResetRateLimiter rateLimiter,
            ObjectMapper objectMapper
    ) {
        this.inspectResetToken = Objects.requireNonNull(inspectResetToken, "inspectResetToken no puede ser nulo");
        this.completeInitialPasswordSetup = Objects.requireNonNull(completeInitialPasswordSetup, "completeInitialPasswordSetup no puede ser nulo");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    public PasswordResetController(
            InspectResetToken inspectResetToken,
            CompleteInitialPasswordSetup completeInitialPasswordSetup,
            PasswordResetRateLimiter rateLimiter
    ) {
        this(inspectResetToken, completeInitialPasswordSetup, rateLimiter, new tools.jackson.databind.json.JsonMapper());
    }

    @PostMapping("/api/v1/auth/password-reset/inspect")
    public ResponseEntity<?> inspect(
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);

        // 1. Rate limiting por IP
        PasswordResetRateLimiter.RateLimitResult rateResult = rateLimiter.tryAcquireInspect(clientIp);
        if (rateResult instanceof PasswordResetRateLimiter.RateLimitResult.Rejected rejected) {
            return rateLimitedResponse(httpRequest, rejected.retryAfterSeconds());
        }

        // 2. Validación de Content-Type compatible con application/json
        if (!isJsonCompatible(httpRequest)) {
            return validationFailedResponse(httpRequest, List.of());
        }

        // 3. Validación de cuerpo vacío o en blanco
        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(
                    httpRequest,
                    List.of(new FieldErrorDto("token", "REQUIRED", "El token es obligatorio."))
            );
        }

        // 4. Parseo seguro de JSON sin eco de trazas
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(rawBody);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, List.of());
        }

        if (rootNode == null || !rootNode.isObject()) {
            return validationFailedResponse(httpRequest, List.of());
        }

        // 5. Validación de tipos y presencia de campos
        JsonNode tokenNode = rootNode.get("token");
        if (tokenNode == null || tokenNode.isNull()) {
            return validationFailedResponse(
                    httpRequest,
                    List.of(new FieldErrorDto("token", "REQUIRED", "El token es obligatorio."))
            );
        }
        if (!tokenNode.isTextual()) {
            return validationFailedResponse(
                    httpRequest,
                    List.of(new FieldErrorDto("token", "INVALID_FORMAT", "El token debe ser una cadena de texto."))
            );
        }
        String token = tokenNode.asText();
        if (token.isBlank()) {
            return validationFailedResponse(
                    httpRequest,
                    List.of(new FieldErrorDto("token", "REQUIRED", "El token es obligatorio."))
            );
        }

        // 6. Inspección del enlace
        try {
            TokenInspectionResult inspectionResult = inspectResetToken.inspect(token);
            if (inspectionResult instanceof TokenInspectionResult.Valid valid) {
                return ResponseEntity.ok()
                        .header("Cache-Control", "no-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new InspectLinkResponse(true, valid.purpose().name()));
            }

            return resetLinkInvalidResponse(httpRequest);
        } catch (TokenDataAccessException e) {
            return serviceUnavailableResponse(httpRequest);
        }
    }

    @PostMapping("/api/v1/auth/password-reset/complete")
    public ResponseEntity<?> complete(
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);

        // 1. Rate limiting por IP
        PasswordResetRateLimiter.RateLimitResult rateResult = rateLimiter.tryAcquireComplete(clientIp);
        if (rateResult instanceof PasswordResetRateLimiter.RateLimitResult.Rejected rejected) {
            return rateLimitedResponse(httpRequest, rejected.retryAfterSeconds());
        }

        // 2. Validación de Content-Type compatible con application/json
        if (!isJsonCompatible(httpRequest)) {
            return validationFailedResponse(httpRequest, List.of());
        }

        // 3. Validación de cuerpo vacío o en blanco
        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, List.of(
                    new FieldErrorDto("token", "REQUIRED", "El token es obligatorio."),
                    new FieldErrorDto("newPassword", "REQUIRED", "La contraseña es obligatoria.")
            ));
        }

        // 4. Parseo seguro de JSON sin eco de trazas
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(rawBody);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, List.of());
        }

        if (rootNode == null || !rootNode.isObject()) {
            return validationFailedResponse(httpRequest, List.of());
        }

        // 5. Validación estructural y tipado de campos
        List<FieldErrorDto> validationErrors = new ArrayList<>();
        String token = null;
        String newPassword = null;

        JsonNode tokenNode = rootNode.get("token");
        if (tokenNode == null || tokenNode.isNull()) {
            validationErrors.add(new FieldErrorDto("token", "REQUIRED", "El token es obligatorio."));
        } else if (!tokenNode.isTextual()) {
            validationErrors.add(new FieldErrorDto("token", "INVALID_FORMAT", "El token debe ser una cadena de texto."));
        } else if (tokenNode.asText().isBlank()) {
            validationErrors.add(new FieldErrorDto("token", "REQUIRED", "El token es obligatorio."));
        } else {
            token = tokenNode.asText();
        }

        JsonNode passwordNode = rootNode.get("newPassword");
        if (passwordNode == null || passwordNode.isNull()) {
            validationErrors.add(new FieldErrorDto("newPassword", "REQUIRED", "La contraseña es obligatoria."));
        } else if (!passwordNode.isTextual()) {
            validationErrors.add(new FieldErrorDto("newPassword", "INVALID_FORMAT", "La contraseña debe ser una cadena de texto."));
        } else if (passwordNode.asText().isBlank()) {
            validationErrors.add(new FieldErrorDto("newPassword", "REQUIRED", "La contraseña es obligatoria."));
        } else {
            newPassword = passwordNode.asText();
        }

        if (!validationErrors.isEmpty()) {
            return validationFailedResponse(httpRequest, validationErrors);
        }

        // 6. Pre-inspección rápida: descartar tokens no utilizables antes de HIBP y cómputo Argon2id
        try {
            TokenInspectionResult inspection = inspectResetToken.inspect(token);
            if (inspection instanceof TokenInspectionResult.Invalid) {
                return resetLinkInvalidResponse(httpRequest);
            }
        } catch (TokenDataAccessException e) {
            return serviceUnavailableResponse(httpRequest);
        }

        // 7. Completado de configuración inicial
        try {
            PasswordSetupResult setupResult = completeInitialPasswordSetup.complete(token, newPassword);

            if (setupResult instanceof PasswordSetupResult.Success) {
                rateLimiter.resetComplete(clientIp);
                return ResponseEntity.noContent()
                        .header("Cache-Control", "no-store")
                        .build();
            }

            if (setupResult instanceof PasswordSetupResult.InvalidToken) {
                return resetLinkInvalidResponse(httpRequest);
            }

            if (setupResult instanceof PasswordSetupResult.PolicyViolation policyViolation) {
                List<FieldErrorDto> policyErrors = policyViolation.violations().stream()
                        .map(this::mapPolicyViolation)
                        .toList();
                return validationFailedResponse(httpRequest, policyErrors);
            }

            return resetLinkInvalidResponse(httpRequest);
        } catch (PasswordBlocklistUnavailableException | PasswordSetupDataAccessException | TokenDataAccessException e) {
            return serviceUnavailableResponse(httpRequest);
        }
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class
    })
    public ResponseEntity<?> handleHttpParsingExceptions(HttpServletRequest request) {
        return validationFailedResponse(request, List.of());
    }

    private boolean isJsonCompatible(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.isCompatibleWith(mediaType);
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }

    private FieldErrorDto mapPolicyViolation(ViolacionPolitica v) {
        String message = switch (v) {
            case CONTRASENA_VACIA -> "La contraseña no puede estar vacía.";
            case DEMASIADO_CORTA -> "La contraseña debe tener al menos 15 caracteres.";
            case DEMASIADO_LARGA -> "La contraseña no puede superar los 128 caracteres.";
            case CONTRASENA_BLOQUEADA -> "La contraseña elegida ha sido comprometida en filtraciones públicas o es insegura.";
        };
        return new FieldErrorDto("newPassword", v.getCodigo(), message);
    }

    private ResponseEntity<?> rateLimitedResponse(HttpServletRequest request, long retryAfterSeconds) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .header("Cache-Control", "no-store")
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(new ProblemDetailDto(
                        "/problems/rate-limited",
                        "Límite de solicitudes alcanzado",
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Demasiados intentos. Espera antes de volver a intentarlo.",
                        request.getRequestURI(),
                        "RATE_LIMITED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private ResponseEntity<?> validationFailedResponse(HttpServletRequest request, List<FieldErrorDto> errors) {
        List<Object> errorDtos = errors.stream().map(e -> (Object) e).toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Cache-Control", "no-store")
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(new ProblemDetailDto(
                        "/problems/validation-failed",
                        "Error de validación",
                        HttpStatus.BAD_REQUEST.value(),
                        "La petición contiene campos inválidos.",
                        request.getRequestURI(),
                        "VALIDATION_FAILED",
                        UUID.randomUUID().toString(),
                        errorDtos
                ));
    }

    private ResponseEntity<?> resetLinkInvalidResponse(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .header("Cache-Control", "no-store")
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(new ProblemDetailDto(
                        "/problems/reset-link-invalid",
                        "Enlace no utilizable",
                        HttpStatus.UNPROCESSABLE_ENTITY.value(),
                        "El enlace ha vencido o ya fue utilizado.",
                        request.getRequestURI(),
                        "RESET_LINK_INVALID",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private ResponseEntity<?> serviceUnavailableResponse(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Cache-Control", "no-store")
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(new ProblemDetailDto(
                        "/problems/service-unavailable",
                        "Servicio no disponible",
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "El servicio no está disponible temporalmente. Inténtalo más tarde.",
                        request.getRequestURI(),
                        "SERVICE_UNAVAILABLE",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "UNKNOWN";
    }
}
