package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.security.SecurityProblemHandlers.ProblemDetailDto;
import com.turnoshift.security.idempotency.BusinessIdempotencyDataAccessException;
import com.turnoshift.security.idempotency.BusinessIdempotencyResult;
import com.turnoshift.security.idempotency.BusinessIdempotentOperationExecutor;
import com.turnoshift.unavailability.application.CancelUnavailabilityUseCase;
import com.turnoshift.unavailability.application.CreateUnavailabilityUseCase;
import com.turnoshift.unavailability.application.GetUnavailabilityUseCase;
import com.turnoshift.unavailability.application.ListEmployeeUnavailabilitiesUseCase;
import com.turnoshift.unavailability.application.ListOrganizationUnavailabilitiesUseCase;
import com.turnoshift.unavailability.application.UpdateUnavailabilityUseCase;
import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.OrganizationTimezonePort;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import com.turnoshift.unavailability.domain.exceptions.EmployeeNotFoundException;
import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;
import com.turnoshift.unavailability.domain.exceptions.PastIntervalModificationProhibitedException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityAlreadyCancelledException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityConcurrentModificationException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityNotFoundException;
import com.turnoshift.unavailability.infrastructure.persistence.UnavailabilityDataAccessException;
import com.turnoshift.unavailability.infrastructure.web.dto.CancelUnavailabilityRequest;
import com.turnoshift.unavailability.infrastructure.web.dto.CreateUnavailabilityRequest;
import com.turnoshift.unavailability.infrastructure.web.dto.EmployeeUnavailabilityResponse;
import com.turnoshift.unavailability.infrastructure.web.dto.UnavailabilityListResponse;
import com.turnoshift.unavailability.infrastructure.web.dto.UpdateUnavailabilityRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Controlador REST para gestión de indisponibilidades de colaboradores (HU-08 / I2.4).
 *
 * <p>Aplica aislamiento multi-tenant estricto resolviendo la organización exclusivamente
 * de la identidad de la sesión autenticada en el SecurityContext.
 */
@RestController
public class UnavailabilityController {

    private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
            MediaType.parseMediaType("application/problem+json;charset=UTF-8");

