package com.turnoshift.organization.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.organization.application.GetOrganizationConfigurationUseCase;
import com.turnoshift.organization.application.UpdateActiveSiteNameUseCase;
import com.turnoshift.organization.application.UpdateOrganizationDisplayNameUseCase;
import com.turnoshift.organization.domain.ActiveSiteNotFoundException;
import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.InvalidOrganizationDataException;
import com.turnoshift.organization.domain.OrganizationConfiguration;
import com.turnoshift.organization.domain.OrganizationNotFoundException;
import com.turnoshift.organization.domain.OrganizationOptimisticLockException;
import com.turnoshift.organization.domain.OrganizationValidation;
import com.turnoshift.organization.domain.OrganizationView;
import com.turnoshift.organization.domain.SiteOptimisticLockException;
import com.turnoshift.organization.infrastructure.OrganizationConfigurationDataAccessException;
import com.turnoshift.security.SecurityProblemHandlers.ProblemDetailDto;
import com.turnoshift.security.idempotency.BusinessFingerprintCalculator;
import com.turnoshift.security.idempotency.BusinessIdempotencyDataAccessException;
import com.turnoshift.security.idempotency.BusinessIdempotencyResult;
import com.turnoshift.security.idempotency.BusinessIdempotentOperationExecutor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Controlador REST seguro e idempotente para la configuración de organización y sede única.
 * <p>
 * Los identificadores de organización y usuario actor se derivan exclusivamente de
 * {@link AuthenticatedIdentity} en el {@code SecurityContext}. Ninguna petición puede sobreescribir
 * estos datos desde cuerpo, parámetros de consulta ni rutas.
 */
@RestController
@Validated
public class OrganizationConfigurationController {

    private static final MediaType PROBLEM_JSON_MEDIA_TYPE = MediaType.parseMediaType("application/problem+json");

    private final GetOrganizationConfigurationUseCase getConfigurationUseCase;
    private final UpdateOrganizationDisplayNameUseCase updateDisplayNameUseCase;
    private final UpdateActiveSiteNameUseCase updateSiteNameUseCase;
    private final BusinessIdempotentOperationExecutor executor;
    private final ObjectMapper objectMapper;
    private final SecurityContextHolderStrategy securityContextHolderStrategy;

