package com.turnoshift.team.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.security.SecurityProblemHandlers.ProblemDetailDto;
import com.turnoshift.security.idempotency.BusinessFingerprintCalculator;
import com.turnoshift.security.idempotency.BusinessIdempotencyDataAccessException;
import com.turnoshift.security.idempotency.BusinessIdempotencyResult;
import com.turnoshift.security.idempotency.BusinessIdempotentOperationExecutor;
import com.turnoshift.team.application.ChangePositionStatusUseCase;
import com.turnoshift.team.application.CreatePositionUseCase;
import com.turnoshift.team.application.GetPositionsUseCase;
import com.turnoshift.team.application.UpdatePositionUseCase;
import com.turnoshift.team.domain.InvalidPositionDataException;
import com.turnoshift.team.domain.Position;
import com.turnoshift.team.domain.PositionCodeAlreadyExistsException;
import com.turnoshift.team.domain.PositionNotFoundException;
import com.turnoshift.team.domain.PositionOptimisticLockException;
import com.turnoshift.team.infrastructure.PositionDataAccessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Controlador REST para el catálogo de puestos de trabajo (HU-06).
 *
 * <p>Aplica aislamiento multi-tenant estricto derivando la organización y el actor
 * exclusivamente de la sesión autenticada en el SecurityContext.
 *
 * <p>Las mutaciones se coordinan de manera atómica e idempotente con {@link BusinessIdempotentOperationExecutor}.
 */
@RestController
public class PositionController {

    private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
            MediaType.parseMediaType("application/problem+json;charset=UTF-8");

    private final CreatePositionUseCase createPositionUseCase;
    private final UpdatePositionUseCase updatePositionUseCase;
    private final ChangePositionStatusUseCase changePositionStatusUseCase;
    private final GetPositionsUseCase getPositionsUseCase;
    private final BusinessIdempotentOperationExecutor executor;
    private final ObjectMapper objectMapper;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public PositionController(
            CreatePositionUseCase createPositionUseCase,
            UpdatePositionUseCase updatePositionUseCase,
            ChangePositionStatusUseCase changePositionStatusUseCase,
            GetPositionsUseCase getPositionsUseCase,
            BusinessIdempotentOperationExecutor executor,
            ObjectMapper objectMapper
    ) {
        this.createPositionUseCase = Objects.requireNonNull(createPositionUseCase, "createPositionUseCase no puede ser nulo");
        this.updatePositionUseCase = Objects.requireNonNull(updatePositionUseCase, "updatePositionUseCase no puede ser nulo");
        this.changePositionStatusUseCase = Objects.requireNonNull(changePositionStatusUseCase, "changePositionStatusUseCase no puede ser nulo");
        this.getPositionsUseCase = Objects.requireNonNull(getPositionsUseCase, "getPositionsUseCase no puede ser nulo");
        this.executor = Objects.requireNonNull(executor, "executor no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    // =========================================================================
    // GET /api/v1/positions
    // =========================================================================

    @GetMapping("/api/v1/positions")
    public ResponseEntity<?> getPositions(
            @RequestParam(name = "active", required = false) String activeParam,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        Boolean activeFilter = null;
        if (activeParam != null) {
            String trimmed = activeParam.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(trimmed)) {
                activeFilter = Boolean.TRUE;
            } else if ("false".equals(trimmed)) {
                activeFilter = Boolean.FALSE;
            } else {
                return validationFailedResponse(httpRequest, "El parámetro 'active' solo admite los valores 'true' o 'false'.");
            }
        }

        List<Position> positions = getPositionsUseCase.execute(identity.organizationId(), activeFilter);
        List<PositionResponse> responseBody = positions.stream()
                .map(PositionResponse::from)
                .toList();

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    // =========================================================================
    // POST /api/v1/positions
    // =========================================================================

    @PostMapping("/api/v1/positions")
    public ResponseEntity<?> createPosition(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la petición es obligatorio.");
        }

        CreatePositionRequest req;
        try {
            req = objectMapper.readValue(rawBody, CreatePositionRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        String normalizedCode;
        String normalizedName;
        try {
            normalizedCode = Position.validateAndNormalizeCode(req.code());
            normalizedName = Position.validateAndNormalizeName(req.name());
        } catch (InvalidPositionDataException e) {
            return validationFailedResponse(httpRequest, "Los datos del puesto no cumplen con las reglas requeridas.");
        }

        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, normalizedCode);
        BusinessFingerprintCalculator.appendField(sb, normalizedName);
        String canonicalPayload = sb.toString();

        PositionIdempotentCommand command = new PositionIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "POST",
                "/api/v1/positions",
                canonicalPayload,
                true,
                objectMapper
        );

        Supplier<PositionExecutionResult> effect = () -> {
            try {
                Position pos = createPositionUseCase.execute(identity.organizationId(), normalizedCode, normalizedName);
                return new PositionExecutionResult.Success(PositionResponse.from(pos));
            } catch (PositionCodeAlreadyExistsException e) {
                return new PositionExecutionResult.CodeAlreadyExists();
            } catch (PositionNotFoundException e) {
                return new PositionExecutionResult.NotFound();
            } catch (PositionOptimisticLockException e) {
                return new PositionExecutionResult.StaleVersion();
            }
        };

        BusinessIdempotencyResult<PositionExecutionResult> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, true, httpRequest);
    }

