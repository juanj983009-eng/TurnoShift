package com.turnoshift.policy.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.identity.infrastructure.web.FieldErrorDto;
import com.turnoshift.policy.application.ActivatePolicyCommand;
import com.turnoshift.policy.application.ActivatePolicyUseCase;
import com.turnoshift.policy.application.GetCurrentPolicyUseCase;
import com.turnoshift.policy.application.GetPolicyHistoryUseCase;
import com.turnoshift.policy.application.PolicyPreviewResult;
import com.turnoshift.policy.application.PreviewPolicyChangeUseCase;
import com.turnoshift.policy.domain.PolicyConcurrentModificationException;
import com.turnoshift.policy.domain.PolicyDataAccessException;
import com.turnoshift.policy.domain.PolicyOperationBusyException;
import com.turnoshift.policy.domain.PolicyReason;
import com.turnoshift.policy.domain.PolicyThresholds;
import com.turnoshift.policy.domain.PolicyValidationException;
import com.turnoshift.policy.domain.PolicyVersion;
import com.turnoshift.policy.domain.ValidationFingerprint;
import com.turnoshift.security.SecurityProblemHandlers.ProblemDetailDto;
import com.turnoshift.security.idempotency.BusinessIdempotencyResult;
import com.turnoshift.security.idempotency.BusinessIdempotentOperationExecutor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Controlador REST para lectura, previsualización y activación de políticas operativas iniciales (I2.3-c).
 *
 * <p>Aplica aislamiento multi-tenant estricto obteniendo la organización y el actor exclusivamente
 * de la identidad de la sesión autenticada en el SecurityContext.
 */
@RestController
public class PolicyController {

    private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
            MediaType.parseMediaType("application/problem+json;charset=UTF-8");