    private final GetUnavailabilityUseCase getUnavailabilityUseCase;
    private final ListEmployeeUnavailabilitiesUseCase listEmployeeUnavailabilitiesUseCase;
    private final ListOrganizationUnavailabilitiesUseCase listOrganizationUnavailabilitiesUseCase;
    private final CreateUnavailabilityUseCase createUnavailabilityUseCase;
    private final UpdateUnavailabilityUseCase updateUnavailabilityUseCase;
    private final CancelUnavailabilityUseCase cancelUnavailabilityUseCase;
    private final OrganizationTimezonePort organizationTimezonePort;
    private final BusinessIdempotentOperationExecutor executor;
    private final ObjectMapper objectMapper;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public UnavailabilityController(
            GetUnavailabilityUseCase getUnavailabilityUseCase,
            ListEmployeeUnavailabilitiesUseCase listEmployeeUnavailabilitiesUseCase,
            ListOrganizationUnavailabilitiesUseCase listOrganizationUnavailabilitiesUseCase,
            CreateUnavailabilityUseCase createUnavailabilityUseCase,
            UpdateUnavailabilityUseCase updateUnavailabilityUseCase,
            CancelUnavailabilityUseCase cancelUnavailabilityUseCase,
            OrganizationTimezonePort organizationTimezonePort,
            BusinessIdempotentOperationExecutor executor,
            ObjectMapper objectMapper
    ) {
        this.getUnavailabilityUseCase = Objects.requireNonNull(getUnavailabilityUseCase, "getUnavailabilityUseCase no puede ser nulo");
        this.listEmployeeUnavailabilitiesUseCase = Objects.requireNonNull(listEmployeeUnavailabilitiesUseCase, "listEmployeeUnavailabilitiesUseCase no puede ser nulo");
        this.listOrganizationUnavailabilitiesUseCase = Objects.requireNonNull(listOrganizationUnavailabilitiesUseCase, "listOrganizationUnavailabilitiesUseCase no puede ser nulo");
        this.createUnavailabilityUseCase = Objects.requireNonNull(createUnavailabilityUseCase, "createUnavailabilityUseCase no puede ser nulo");
        this.updateUnavailabilityUseCase = Objects.requireNonNull(updateUnavailabilityUseCase, "updateUnavailabilityUseCase no puede ser nulo");
        this.cancelUnavailabilityUseCase = Objects.requireNonNull(cancelUnavailabilityUseCase, "cancelUnavailabilityUseCase no puede ser nulo");
        this.organizationTimezonePort = Objects.requireNonNull(organizationTimezonePort, "organizationTimezonePort no puede ser nulo");
        this.executor = Objects.requireNonNull(executor, "executor no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    // =========================================================================
    // 1. GET /api/v1/employees/{employeeId}/unavailabilities
    // =========================================================================

    @GetMapping("/api/v1/employees/{employeeId}/unavailabilities")
    public ResponseEntity<?> getEmployeeUnavailabilities(
            @PathVariable("employeeId") String employeeIdParam,
            @RequestParam(name = "from", required = false) String fromParam,
            @RequestParam(name = "to", required = false) String toParam,
            @RequestParam(name = "includeCancelled", required = false) String includeCancelledParam,
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

        Boolean includeCancelled = parseStrictBoolean(includeCancelledParam);
        if (includeCancelled == null) {
            return validationFailedResponse(httpRequest, "El parámetro 'includeCancelled' debe ser 'true' o 'false'.");
        }

        boolean hasFrom = fromParam != null && !fromParam.isBlank();
        boolean hasTo = toParam != null && !toParam.isBlank();

        if (hasFrom != hasTo) {
            return validationFailedResponse(httpRequest, "Los parámetros 'from' y 'to' deben proporcionarse juntos.");
        }

        List<EmployeeUnavailability> list;
        try {
            if (hasFrom) {
                Instant from = parseInstant(fromParam);
                Instant to = parseInstant(toParam);
                if (from == null || to == null) {
                    return validationFailedResponse(httpRequest, "Los parámetros 'from' y 'to' deben ser instantes ISO-8601 UTC válidos.");
                }
                if (!from.isBefore(to)) {
                    return validationFailedResponse(httpRequest, "El parámetro 'from' debe ser anterior a 'to'.");
                }
                list = listEmployeeUnavailabilitiesUseCase.execute(
                        identity.organizationId(), employeeId, from, to, includeCancelled
                );
            } else {
                list = listEmployeeUnavailabilitiesUseCase.execute(
                        identity.organizationId(), employeeId, includeCancelled
                );
            }
        } catch (EmployeeNotFoundException e) {
            return employeeNotFoundResponse(httpRequest, "El colaborador solicitado no existe o no pertenece a la organización.");
        } catch (InvalidUnavailabilityDataException e) {
            return validationFailedResponse(httpRequest, e.getMessage());
        }

        List<EmployeeUnavailabilityResponse> items = list.stream()
                .map(EmployeeUnavailabilityResponse::from)
                .toList();

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UnavailabilityListResponse(items));
    }

    // =========================================================================
    // 2. GET /api/v1/unavailabilities
    // =========================================================================

    @GetMapping("/api/v1/unavailabilities")
    public ResponseEntity<?> getOrganizationUnavailabilities(
            @RequestParam(name = "from", required = false) String fromParam,
            @RequestParam(name = "to", required = false) String toParam,
            @RequestParam(name = "includeCancelled", required = false) String includeCancelledParam,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        Boolean includeCancelled = parseStrictBoolean(includeCancelledParam);
        if (includeCancelled == null) {
            return validationFailedResponse(httpRequest, "El parámetro 'includeCancelled' debe ser 'true' o 'false'.");
        }

        if (fromParam == null || fromParam.isBlank() || toParam == null || toParam.isBlank()) {
            return validationFailedResponse(httpRequest, "Los parámetros 'from' y 'to' son obligatorios para consultar la ventana organizacional.");
        }

        Instant from = parseInstant(fromParam);
        Instant to = parseInstant(toParam);
        if (from == null || to == null) {
            return validationFailedResponse(httpRequest, "Los parámetros 'from' y 'to' deben ser instantes ISO-8601 UTC válidos.");
        }

        if (!from.isBefore(to)) {
            return validationFailedResponse(httpRequest, "El parámetro 'from' debe ser anterior a 'to'.");
        }

        if (Duration.between(from, to).compareTo(Duration.ofDays(92)) > 0) {
            return validationFailedResponse(httpRequest, "El rango entre 'from' y 'to' no puede superar los 92 días.");
        }

        List<EmployeeUnavailability> list;
        try {
            list = listOrganizationUnavailabilitiesUseCase.execute(
                    identity.organizationId(), from, to, includeCancelled
            );
        } catch (InvalidUnavailabilityDataException e) {
            return validationFailedResponse(httpRequest, e.getMessage());
        }

        List<EmployeeUnavailabilityResponse> items = list.stream()
                .map(EmployeeUnavailabilityResponse::from)
                .toList();

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UnavailabilityListResponse(items));
    }

