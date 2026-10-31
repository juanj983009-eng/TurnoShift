package com.turnoshift.team.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.security.SecurityProblemHandlers.ProblemDetailDto;
import com.turnoshift.security.idempotency.BusinessFingerprintCalculator;
import com.turnoshift.security.idempotency.BusinessIdempotencyDataAccessException;
import com.turnoshift.security.idempotency.BusinessIdempotencyResult;
import com.turnoshift.security.idempotency.BusinessIdempotentOperationExecutor;
import com.turnoshift.team.application.ActivateEmployeeUseCase;
import com.turnoshift.team.application.CreateEmployeeUseCase;
import com.turnoshift.team.application.DeactivateEmployeeUseCase;
import com.turnoshift.team.application.GetEmployeeUseCase;
import com.turnoshift.team.application.GetEmployeesUseCase;
import com.turnoshift.team.application.UpdateEmployeeUseCase;
import com.turnoshift.team.domain.DeactivationPreview;
import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeCodeAlreadyExistsException;
import com.turnoshift.team.domain.EmployeeLicenseLimitExceededException;
import com.turnoshift.team.domain.EmployeeNotFoundException;
import com.turnoshift.team.domain.EmployeeOptimisticLockException;
import com.turnoshift.team.domain.InvalidEmployeeDataException;
import com.turnoshift.team.domain.NoActiveSiteException;
import com.turnoshift.team.domain.PositionInactiveException;
import com.turnoshift.team.domain.PositionNotFoundException;
import com.turnoshift.team.infrastructure.EmployeeDataAccessException;
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

@RestController
public class EmployeeController {

    private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
            MediaType.parseMediaType("application/problem+json;charset=UTF-8");