    private final GetCurrentPolicyUseCase getCurrentPolicyUseCase;
    private final GetPolicyHistoryUseCase getPolicyHistoryUseCase;
    private final PreviewPolicyChangeUseCase previewPolicyChangeUseCase;
    private final ActivatePolicyUseCase activatePolicyUseCase;
    private final BusinessIdempotentOperationExecutor executor;
    private final ObjectMapper objectMapper;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public PolicyController(
            GetCurrentPolicyUseCase getCurrentPolicyUseCase,
            GetPolicyHistoryUseCase getPolicyHistoryUseCase,
            PreviewPolicyChangeUseCase previewPolicyChangeUseCase,
            ActivatePolicyUseCase activatePolicyUseCase,
            BusinessIdempotentOperationExecutor executor,
            ObjectMapper objectMapper
    ) {
        this.getCurrentPolicyUseCase = Objects.requireNonNull(getCurrentPolicyUseCase, "getCurrentPolicyUseCase no puede ser nulo");
        this.getPolicyHistoryUseCase = Objects.requireNonNull(getPolicyHistoryUseCase, "getPolicyHistoryUseCase no puede ser nulo");
        this.previewPolicyChangeUseCase = Objects.requireNonNull(previewPolicyChangeUseCase, "previewPolicyChangeUseCase no puede ser nulo");
        this.activatePolicyUseCase = Objects.requireNonNull(activatePolicyUseCase, "activatePolicyUseCase no puede ser nulo");
        this.executor = Objects.requireNonNull(executor, "executor no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    // =========================================================================
    // 1. GET /api/v1/policies/current
    // =========================================================================

    @GetMapping("/api/v1/policies/current")
    public ResponseEntity<?> getCurrentPolicy(HttpServletRequest httpRequest) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        Optional<PolicyVersion> currentOpt = getCurrentPolicyUseCase.get(identity.organizationId());
        PolicyResponse response = currentOpt.map(PolicyResponse::from).orElse(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CurrentPolicyResponse(response));
    }

    // =========================================================================
    // 2. GET /api/v1/policies/history
    // =========================================================================

    @GetMapping("/api/v1/policies/history")
    public ResponseEntity<?> getPolicyHistory(HttpServletRequest httpRequest) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        List<PolicyVersion> history = getPolicyHistoryUseCase.get(identity.organizationId());
        List<PolicyResponse> items = history.stream()
                .map(PolicyResponse::from)
                .toList();

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PolicyHistoryResponse(items));
    }

    // =========================================================================
    // 3. POST /api/v1/policies/preview
    // =========================================================================

    @PostMapping("/api/v1/policies/preview")
    public ResponseEntity<?> preview(
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la solicitud no puede estar vacío.", List.of());
        }

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(rawBody);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "El cuerpo de la solicitud no es un JSON válido.", List.of());
        }

        if (rootNode == null || !rootNode.isObject()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la solicitud debe ser un objeto JSON.", List.of());
        }

        // expectedCurrentPolicyVersionId: obligatorio y nullable
        if (!rootNode.has("expectedCurrentPolicyVersionId")) {
            List<Object> errors = List.of(new FieldErrorDto(
                    "expectedCurrentPolicyVersionId",
                    "REQUIRED",
                    "El campo 'expectedCurrentPolicyVersionId' es obligatorio."
            ));
            return validationFailedResponse(httpRequest, "El campo 'expectedCurrentPolicyVersionId' es obligatorio.", errors);
        }

        JsonNode expectedNode = rootNode.get("expectedCurrentPolicyVersionId");
        UUID expectedCurrentPolicyVersionId = null;
        if (expectedNode.isNull()) {
            expectedCurrentPolicyVersionId = null;
        } else if (expectedNode.isTextual()) {
            try {
                expectedCurrentPolicyVersionId = UUID.fromString(expectedNode.asText().trim());
            } catch (IllegalArgumentException e) {
                List<Object> errors = List.of(new FieldErrorDto(
                        "expectedCurrentPolicyVersionId",
                        "INVALID_FORMAT",
                        "El campo 'expectedCurrentPolicyVersionId' debe ser un UUID válido o null."
                ));
                return validationFailedResponse(httpRequest, "El formato de 'expectedCurrentPolicyVersionId' es inválido.", errors);
            }
        } else {
            List<Object> errors = List.of(new FieldErrorDto(
                    "expectedCurrentPolicyVersionId",
                    "INVALID_FORMAT",
                    "El campo 'expectedCurrentPolicyVersionId' debe ser un UUID válido o null."
            ));
            return validationFailedResponse(httpRequest, "El formato de 'expectedCurrentPolicyVersionId' es inválido.", errors);
        }

        // minimumRestMinutes
        JsonNode restNode = rootNode.get("minimumRestMinutes");
        if (restNode == null || !restNode.isInt()) {
            List<Object> errors = List.of(new FieldErrorDto(
                    "minimumRestMinutes",
                    "REQUIRED",
                    "El campo 'minimumRestMinutes' debe ser un número entero."
            ));
            return validationFailedResponse(httpRequest, "El campo 'minimumRestMinutes' debe ser un número entero.", errors);
        }
        int minRest = restNode.asInt();

        // weeklyWarningMinutes
        JsonNode weeklyNode = rootNode.get("weeklyWarningMinutes");
        if (weeklyNode == null || !weeklyNode.isInt()) {
            List<Object> errors = List.of(new FieldErrorDto(
                    "weeklyWarningMinutes",
                    "REQUIRED",
                    "El campo 'weeklyWarningMinutes' debe ser un número entero."
            ));
            return validationFailedResponse(httpRequest, "El campo 'weeklyWarningMinutes' debe ser un número entero.", errors);
        }
        int weeklyWarning = weeklyNode.asInt();

        PolicyThresholds proposedThresholds;
        try {
            proposedThresholds = new PolicyThresholds(minRest, weeklyWarning);
        } catch (PolicyValidationException e) {
            return validationFailedResponse(httpRequest, e.getMessage(), List.of());
        }

        PolicyPreviewResult result = previewPolicyChangeUseCase.preview(
                identity.organizationId(),
                expectedCurrentPolicyVersionId,
                proposedThresholds
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(PolicyPreviewResponse.from(result));
    }

    // =========================================================================
    // 4. POST /api/v1/policies/activate
    // =========================================================================

    @PostMapping("/api/v1/policies/activate")
    public ResponseEntity<?> activate(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        // 1. Validar Idempotency-Key
        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.", List.of());
        }

        // 2. Validar cuerpo JSON
        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la solicitud no puede estar vacío.", List.of());
        }

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(rawBody);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "El cuerpo de la solicitud no es un JSON válido.", List.of());
        }

        if (rootNode == null || !rootNode.isObject()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la solicitud debe ser un objeto JSON.", List.of());
        }

        // 3. expectedCurrentPolicyVersionId: obligatorio y nullable
        if (!rootNode.has("expectedCurrentPolicyVersionId")) {
            List<Object> errors = List.of(new FieldErrorDto(
                    "expectedCurrentPolicyVersionId",
                    "REQUIRED",
                    "El campo 'expectedCurrentPolicyVersionId' es obligatorio."
            ));
            return validationFailedResponse(httpRequest, "El campo 'expectedCurrentPolicyVersionId' es obligatorio.", errors);
        }

        JsonNode expectedNode = rootNode.get("expectedCurrentPolicyVersionId");
        UUID expectedCurrentPolicyVersionId = null;
        if (expectedNode.isNull()) {
            expectedCurrentPolicyVersionId = null;
        } else if (expectedNode.isTextual()) {
            try {
                expectedCurrentPolicyVersionId = UUID.fromString(expectedNode.asText().trim());
            } catch (IllegalArgumentException e) {
                List<Object> errors = List.of(new FieldErrorDto(
                        "expectedCurrentPolicyVersionId",
                        "INVALID_FORMAT",
                        "El campo 'expectedCurrentPolicyVersionId' debe ser un UUID válido o null."
                ));
                return validationFailedResponse(httpRequest, "El formato de 'expectedCurrentPolicyVersionId' es inválido.", errors);
            }
        } else {
            List<Object> errors = List.of(new FieldErrorDto(
                    "expectedCurrentPolicyVersionId",
                    "INVALID_FORMAT",
                    "El campo 'expectedCurrentPolicyVersionId' debe ser un UUID válido o null."
            ));
            return validationFailedResponse(httpRequest, "El formato de 'expectedCurrentPolicyVersionId' es inválido.", errors);
        }

        // 4. minimumRestMinutes
        JsonNode restNode = rootNode.get("minimumRestMinutes");
        if (restNode == null || !restNode.isInt()) {
            List<Object> errors = List.of(new FieldErrorDto(
                    "minimumRestMinutes",
                    "REQUIRED",
                    "El campo 'minimumRestMinutes' debe ser un número entero."
            ));
            return validationFailedResponse(httpRequest, "El campo 'minimumRestMinutes' debe ser un número entero.", errors);
        }
        int minRest = restNode.asInt();

        // 5. weeklyWarningMinutes
        JsonNode weeklyNode = rootNode.get("weeklyWarningMinutes");
        if (weeklyNode == null || !weeklyNode.isInt()) {
            List<Object> errors = List.of(new FieldErrorDto(
                    "weeklyWarningMinutes",
                    "REQUIRED",
                    "El campo 'weeklyWarningMinutes' debe ser un número entero."
            ));
            return validationFailedResponse(httpRequest, "El campo 'weeklyWarningMinutes' debe ser un número entero.", errors);
        }
        int weeklyWarning = weeklyNode.asInt();

        // 6. reason
        JsonNode reasonNode = rootNode.get("reason");
        if (reasonNode == null || !reasonNode.isTextual()) {
            return reasonRequiredResponse(httpRequest, "El motivo de activación es obligatorio.");
        }
        String reasonStr = reasonNode.asText();

        // 7. validationFingerprint
        JsonNode fingerprintNode = rootNode.get("validationFingerprint");
        if (fingerprintNode == null || !fingerprintNode.isTextual()) {
            return invalidFingerprintResponse(httpRequest, "La huella de validación es obligatoria.");
        }
        String fingerprintStr = fingerprintNode.asText();

        // 8. Validaciones de dominio previas
        PolicyThresholds proposedThresholds = new PolicyThresholds(minRest, weeklyWarning);
        PolicyReason changeReason = new PolicyReason(reasonStr);
        ValidationFingerprint fingerprint = new ValidationFingerprint(fingerprintStr);

        // 9. Construir comando idempotente
        String canonicalPayload = PolicyIdempotentCommand.buildCanonicalPayload(
                expectedCurrentPolicyVersionId,
                minRest,
                weeklyWarning,
                reasonStr,
                fingerprintStr
        );

        PolicyIdempotentCommand command = new PolicyIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                canonicalPayload,
                objectMapper
        );

        // 10. Efecto de negocio: delega al caso de uso la mutación transaccional y el lock
        final UUID finalExpectedId = expectedCurrentPolicyVersionId;
        Supplier<CurrentPolicyResponse> effect = () -> {
            ActivatePolicyCommand activateCmd = new ActivatePolicyCommand(
                    identity.organizationId(),
                    identity.userId(),
                    finalExpectedId,
                    proposedThresholds,
                    fingerprintStr,
                    changeReason
            );
            PolicyVersion persisted = activatePolicyUseCase.activate(activateCmd);
            return new CurrentPolicyResponse(PolicyResponse.from(persisted));
        };

        // 11. Orquestación: idempotencia primero, luego efecto con lock de política
        BusinessIdempotencyResult<CurrentPolicyResponse> result = executor.execute(command, effect);

        return renderIdempotencyResult(result, httpRequest);
    }

    // =========================================================================
    // Manejadores de Excepciones
    // =========================================================================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetailDto> handleHttpMessageNotReadable(HttpServletRequest request, HttpMessageNotReadableException ex) {
        return validationFailedResponse(request, "El cuerpo de la solicitud no es un JSON válido.", List.of());
    }

    @ExceptionHandler(PolicyValidationException.class)
    public ResponseEntity<ProblemDetailDto> handlePolicyValidationException(HttpServletRequest request, PolicyValidationException ex) {
        String code = (ex.getErrorCode() != null && !ex.getErrorCode().isBlank()) ? ex.getErrorCode() : "VALIDATION_FAILED";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/validation-failed",
                        "Error de validación",
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        code,
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    @ExceptionHandler(PolicyConcurrentModificationException.class)
    public ResponseEntity<ProblemDetailDto> handleConcurrentModification(HttpServletRequest request, PolicyConcurrentModificationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/policy-concurrent-modification",
                        "Conflicto de concurrencia de política",
                        HttpStatus.CONFLICT.value(),
                        "La política actual cambió mientras se preparaba la previsualización.",
                        request.getRequestURI(),
                        "POLICY_CONCURRENT_MODIFICATION",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    @ExceptionHandler(PolicyOperationBusyException.class)
    public ResponseEntity<ProblemDetailDto> handlePolicyOperationBusy(HttpServletRequest request, PolicyOperationBusyException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(new ProblemDetailDto(
                        "/problems/policy-operation-busy",
                        "Operación de política ocupada",
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "El recurso de políticas de la organización está ocupado por otra operación concurrente. Intente nuevamente más tarde.",
                        request.getRequestURI(),
                        "POLICY_OPERATION_BUSY",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    @ExceptionHandler(PolicyDataAccessException.class)
    public ResponseEntity<ProblemDetailDto> handleDataAccessException(HttpServletRequest request, PolicyDataAccessException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/service-unavailable",
                        "Servicio no disponible",
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "El servicio no está disponible temporalmente. Intente más tarde.",
                        request.getRequestURI(),
                        "SERVICE_UNAVAILABLE",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    // =========================================================================
    // Auxiliares
    // =========================================================================

    private Optional<AuthenticatedIdentity> getAuthenticatedIdentity() {
        Authentication auth = securityContextHolderStrategy.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedIdentity identity) {
            return Optional.of(identity);
        }
        return Optional.empty();
    }

    private ResponseEntity<ProblemDetailDto> sessionRequiredResponse(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(new ProblemDetailDto(
                        "/problems/session-required",
                        "Sesión requerida",
                        HttpStatus.UNAUTHORIZED.value(),
                        "No existe sesión activa. Inicia sesión para continuar.",
                        request.getRequestURI(),
                        "SESSION_REQUIRED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private ResponseEntity<ProblemDetailDto> validationFailedResponse(
            HttpServletRequest request,
            String detail,
            List<Object> errors
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/validation-failed",
                        "Error de validación",
                        HttpStatus.BAD_REQUEST.value(),
                        detail,
                        request.getRequestURI(),
                        "VALIDATION_FAILED",
                        UUID.randomUUID().toString(),
                        errors != null ? errors : List.of()
                ));
    }

    private ResponseEntity<ProblemDetailDto> reasonRequiredResponse(HttpServletRequest request, String detail) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/validation-failed",
                        "Error de validación",
                        HttpStatus.BAD_REQUEST.value(),
                        detail,
                        request.getRequestURI(),
                        "REASON_REQUIRED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private ResponseEntity<ProblemDetailDto> invalidFingerprintResponse(HttpServletRequest request, String detail) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/validation-failed",
                        "Error de validación",
                        HttpStatus.BAD_REQUEST.value(),
                        detail,
                        request.getRequestURI(),
                        "INVALID_VALIDATION_FINGERPRINT",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private UUID parseIdempotencyKey(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ResponseEntity<?> renderIdempotencyResult(
            BusinessIdempotencyResult<CurrentPolicyResponse> result,
            HttpServletRequest httpRequest
    ) {
        if (result instanceof BusinessIdempotencyResult.Proceed(var response)) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        } else if (result instanceof BusinessIdempotencyResult.Replay(var response)) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("Idempotency-Replayed", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        } else if (result instanceof BusinessIdempotencyResult.KeyReused) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(new ProblemDetailDto(
                            "/problems/idempotency-key-reused",
                            "Clave de idempotencia reutilizada",
                            HttpStatus.CONFLICT.value(),
                            "La clave de idempotencia ya fue utilizada con una petición diferente.",
                            httpRequest.getRequestURI(),
                            "IDEMPOTENCY_KEY_REUSED",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        } else if (result instanceof BusinessIdempotencyResult.Expired) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(new ProblemDetailDto(
                            "/problems/idempotency-key-expired",
                            "Clave de idempotencia expirada",
                            HttpStatus.CONFLICT.value(),
                            "La clave de idempotencia ha expirado.",
                            httpRequest.getRequestURI(),
                            "IDEMPOTENCY_KEY_EXPIRED",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        }
        throw new IllegalStateException("Resultado de idempotencia no soportado: " + result);
    }
}