    // =========================================================================
    // 3. GET /api/v1/unavailabilities/{unavailabilityId}
    // =========================================================================

    @GetMapping("/api/v1/unavailabilities/{unavailabilityId}")
    public ResponseEntity<?> getUnavailability(
            @PathVariable("unavailabilityId") String unavailabilityIdParam,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID unavailabilityId = parseUuid(unavailabilityIdParam);
        if (unavailabilityId == null) {
            return validationFailedResponse(httpRequest, "El identificador de la indisponibilidad debe ser un UUID válido.");
        }

        try {
            EmployeeUnavailability unavailability = getUnavailabilityUseCase.execute(
                    identity.organizationId(), unavailabilityId
            );
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(EmployeeUnavailabilityResponse.from(unavailability));
        } catch (UnavailabilityNotFoundException e) {
            return unavailabilityNotFoundResponse(httpRequest, "La indisponibilidad solicitada no existe o no pertenece a la organización.");
        }
    }

    // =========================================================================
    // 4. POST /api/v1/employees/{employeeId}/unavailabilities
    // =========================================================================

    @PostMapping("/api/v1/employees/{employeeId}/unavailabilities")
    public ResponseEntity<?> createUnavailability(
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

        UUID idempotencyKey = parseUuid(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la petición es obligatorio.");
        }

        CreateUnavailabilityRequest req;
        try {
            req = objectMapper.readValue(rawBody, CreateUnavailabilityRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        ZoneId zoneId = organizationTimezonePort.resolveZoneId(identity.organizationId());
        TimeInterval interval;
        try {
            interval = UnavailabilityTemporalParser.parse(
                    req.startsAt(), req.endsAt(), req.allDay(), req.startDate(), req.endDate(), zoneId
            );
        } catch (InvalidUnavailabilityDataException e) {
            return validationFailedResponse(httpRequest, e.getMessage());
        }

        UnavailabilityReason reason = parseReasonCode(req.reasonCode());
        if (req.reasonCode() != null && !req.reasonCode().isBlank() && reason == null) {
            return validationFailedResponse(httpRequest, "El motivo '" + req.reasonCode() + "' no pertenece al catálogo permitido.");
        }

        String canonicalPayload = UnavailabilityIdempotentCommand.buildCreateCanonicalPayload(
                identity.organizationId(),
                identity.userId(),
                employeeId,
                interval.startsAt(),
                interval.endsAt(),
                reason != null ? reason.name() : null,
                req.restrictedNote()
        );

        UnavailabilityIdempotentCommand command = new UnavailabilityIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "POST",
                "/api/v1/employees/" + employeeId + "/unavailabilities",
                canonicalPayload,
                true,
                objectMapper
        );

        Supplier<EmployeeUnavailabilityResponse> effect = () -> {
            EmployeeUnavailability created = createUnavailabilityUseCase.execute(
                    identity.organizationId(),
                    employeeId,
                    interval.startsAt(),
                    interval.endsAt(),
                    reason,
                    req.restrictedNote(),
                    identity.userId()
            );
            return EmployeeUnavailabilityResponse.from(created);
        };

        BusinessIdempotencyResult<EmployeeUnavailabilityResponse> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, true, httpRequest);
    }

    // =========================================================================
    // 5. PUT /api/v1/unavailabilities/{unavailabilityId}
    // =========================================================================