    public OrganizationConfigurationController(
            GetOrganizationConfigurationUseCase getConfigurationUseCase,
            UpdateOrganizationDisplayNameUseCase updateDisplayNameUseCase,
            UpdateActiveSiteNameUseCase updateSiteNameUseCase,
            BusinessIdempotentOperationExecutor executor,
            ObjectMapper objectMapper
    ) {
        this.getConfigurationUseCase = Objects.requireNonNull(getConfigurationUseCase, "getConfigurationUseCase no puede ser nulo");
        this.updateDisplayNameUseCase = Objects.requireNonNull(updateDisplayNameUseCase, "updateDisplayNameUseCase no puede ser nulo");
        this.updateSiteNameUseCase = Objects.requireNonNull(updateSiteNameUseCase, "updateSiteNameUseCase no puede ser nulo");
        this.executor = Objects.requireNonNull(executor, "executor no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        this.securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    }

    // =========================================================================
    // 1. GET /api/v1/organization/configuration
    // =========================================================================

    @GetMapping("/api/v1/organization/configuration")
    public ResponseEntity<?> getConfiguration(HttpServletRequest httpRequest) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        try {
            OrganizationConfiguration config = getConfigurationUseCase.execute(identity.organizationId());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(OrganizationConfigurationResponse.from(config));
        } catch (OrganizationNotFoundException | ActiveSiteNotFoundException e) {
            return resourceNotFoundResponse(httpRequest);
        }
    }

    // =========================================================================
    // 2. PATCH /api/v1/organization/configuration/display-name
    // =========================================================================

    @PatchMapping("/api/v1/organization/configuration/display-name")
    public ResponseEntity<?> updateDisplayName(
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

        UpdateOrganizationDisplayNameRequest req;
        try {
            req = objectMapper.readValue(rawBody, UpdateOrganizationDisplayNameRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        if (req.expectedLockVersion() == null || req.expectedLockVersion() < 0) {
            return validationFailedResponse(httpRequest, "El campo expectedLockVersion es obligatorio y debe ser mayor o igual a 0.");
        }

        String normalizedDisplayName;
        try {
            normalizedDisplayName = OrganizationValidation.validateAndNormalizeDisplayName(req.displayName());
        } catch (InvalidOrganizationDataException e) {
            return validationFailedResponse(httpRequest, "El nombre de la organización es inválido.");
        }

        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, normalizedDisplayName);
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(req.expectedLockVersion()));
        String canonicalPayload = sb.toString();

        OrganizationIdempotentCommand command = new OrganizationIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "PATCH",
                "/api/v1/organization/configuration/display-name",
                canonicalPayload,
                OrganizationIdempotentCommand.TargetType.ORGANIZATION,
                objectMapper
        );

        Supplier<OrganizationExecutionResult> effect = () -> {
            try {
                OrganizationView updated = updateDisplayNameUseCase.execute(
                        identity.organizationId(),
                        normalizedDisplayName,
                        req.expectedLockVersion()
                );
                return new OrganizationExecutionResult.OrganizationSuccess(updated);
            } catch (OrganizationNotFoundException e) {
                return new OrganizationExecutionResult.ResourceNotFound();
            } catch (OrganizationOptimisticLockException e) {
                return new OrganizationExecutionResult.ConcurrentModification();
            }
        };

        BusinessIdempotencyResult<OrganizationExecutionResult> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, httpRequest);
    }

    // =========================================================================
    // 3. PATCH /api/v1/organization/configuration/active-site/name
    // =========================================================================

    @PatchMapping("/api/v1/organization/configuration/active-site/name")
    public ResponseEntity<?> updateActiveSiteName(
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

        UpdateActiveSiteNameRequest req;
        try {
            req = objectMapper.readValue(rawBody, UpdateActiveSiteNameRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        if (req.expectedLockVersion() == null || req.expectedLockVersion() < 0) {
            return validationFailedResponse(httpRequest, "El campo expectedLockVersion es obligatorio y debe ser mayor o igual a 0.");
        }

        String normalizedSiteName;
        try {
            normalizedSiteName = OrganizationValidation.validateAndNormalizeSiteName(req.name());
        } catch (InvalidOrganizationDataException e) {
            return validationFailedResponse(httpRequest, "El nombre de la sede es inválido.");
        }

        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, normalizedSiteName);
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(req.expectedLockVersion()));
        String canonicalPayload = sb.toString();

        OrganizationIdempotentCommand command = new OrganizationIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "PATCH",
                "/api/v1/organization/configuration/active-site/name",
                canonicalPayload,
                OrganizationIdempotentCommand.TargetType.SITE,
                objectMapper
        );

        Supplier<OrganizationExecutionResult> effect = () -> {
            try {
                ActiveSiteView updated = updateSiteNameUseCase.execute(
                        identity.organizationId(),
                        normalizedSiteName,
                        req.expectedLockVersion()
                );
                return new OrganizationExecutionResult.SiteSuccess(updated);
            } catch (ActiveSiteNotFoundException e) {
                return new OrganizationExecutionResult.ResourceNotFound();
            } catch (SiteOptimisticLockException e) {
                return new OrganizationExecutionResult.ConcurrentModification();
            }
        };

        BusinessIdempotencyResult<OrganizationExecutionResult> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, httpRequest);
    }

    // =========================================================================
    // Manejo de excepciones de persistencia e infraestructura
    // =========================================================================

    @ExceptionHandler({OrganizationConfigurationDataAccessException.class, BusinessIdempotencyDataAccessException.class})
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

    @ExceptionHandler(InvalidOrganizationDataException.class)
    public ResponseEntity<ProblemDetailDto> handleInvalidOrganizationDataException(HttpServletRequest request, InvalidOrganizationDataException ex) {
        return validationFailedResponse(request, "Los datos proporcionados no cumplen con el formato requerido.");
    }

    // =========================================================================
    // Métodos auxiliares de renderizado y validación
    // =========================================================================

    private Optional<AuthenticatedIdentity> getAuthenticatedIdentity() {
        Authentication auth = securityContextHolderStrategy.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken) && auth.getPrincipal() instanceof AuthenticatedIdentity identity) {
            return Optional.of(identity);
        }
        return Optional.empty();
    }

    private ResponseEntity<?> renderIdempotencyResult(
            BusinessIdempotencyResult<OrganizationExecutionResult> result,
            HttpServletRequest httpRequest
    ) {
        if (result instanceof BusinessIdempotencyResult.Proceed(var execResult)) {
            return renderExecutionResult(execResult, false, httpRequest);
        } else if (result instanceof BusinessIdempotencyResult.Replay(var execResult)) {
            return renderExecutionResult(execResult, true, httpRequest);
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
            OrganizationExecutionResult execResult,
            boolean isReplay,
            HttpServletRequest httpRequest
    ) {
        int statusCode = (execResult instanceof OrganizationExecutionResult.OrganizationSuccess ||
                          execResult instanceof OrganizationExecutionResult.SiteSuccess)
                ? HttpStatus.OK.value()
                : (execResult instanceof OrganizationExecutionResult.ResourceNotFound
                ? HttpStatus.NOT_FOUND.value()
                : HttpStatus.CONFLICT.value());

        var builder = ResponseEntity.status(statusCode)
                .header(HttpHeaders.CACHE_CONTROL, "no-store");

        if (isReplay) {
            builder.header("Idempotency-Replayed", "true");
        }

        if (execResult instanceof OrganizationExecutionResult.OrganizationSuccess(var org)) {
            return builder.contentType(MediaType.APPLICATION_JSON).body(org);
        } else if (execResult instanceof OrganizationExecutionResult.SiteSuccess(var site)) {
            return builder.contentType(MediaType.APPLICATION_JSON).body(site);
        } else if (execResult instanceof OrganizationExecutionResult.ResourceNotFound) {
            return builder.contentType(PROBLEM_JSON_MEDIA_TYPE).body(new ProblemDetailDto(
                    "/problems/resource-not-found",
                    "Recurso no encontrado",
                    HttpStatus.NOT_FOUND.value(),
                    "El recurso solicitado no existe o no pertenece a la organización.",
                    httpRequest.getRequestURI(),
                    "RESOURCE_NOT_FOUND",
                    UUID.randomUUID().toString(),
                    List.of()
            ));
        } else if (execResult instanceof OrganizationExecutionResult.ConcurrentModification) {
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

    private ResponseEntity<ProblemDetailDto> resourceNotFoundResponse(HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(new ProblemDetailDto(
                        "/problems/resource-not-found",
                        "Recurso no encontrado",
                        HttpStatus.NOT_FOUND.value(),
                        "El recurso solicitado no existe o no pertenece a la organización.",
                        httpRequest.getRequestURI(),
                        "RESOURCE_NOT_FOUND",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }
}