    // =========================================================================
    // PUT /api/v1/positions/{positionId}
    // =========================================================================

    @PutMapping("/api/v1/positions/{positionId}")
    public ResponseEntity<?> updatePosition(
            @PathVariable("positionId") String positionIdParam,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID positionId = parseUuid(positionIdParam);
        if (positionId == null) {
            return validationFailedResponse(httpRequest, "El identificador de puesto debe ser un UUID válido.");
        }

        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la petición es obligatorio.");
        }

        UpdatePositionRequest req;
        try {
            req = objectMapper.readValue(rawBody, UpdatePositionRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        if (req.expectedLockVersion() == null || req.expectedLockVersion() < 0) {
            return validationFailedResponse(httpRequest, "El campo expectedLockVersion es obligatorio y debe ser mayor o igual a 0.");
        }

        String normalizedName;
        try {
            normalizedName = Position.validateAndNormalizeName(req.name());
        } catch (InvalidPositionDataException e) {
            return validationFailedResponse(httpRequest, "El nombre proporcionado es inválido.");
        }

        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, positionId.toString());
        BusinessFingerprintCalculator.appendField(sb, normalizedName);
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(req.expectedLockVersion()));
        String canonicalPayload = sb.toString();

        PositionIdempotentCommand command = new PositionIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "PUT",
                "/api/v1/positions/" + positionId,
                canonicalPayload,
                false,
                objectMapper
        );

        Supplier<PositionExecutionResult> effect = () -> {
            try {
                Position pos = updatePositionUseCase.execute(identity.organizationId(), positionId, normalizedName, req.expectedLockVersion());
                return new PositionExecutionResult.Success(PositionResponse.from(pos));
            } catch (PositionCodeAlreadyExistsException e) {
                return new PositionExecutionResult.CodeAlreadyExists();
            } catch (PositionNotFoundException e) {
                return new PositionExecutionResult.NotFound();
            } catch (PositionOptimisticLockException e) {
                return new PositionExecutionResult.StaleVersion();
            }
        };

        BusinessIdempotencyResult<PositionExecutionResult> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, false, httpRequest);
    }

    // =========================================================================
    // PATCH /api/v1/positions/{positionId}/status
    // =========================================================================

    @PatchMapping("/api/v1/positions/{positionId}/status")
    public ResponseEntity<?> changePositionStatus(
            @PathVariable("positionId") String positionIdParam,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID positionId = parseUuid(positionIdParam);
        if (positionId == null) {
            return validationFailedResponse(httpRequest, "El identificador de puesto debe ser un UUID válido.");
        }

        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la petición es obligatorio.");
        }

        ChangePositionStatusRequest req;
        try {
            req = objectMapper.readValue(rawBody, ChangePositionStatusRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        if (req.isActive() == null) {
            return validationFailedResponse(httpRequest, "El campo isActive es obligatorio.");
        }

        if (req.expectedLockVersion() == null || req.expectedLockVersion() < 0) {
            return validationFailedResponse(httpRequest, "El campo expectedLockVersion es obligatorio y debe ser mayor o igual a 0.");
        }

        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, positionId.toString());
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(req.isActive()));
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(req.expectedLockVersion()));
        String canonicalPayload = sb.toString();

        PositionIdempotentCommand command = new PositionIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "PATCH",
                "/api/v1/positions/" + positionId + "/status",
                canonicalPayload,
                false,
                objectMapper
        );

        Supplier<PositionExecutionResult> effect = () -> {
            try {
                Position pos = changePositionStatusUseCase.execute(identity.organizationId(), positionId, req.isActive(), req.expectedLockVersion());
                return new PositionExecutionResult.Success(PositionResponse.from(pos));
            } catch (PositionCodeAlreadyExistsException e) {
                return new PositionExecutionResult.CodeAlreadyExists();
            } catch (PositionNotFoundException e) {
                return new PositionExecutionResult.NotFound();
            } catch (PositionOptimisticLockException e) {
                return new PositionExecutionResult.StaleVersion();
            }
        };

        BusinessIdempotencyResult<PositionExecutionResult> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, false, httpRequest);
    }

    // =========================================================================
    // Manejo de excepciones de persistencia e infraestructura
    // =========================================================================

    @ExceptionHandler({PositionDataAccessException.class, BusinessIdempotencyDataAccessException.class})
    public ResponseEntity<ProblemDetailDto> handleDataAccessException(HttpServletRequest request, Exception ex) {
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

    @ExceptionHandler(InvalidPositionDataException.class)
    public ResponseEntity<ProblemDetailDto> handleInvalidPositionDataException(HttpServletRequest request, InvalidPositionDataException ex) {
        return validationFailedResponse(request, "Los datos proporcionados no cumplen con el formato requerido.");
    }

    // =========================================================================
    // Métodos auxiliares de renderizado y validación
    // =========================================================================

    private Optional<AuthenticatedIdentity> getAuthenticatedIdentity() {
        Authentication auth = securityContextHolderStrategy.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedIdentity identity) {
            return Optional.of(identity);
        }
        return Optional.empty();
    }

    private ResponseEntity<?> renderIdempotencyResult(
            BusinessIdempotencyResult<PositionExecutionResult> result,
            boolean isCreate,
            HttpServletRequest httpRequest
    ) {
        if (result instanceof BusinessIdempotencyResult.Proceed(var execResult)) {
            return renderExecutionResult(execResult, isCreate, false, httpRequest);
        } else if (result instanceof BusinessIdempotencyResult.Replay(var execResult)) {
            return renderExecutionResult(execResult, isCreate, true, httpRequest);
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

    private ResponseEntity<?> renderExecutionResult(
            PositionExecutionResult execResult,
            boolean isCreate,
            boolean isReplay,
            HttpServletRequest httpRequest
    ) {
        int statusCode = (execResult instanceof PositionExecutionResult.Success)
                ? (isCreate ? HttpStatus.CREATED.value() : HttpStatus.OK.value())
                : (execResult instanceof PositionExecutionResult.NotFound ? HttpStatus.NOT_FOUND.value() : HttpStatus.CONFLICT.value());

        var builder = ResponseEntity.status(statusCode)
                .header(HttpHeaders.CACHE_CONTROL, "no-store");

        if (isReplay) {
            builder.header("Idempotency-Replayed", "true");
        }

        if (execResult instanceof PositionExecutionResult.Success(var pos)) {
            return builder.contentType(MediaType.APPLICATION_JSON).body(pos);
        } else if (execResult instanceof PositionExecutionResult.NotFound) {
            return builder.contentType(PROBLEM_JSON_MEDIA_TYPE).body(new ProblemDetailDto(
                    "/problems/position-not-found",
                    "Puesto no encontrado",
                    HttpStatus.NOT_FOUND.value(),
                    "El puesto solicitado no existe o no pertenece a la organización.",
                    httpRequest.getRequestURI(),
                    "POSITION_NOT_FOUND",
                    UUID.randomUUID().toString(),
                    List.of()
            ));
        } else if (execResult instanceof PositionExecutionResult.CodeAlreadyExists) {
            return builder.contentType(PROBLEM_JSON_MEDIA_TYPE).body(new ProblemDetailDto(
                    "/problems/position-code-already-exists",
                    "Código de puesto duplicado",
                    HttpStatus.CONFLICT.value(),
                    "El código de puesto ya existe en la organización.",
                    httpRequest.getRequestURI(),
                    "POSITION_CODE_ALREADY_EXISTS",
                    UUID.randomUUID().toString(),
                    List.of()
            ));
        } else if (execResult instanceof PositionExecutionResult.StaleVersion) {
            return builder.contentType(PROBLEM_JSON_MEDIA_TYPE).body(new ProblemDetailDto(
                    "/problems/concurrent-modification",
                    "Conflicto de concurrencia",
                    HttpStatus.CONFLICT.value(),
                    "El recurso fue modificado por otra operación concurrente.",
                    httpRequest.getRequestURI(),
                    "CONCURRENT_MODIFICATION",
                    UUID.randomUUID().toString(),
                    List.of()
            ));
        }
        throw new IllegalStateException("Resultado de ejecución no soportado: " + execResult);
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

    private UUID parseUuid(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(param.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ResponseEntity<ProblemDetailDto> sessionRequiredResponse(HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(new ProblemDetailDto(
                        "/problems/session-required",
                        "Sesión requerida",
                        HttpStatus.UNAUTHORIZED.value(),
                        "No existe sesión activa. Inicia sesión para continuar.",
                        httpRequest.getRequestURI(),
                        "SESSION_REQUIRED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private ResponseEntity<ProblemDetailDto> validationFailedResponse(HttpServletRequest httpRequest, String detail) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(new ProblemDetailDto(
                        "/problems/validation-failed",
                        "Error de validación",
                        HttpStatus.BAD_REQUEST.value(),
                        detail,
                        httpRequest.getRequestURI(),
                        "VALIDATION_FAILED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }
}