    @PutMapping("/api/v1/unavailabilities/{unavailabilityId}")
    public ResponseEntity<?> updateUnavailability(
            @PathVariable("unavailabilityId") String unavailabilityIdParam,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID unavailabilityId = parseUuid(unavailabilityIdParam);
        if (unavailabilityId == null) {
            return validationFailedResponse(httpRequest, "El identificador de la indisponibilidad debe ser un UUID válido.");
        }

        UUID idempotencyKey = parseUuid(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la petición es obligatorio.");
        }

        UpdateUnavailabilityRequest req;
        try {
            req = objectMapper.readValue(rawBody, UpdateUnavailabilityRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        if (req.expectedLockVersion() == null || req.expectedLockVersion() < 0) {
            return validationFailedResponse(httpRequest, "El campo 'expectedLockVersion' es obligatorio y debe ser un entero mayor o igual a 0.");
        }

        ZoneId zoneId = organizationTimezonePort.resolveZoneId(identity.organizationId());
        TimeInterval interval;
        try {
            interval = UnavailabilityTemporalParser.parse(
                    req.startsAt(), req.endsAt(), req.allDay(), req.startDate(), req.endDate(), zoneId
            );
        } catch (InvalidUnavailabilityDataException e) {
            return validationFailedResponse(httpRequest, e.getMessage());
        }

        UnavailabilityReason reason = parseReasonCode(req.reasonCode());
        if (req.reasonCode() != null && !req.reasonCode().isBlank() && reason == null) {
            return validationFailedResponse(httpRequest, "El motivo '" + req.reasonCode() + "' no pertenece al catálogo permitido.");
        }

        String canonicalPayload = UnavailabilityIdempotentCommand.buildUpdateCanonicalPayload(
                identity.organizationId(),
                identity.userId(),
                unavailabilityId,
                interval.startsAt(),
                interval.endsAt(),
                reason != null ? reason.name() : null,
                req.restrictedNote(),
                req.expectedLockVersion()
        );

        UnavailabilityIdempotentCommand command = new UnavailabilityIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "PUT",
                "/api/v1/unavailabilities/" + unavailabilityId,
                canonicalPayload,
                false,
                objectMapper
        );

        Supplier<EmployeeUnavailabilityResponse> effect = () -> {
            EmployeeUnavailability updated = updateUnavailabilityUseCase.execute(
                    identity.organizationId(),
                    unavailabilityId,
                    interval.startsAt(),
                    interval.endsAt(),
                    reason,
                    req.restrictedNote(),
                    req.expectedLockVersion()
            );
            return EmployeeUnavailabilityResponse.from(updated);
        };

        BusinessIdempotencyResult<EmployeeUnavailabilityResponse> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, false, httpRequest);
    }

    // =========================================================================
    // 6. POST /api/v1/unavailabilities/{unavailabilityId}/cancel
    // =========================================================================

    @PostMapping("/api/v1/unavailabilities/{unavailabilityId}/cancel")
    public ResponseEntity<?> cancelUnavailability(
            @PathVariable("unavailabilityId") String unavailabilityIdParam,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID unavailabilityId = parseUuid(unavailabilityIdParam);
        if (unavailabilityId == null) {
            return validationFailedResponse(httpRequest, "El identificador de la indisponibilidad debe ser un UUID válido.");
        }

        UUID idempotencyKey = parseUuid(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la petición es obligatorio.");
        }

        CancelUnavailabilityRequest req;
        try {
            req = objectMapper.readValue(rawBody, CancelUnavailabilityRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        if (req.expectedLockVersion() == null || req.expectedLockVersion() < 0) {
            return validationFailedResponse(httpRequest, "El campo 'expectedLockVersion' es obligatorio y debe ser un entero mayor o igual a 0.");
        }

        String canonicalPayload = UnavailabilityIdempotentCommand.buildCancelCanonicalPayload(
                identity.organizationId(),
                identity.userId(),
                unavailabilityId,
                req.expectedLockVersion()
        );

        UnavailabilityIdempotentCommand command = new UnavailabilityIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "POST",
                "/api/v1/unavailabilities/" + unavailabilityId + "/cancel",
                canonicalPayload,
                false,
                objectMapper
        );

        Supplier<EmployeeUnavailabilityResponse> effect = () -> {
            EmployeeUnavailability cancelled = cancelUnavailabilityUseCase.execute(
                    identity.organizationId(),
                    unavailabilityId,
                    identity.userId(),
                    req.expectedLockVersion()
            );
            return EmployeeUnavailabilityResponse.from(cancelled);
        };

        BusinessIdempotencyResult<EmployeeUnavailabilityResponse> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, false, httpRequest);
    }

    // =========================================================================
    // Manejo de excepciones y Problem Details (RFC 9457)
    // =========================================================================