    private final CreateEmployeeUseCase createEmployeeUseCase;
    private final GetEmployeesUseCase getEmployeesUseCase;
    private final GetEmployeeUseCase getEmployeeUseCase;
    private final UpdateEmployeeUseCase updateEmployeeUseCase;
    private final ActivateEmployeeUseCase activateEmployeeUseCase;
    private final DeactivateEmployeeUseCase deactivateEmployeeUseCase;
    private final BusinessIdempotentOperationExecutor executor;
    private final ObjectMapper objectMapper;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public EmployeeController(
            CreateEmployeeUseCase createEmployeeUseCase,
            GetEmployeesUseCase getEmployeesUseCase,
            GetEmployeeUseCase getEmployeeUseCase,
            UpdateEmployeeUseCase updateEmployeeUseCase,
            ActivateEmployeeUseCase activateEmployeeUseCase,
            DeactivateEmployeeUseCase deactivateEmployeeUseCase,
            BusinessIdempotentOperationExecutor executor,
            ObjectMapper objectMapper
    ) {
        this.createEmployeeUseCase = Objects.requireNonNull(createEmployeeUseCase, "createEmployeeUseCase no puede ser nulo");
        this.getEmployeesUseCase = Objects.requireNonNull(getEmployeesUseCase, "getEmployeesUseCase no puede ser nulo");
        this.getEmployeeUseCase = Objects.requireNonNull(getEmployeeUseCase, "getEmployeeUseCase no puede ser nulo");
        this.updateEmployeeUseCase = Objects.requireNonNull(updateEmployeeUseCase, "updateEmployeeUseCase no puede ser nulo");
        this.activateEmployeeUseCase = Objects.requireNonNull(activateEmployeeUseCase, "activateEmployeeUseCase no puede ser nulo");
        this.deactivateEmployeeUseCase = Objects.requireNonNull(deactivateEmployeeUseCase, "deactivateEmployeeUseCase no puede ser nulo");
        this.executor = Objects.requireNonNull(executor, "executor no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    // =========================================================================
    // 1. GET /api/v1/employees?active=true|false
    // =========================================================================

    @GetMapping("/api/v1/employees")
    public ResponseEntity<?> getEmployees(
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

        List<Employee> employees = getEmployeesUseCase.execute(identity.organizationId(), activeFilter);
        List<EmployeeResponse> responseBody = employees.stream()
                .map(EmployeeResponse::from)
                .toList();

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    // =========================================================================
    // 2. GET /api/v1/employees/{employeeId}
    // =========================================================================

    @GetMapping("/api/v1/employees/{employeeId}")
    public ResponseEntity<?> getEmployee(
            @PathVariable("employeeId") String employeeIdParam,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID employeeId = parseUuid(employeeIdParam);
        if (employeeId == null) {
            return validationFailedResponse(httpRequest, "El identificador del colaborador debe ser un UUID válido.");
        }

        try {
            Employee employee = getEmployeeUseCase.execute(identity.organizationId(), employeeId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(EmployeeResponse.from(employee));
        } catch (EmployeeNotFoundException e) {
            return notFoundResponse(httpRequest, "El colaborador solicitado no existe o no pertenece a la organización.");
        }
    }

    // =========================================================================
    // 3. POST /api/v1/employees
    // =========================================================================

    @PostMapping("/api/v1/employees")
    public ResponseEntity<?> createEmployee(
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

        CreateEmployeeRequest req;
        try {
            req = objectMapper.readValue(rawBody, CreateEmployeeRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        if (req.positionId() == null) {
            return validationFailedResponse(httpRequest, "El campo positionId es obligatorio.");
        }

        String normalizedCode;
        String normalizedDisplayName;
        try {
            normalizedCode = Employee.validateAndNormalizeCode(req.code());
            normalizedDisplayName = Employee.validateAndNormalizeDisplayName(req.displayName());
        } catch (InvalidEmployeeDataException e) {
            return validationFailedResponse(httpRequest, "Los datos del colaborador no cumplen con las reglas requeridas.");
        }

        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, normalizedCode);
        BusinessFingerprintCalculator.appendField(sb, normalizedDisplayName);
        BusinessFingerprintCalculator.appendField(sb, req.positionId().toString());
        String canonicalPayload = sb.toString();

        EmployeeIdempotentCommand command = new EmployeeIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "POST",
                "/api/v1/employees",
                canonicalPayload,
                true,
                objectMapper
        );

        Supplier<EmployeeExecutionResult> effect = () -> {
            try {
                Employee employee = createEmployeeUseCase.execute(
                        identity.organizationId(),
                        normalizedCode,
                        normalizedDisplayName,
                        req.positionId()
                );
                return new EmployeeExecutionResult.Success(EmployeeResponse.from(employee));
            } catch (EmployeeCodeAlreadyExistsException e) {
                return new EmployeeExecutionResult.CodeAlreadyExists();
            } catch (PositionNotFoundException e) {
                return new EmployeeExecutionResult.PositionNotFound();
            } catch (PositionInactiveException e) {
                return new EmployeeExecutionResult.PositionInactive();
            } catch (NoActiveSiteException e) {
                return new EmployeeExecutionResult.NoActiveSite();
            } catch (EmployeeLicenseLimitExceededException e) {
                return new EmployeeExecutionResult.LicenseLimitExceeded();
            }
        };

        BusinessIdempotencyResult<EmployeeExecutionResult> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, true, httpRequest);
    }

    // =========================================================================
    // 4. PATCH /api/v1/employees/{employeeId}
    // =========================================================================

    @PatchMapping("/api/v1/employees/{employeeId}")
    public ResponseEntity<?> updateEmployee(
            @PathVariable("employeeId") String employeeIdParam,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID employeeId = parseUuid(employeeIdParam);
        if (employeeId == null) {
            return validationFailedResponse(httpRequest, "El identificador del colaborador debe ser un UUID válido.");
        }

        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la petición es obligatorio.");
        }

        UpdateEmployeeRequest req;
        try {
            req = objectMapper.readValue(rawBody, UpdateEmployeeRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        if (req.expectedLockVersion() == null || req.expectedLockVersion() < 0) {
            return validationFailedResponse(httpRequest, "El campo expectedLockVersion es obligatorio y debe ser mayor o igual a 0.");
        }

        if ((req.displayName() == null || req.displayName().isBlank()) && req.positionId() == null) {
            return validationFailedResponse(httpRequest, "Al menos displayName o positionId debe estar presente.");
        }

        String normalizedDisplayName = null;
        if (req.displayName() != null) {
            try {
                normalizedDisplayName = Employee.validateAndNormalizeDisplayName(req.displayName());
            } catch (InvalidEmployeeDataException e) {
                return validationFailedResponse(httpRequest, "El nombre del colaborador no cumple con las reglas requeridas.");
            }
        }

        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, employeeId.toString());
        if (normalizedDisplayName != null) {
            BusinessFingerprintCalculator.appendField(sb, normalizedDisplayName);
        }
        if (req.positionId() != null) {
            BusinessFingerprintCalculator.appendField(sb, req.positionId().toString());
        }
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(req.expectedLockVersion()));
        String canonicalPayload = sb.toString();

        EmployeeIdempotentCommand command = new EmployeeIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "PATCH",
                "/api/v1/employees/" + employeeId,
                canonicalPayload,
                false,
                objectMapper
        );

        final String finalDisplayName = normalizedDisplayName;
        Supplier<EmployeeExecutionResult> effect = () -> {
            try {
                Employee employee = updateEmployeeUseCase.execute(
                        identity.organizationId(),
                        employeeId,
                        finalDisplayName,
                        req.positionId(),
                        req.expectedLockVersion()
                );
                return new EmployeeExecutionResult.Success(EmployeeResponse.from(employee));
            } catch (EmployeeNotFoundException e) {
                return new EmployeeExecutionResult.NotFound();
            } catch (PositionNotFoundException e) {
                return new EmployeeExecutionResult.PositionNotFound();
            } catch (PositionInactiveException e) {
                return new EmployeeExecutionResult.PositionInactive();
            } catch (EmployeeOptimisticLockException e) {
                return new EmployeeExecutionResult.StaleVersion();
            }
        };

        BusinessIdempotencyResult<EmployeeExecutionResult> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, false, httpRequest);
    }

    // =========================================================================
    // 5. POST /api/v1/employees/{employeeId}/activate
    // =========================================================================

    @PostMapping("/api/v1/employees/{employeeId}/activate")
    public ResponseEntity<?> activateEmployee(
            @PathVariable("employeeId") String employeeIdParam,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        return handleStatusChange(employeeIdParam, idempotencyKeyHeader, rawBody, httpRequest, true);
    }

    // =========================================================================
    // 6. POST /api/v1/employees/{employeeId}/deactivate
    // =========================================================================

    @PostMapping("/api/v1/employees/{employeeId}/deactivate")
    public ResponseEntity<?> deactivateEmployee(
            @PathVariable("employeeId") String employeeIdParam,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        return handleStatusChange(employeeIdParam, idempotencyKeyHeader, rawBody, httpRequest, false);
    }

    private ResponseEntity<?> handleStatusChange(
            String employeeIdParam,
            String idempotencyKeyHeader,
            String rawBody,
            HttpServletRequest httpRequest,
            boolean isActivate
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID employeeId = parseUuid(employeeIdParam);
        if (employeeId == null) {
            return validationFailedResponse(httpRequest, "El identificador del colaborador debe ser un UUID válido.");
        }

        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la petición es obligatorio.");
        }

        ChangeEmployeeStatusRequest req;
        try {
            req = objectMapper.readValue(rawBody, ChangeEmployeeStatusRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        if (req.expectedLockVersion() == null || req.expectedLockVersion() < 0) {
            return validationFailedResponse(httpRequest, "El campo expectedLockVersion es obligatorio y debe ser mayor o igual a 0.");
        }

        String actionPath = isActivate ? "/activate" : "/deactivate";
        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, employeeId.toString());
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(req.expectedLockVersion()));
        String canonicalPayload = sb.toString();