    @ExceptionHandler(PastIntervalModificationProhibitedException.class)
    public ResponseEntity<ProblemDetailDto> handlePastInterval(HttpServletRequest request, PastIntervalModificationProhibitedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/past-interval-modification-prohibited",
                        "Modificación de intervalo pasado o iniciado prohibida",
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage() != null ? ex.getMessage() : "No se puede registrar, modificar ni cancelar una indisponibilidad en un periodo que ya ha iniciado o finalizado.",
                        request.getRequestURI(),
                        "PAST_INTERVAL_MODIFICATION_PROHIBITED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    @ExceptionHandler(UnavailabilityAlreadyCancelledException.class)
    public ResponseEntity<ProblemDetailDto> handleAlreadyCancelled(HttpServletRequest request, UnavailabilityAlreadyCancelledException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/unavailability-already-cancelled",
                        "Indisponibilidad ya cancelada",
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage() != null ? ex.getMessage() : "La indisponibilidad especificada ya se encuentra cancelada.",
                        request.getRequestURI(),
                        "UNAVAILABILITY_ALREADY_CANCELLED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    @ExceptionHandler(UnavailabilityConcurrentModificationException.class)
    public ResponseEntity<ProblemDetailDto> handleConcurrentModification(HttpServletRequest request, UnavailabilityConcurrentModificationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/concurrent-modification",
                        "Conflicto de concurrencia",
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage() != null ? ex.getMessage() : "El recurso fue modificado por otra operación concurrente o la versión esperada no coincide.",
                        request.getRequestURI(),
                        "CONCURRENT_MODIFICATION",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    @ExceptionHandler({UnavailabilityDataAccessException.class, BusinessIdempotencyDataAccessException.class})
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

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ProblemDetailDto> handleEmployeeNotFound(HttpServletRequest request, EmployeeNotFoundException ex) {
        return employeeNotFoundResponse(request, "El colaborador solicitado no existe o no pertenece a la organización.");
    }

    @ExceptionHandler(UnavailabilityNotFoundException.class)
    public ResponseEntity<ProblemDetailDto> handleUnavailabilityNotFound(HttpServletRequest request, UnavailabilityNotFoundException ex) {
        return unavailabilityNotFoundResponse(request, "La indisponibilidad solicitada no existe o no pertenece a la organización.");
    }

    @ExceptionHandler(InvalidUnavailabilityDataException.class)
    public ResponseEntity<ProblemDetailDto> handleInvalidData(HttpServletRequest request, InvalidUnavailabilityDataException ex) {
        return validationFailedResponse(request, ex.getMessage());
    }

    // =========================================================================
    // Respuestas y Auxiliares
    // =========================================================================

    private ResponseEntity<?> renderIdempotencyResult(
            BusinessIdempotencyResult<EmployeeUnavailabilityResponse> result,
            boolean isCreate,
            HttpServletRequest httpRequest
    ) {
        if (result instanceof BusinessIdempotencyResult.Proceed(var resp)) {
            var builder = ResponseEntity.status(isCreate ? HttpStatus.CREATED : HttpStatus.OK)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_JSON);
            if (isCreate) {
                builder.location(URI.create("/api/v1/unavailabilities/" + resp.id()));
            }
            return builder.body(resp);
        } else if (result instanceof BusinessIdempotencyResult.Replay(var resp)) {
            var builder = ResponseEntity.status(isCreate ? HttpStatus.CREATED : HttpStatus.OK)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("Idempotency-Replayed", "true")
                    .contentType(MediaType.APPLICATION_JSON);
            if (isCreate) {
                builder.location(URI.create("/api/v1/unavailabilities/" + resp.id()));
            }
            return builder.body(resp);
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

    private Optional<AuthenticatedIdentity> getAuthenticatedIdentity() {
        Authentication auth = securityContextHolderStrategy.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedIdentity identity) {
            return Optional.of(identity);
        }
        return Optional.empty();
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

    private ResponseEntity<ProblemDetailDto> employeeNotFoundResponse(HttpServletRequest request, String detail) {
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

    private ResponseEntity<ProblemDetailDto> unavailabilityNotFoundResponse(HttpServletRequest request, String detail) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/unavailability-not-found",
                        "Indisponibilidad no encontrada",
                        HttpStatus.NOT_FOUND.value(),
                        detail,
                        request.getRequestURI(),
                        "UNAVAILABILITY_NOT_FOUND",
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

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Boolean parseStrictBoolean(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(trimmed)) {
            return true;
        }
        if ("false".equals(trimmed)) {
            return false;
        }
        return null;
    }

    private UnavailabilityReason parseReasonCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return UnavailabilityReason.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