        EmployeeIdempotentCommand command = new EmployeeIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "POST",
                "/api/v1/employees/" + employeeId + actionPath,
                canonicalPayload,
                false,
                objectMapper
        );

        Supplier<EmployeeExecutionResult> effect = () -> {
            try {
                Employee employee = isActivate
                        ? activateEmployeeUseCase.execute(identity.organizationId(), employeeId, req.expectedLockVersion())
                        : deactivateEmployeeUseCase.execute(identity.organizationId(), employeeId, req.expectedLockVersion());
                return new EmployeeExecutionResult.Success(EmployeeResponse.from(employee));
            } catch (EmployeeNotFoundException e) {
                return new EmployeeExecutionResult.NotFound();
            } catch (EmployeeLicenseLimitExceededException e) {
                return new EmployeeExecutionResult.LicenseLimitExceeded();
            } catch (EmployeeOptimisticLockException e) {
                return new EmployeeExecutionResult.StaleVersion();
            }
        };

        BusinessIdempotencyResult<EmployeeExecutionResult> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, false, httpRequest);
    }

    // =========================================================================
    // 7. POST /api/v1/employees/{employeeId}/deactivation-preview
    // =========================================================================

    @PostMapping("/api/v1/employees/{employeeId}/deactivation-preview")
    public ResponseEntity<?> deactivationPreview(
            @PathVariable("employeeId") String employeeIdParam,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID employeeId = parseUuid(employeeIdParam);
        if (employeeId == null) {
            return validationFailedResponse(httpRequest, "El identificador del colaborador debe ser un UUID válido.");
        }

        try {
            DeactivationPreview preview = deactivateEmployeeUseCase.preview(identity.organizationId(), employeeId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(DeactivationPreviewResponse.from(preview));
        } catch (EmployeeNotFoundException e) {
            return notFoundResponse(httpRequest, "El colaborador solicitado no existe o no pertenece a la organización.");
        }
    }

    // =========================================================================
    // Manejo de excepciones de persistencia e infraestructura
    // =========================================================================

    @ExceptionHandler({EmployeeDataAccessException.class, BusinessIdempotencyDataAccessException.class})
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

    @ExceptionHandler(InvalidEmployeeDataException.class)
    public ResponseEntity<ProblemDetailDto> handleInvalidEmployeeDataException(HttpServletRequest request, InvalidEmployeeDataException ex) {
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
            BusinessIdempotencyResult<EmployeeExecutionResult> result,
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
            EmployeeExecutionResult execResult,
            boolean isCreate,
            boolean isReplay,
            HttpServletRequest httpRequest
    ) {
        int statusCode;
        if (execResult instanceof EmployeeExecutionResult.Success) {
            statusCode = isCreate ? HttpStatus.CREATED.value() : HttpStatus.OK.value();
        } else if (execResult instanceof EmployeeExecutionResult.NotFound
                || execResult instanceof EmployeeExecutionResult.PositionNotFound) {
            statusCode = HttpStatus.NOT_FOUND.value();
        } else {
            statusCode = HttpStatus.CONFLICT.value();
        }

        var builder = ResponseEntity.status(statusCode)
                .header(HttpHeaders.CACHE_CONTROL, "no-store");

        if (isReplay) {
            builder.header("Idempotency-Replayed", "true");
        }

        if (execResult instanceof EmployeeExecutionResult.Success(var emp)) {
            return builder
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(emp);
        } else if (execResult instanceof EmployeeExecutionResult.NotFound) {
            return builder
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/employee-not-found",
                            "Colaborador no encontrado",
                            HttpStatus.NOT_FOUND.value(),
                            "El colaborador solicitado no existe o no pertenece a la organización.",
                            httpRequest.getRequestURI(),
                            "EMPLOYEE_NOT_FOUND",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        } else if (execResult instanceof EmployeeExecutionResult.PositionNotFound) {
            return builder
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/position-not-found",
                            "Puesto no encontrado",
                            HttpStatus.NOT_FOUND.value(),
                            "El puesto especificado no existe en la organización.",
                            httpRequest.getRequestURI(),
                            "POSITION_NOT_FOUND",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        } else if (execResult instanceof EmployeeExecutionResult.CodeAlreadyExists) {
            return builder
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/employee-code-already-exists",
                            "Código de colaborador duplicado",
                            HttpStatus.CONFLICT.value(),
                            "El código de colaborador ya existe en la organización.",
                            httpRequest.getRequestURI(),
                            "EMPLOYEE_CODE_ALREADY_EXISTS",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        } else if (execResult instanceof EmployeeExecutionResult.PositionInactive) {
            return builder
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/position-inactive",
                            "Puesto inactivo",
                            HttpStatus.CONFLICT.value(),
                            "El puesto especificado se encuentra inactivo.",
                            httpRequest.getRequestURI(),
                            "POSITION_INACTIVE",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        } else if (execResult instanceof EmployeeExecutionResult.NoActiveSite) {
            return builder
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/no-active-site",
                            "Sin sede activa",
                            HttpStatus.CONFLICT.value(),
                            "La organización no cuenta con una sede activa.",
                            httpRequest.getRequestURI(),
                            "NO_ACTIVE_SITE",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        } else if (execResult instanceof EmployeeExecutionResult.LicenseLimitExceeded) {
            return builder
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/employee-license-limit-reached",
                            "Límite de empleados alcanzado",
                            HttpStatus.CONFLICT.value(),
                            "Se ha alcanzado el límite de empleados activos contratados para la organización.",
                            httpRequest.getRequestURI(),
                            "EMPLOYEE_LICENSE_LIMIT_REACHED",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        } else if (execResult instanceof EmployeeExecutionResult.StaleVersion) {
            return builder
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
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
        throw new IllegalStateException("Resultado no soportado: " + execResult);
    }

    private ResponseEntity<ProblemDetailDto> validationFailedResponse(HttpServletRequest request, String detail) {
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
                        List.of()
                ));
    }

    private ResponseEntity<ProblemDetailDto> notFoundResponse(HttpServletRequest request, String detail) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/employee-not-found",
                        "Colaborador no encontrado",
                        HttpStatus.NOT_FOUND.value(),
                        detail,
                        request.getRequestURI(),
                        "EMPLOYEE_NOT_FOUND",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private ResponseEntity<ProblemDetailDto> sessionRequiredResponse(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/session-required",
                        "Sesión requerida",
                        HttpStatus.UNAUTHORIZED.value(),
                        "Para acceder a este recurso debe iniciar sesión.",
                        request.getRequestURI(),
                        "SESSION_REQUIRED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
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
}
