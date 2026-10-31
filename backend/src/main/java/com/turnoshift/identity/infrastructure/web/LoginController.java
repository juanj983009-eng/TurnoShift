package com.turnoshift.identity.infrastructure.web;

import com.turnoshift.identity.application.AuthenticateUser;
import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.identity.domain.AuthenticationDataAccessException;
import com.turnoshift.identity.domain.AuthenticationOutcome;
import com.turnoshift.identity.domain.AuthenticationResult;
import com.turnoshift.identity.domain.LoginInputValidator;
import com.turnoshift.identity.domain.LoginValidationError;
import com.turnoshift.identity.infrastructure.security.LoginRateLimiter;
import com.turnoshift.security.SecurityProblemHandlers.ProblemDetailDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Controlador para autenticación empresarial multi-tenant (POST /api/v1/auth/login).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Limitación de tasa (rate limiting) por IP en memoria antes de evaluar credenciales.</li>
 *   <li>Validación estructural de entrada con respuestas RFC 9457 Problem Details (400).</li>
 *   <li>Integración con {@link AuthenticateUser} sin duplicar lógica de negocio ni coste de hashing.</li>
 *   <li>Prevención de fijación de sesión mediante rotación del ID de sesión (changeSessionId) en login exitoso.</li>
 *   <li>Persistencia explícita del {@link SecurityContext} autenticado en {@link SecurityContextRepository}.</li>
 *   <li>Mapeo de fallos técnicos de base de datos a 503 SERVICE_UNAVAILABLE sin filtrar SQL ni secretos.</li>
 *   <li>Respuestas indistinguibles para fallos de credenciales (401 AUTHENTICATION_FAILED) previniendo enumeración.</li>
 *   <li>No creación de sesión ante autenticación fallida o rechazada por tasa.</li>
 * </ul>
 */
@RestController
public class LoginController {

    private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
            MediaType.parseMediaType("application/problem+json;charset=UTF-8");

    private final AuthenticateUser authenticateUser;
    private final LoginRateLimiter rateLimiter;
    private final SecurityContextRepository securityContextRepository;
    private final java.time.Clock clock;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public LoginController(
            AuthenticateUser authenticateUser,
            LoginRateLimiter rateLimiter,
            SecurityContextRepository securityContextRepository) {
        this(authenticateUser, rateLimiter, securityContextRepository, java.time.Clock.systemUTC());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LoginController(
            AuthenticateUser authenticateUser,
            LoginRateLimiter rateLimiter,
            SecurityContextRepository securityContextRepository,
            java.time.Clock clock) {
        this.authenticateUser = Objects.requireNonNull(authenticateUser, "authenticateUser no puede ser nulo");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter no puede ser nulo");
        this.securityContextRepository = Objects.requireNonNull(securityContextRepository, "securityContextRepository no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<?> login(
            @RequestBody(required = false) LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String clientIp = resolveClientIp(httpRequest);

        // 1. Rate limiting por IP antes de invocar AuthenticateUser
        LoginRateLimiter.RateLimitResult rateResult = rateLimiter.tryAcquire(clientIp);
        if (rateResult instanceof LoginRateLimiter.RateLimitResult.Rejected rejected) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(rejected.retryAfterSeconds()))
                    .header("Cache-Control", "no-store")
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/rate-limited",
                            "Límite de solicitudes alcanzado",
                            HttpStatus.TOO_MANY_REQUESTS.value(),
                            "Demasiados intentos. Espera antes de volver a intentarlo.",
                            httpRequest.getRequestURI(),
                            "RATE_LIMITED",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        }

        // 2. Validación de entrada estructural (reutiliza LoginInputValidator compartido)
        List<LoginValidationError> validationErrors = LoginInputValidator.validate(
                request != null ? request.organizationCode() : null,
                request != null ? request.email() : null,
                request != null ? request.password() : null
        );
        if (!validationErrors.isEmpty()) {
            List<Object> errorDtos = validationErrors.stream()
                    .map(e -> (Object) new FieldErrorDto(e.field(), e.code(), e.message()))
                    .toList();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("Cache-Control", "no-store")
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/validation-failed",
                            "Error de validación",
                            HttpStatus.BAD_REQUEST.value(),
                            "La petición contiene campos inválidos.",
                            httpRequest.getRequestURI(),
                            "VALIDATION_FAILED",
                            UUID.randomUUID().toString(),
                            errorDtos
                    ));
        }

        // 3. Ejecución del caso de uso de autenticación
        AuthenticationOutcome outcome;
        try {
            outcome = authenticateUser.execute(
                    request.organizationCode(),
                    request.email(),
                    request.password()
            );
        } catch (AuthenticationDataAccessException e) {
            // Error técnico de persistencia (caída de BD, timeout, red) -> 503 sin filtrar detalles
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Cache-Control", "no-store")
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/service-unavailable",
                            "Servicio no disponible",
                            HttpStatus.SERVICE_UNAVAILABLE.value(),
                            "El servicio no está disponible temporalmente. Inténtalo más tarde.",
                            httpRequest.getRequestURI(),
                            "SERVICE_UNAVAILABLE",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        }

        // 4. Tratamiento según resultado
        if (outcome.result() == AuthenticationResult.VALIDATION_FAILED) {
            List<Object> errorDtos = outcome.validationErrors().stream()
                    .map(e -> (Object) new FieldErrorDto(e.field(), e.code(), e.message()))
                    .toList();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("Cache-Control", "no-store")
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/validation-failed",
                            "Error de validación",
                            HttpStatus.BAD_REQUEST.value(),
                            "La petición contiene campos inválidos.",
                            httpRequest.getRequestURI(),
                            "VALIDATION_FAILED",
                            UUID.randomUUID().toString(),
                            errorDtos
                    ));
        }

        if (outcome.result() == AuthenticationResult.ORGANIZATION_ACCESS_DENIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .header("Cache-Control", "no-store")
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/access-denied",
                            "Acceso denegado",
                            HttpStatus.FORBIDDEN.value(),
                            "No tienes permiso para realizar esta acción.",
                            httpRequest.getRequestURI(),
                            "ACCESS_DENIED",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        }

        if (outcome.result() != AuthenticationResult.SUCCESS) {
            // INVALID_CREDENTIALS: fallo genérico indistinguible
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("Cache-Control", "no-store")
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .body(new ProblemDetailDto(
                            "/problems/authentication-failed",
                            "Autenticación fallida",
                            HttpStatus.UNAUTHORIZED.value(),
                            "Las credenciales son incorrectas o la cuenta no puede iniciar sesión.",
                            httpRequest.getRequestURI(),
                            "AUTHENTICATION_FAILED",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        }

        // 5. Autenticación exitosa (SUCCESS)
        AuthenticatedIdentity identity = outcome.identity();

        // 5a. Prevención de fijación de sesión: asegurar sesión y rotar su identificador
        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        session.setAttribute(
                com.turnoshift.identity.domain.SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE,
                clock.millis()
        );

        // 5b. Construcción y persistencia explícita del SecurityContext
        Authentication auth = new UsernamePasswordAuthenticationToken(
                identity,
                null, // Prohibido almacenar contraseñas o hashes en el contexto de seguridad
                List.of(new SimpleGrantedAuthority("ROLE_" + identity.role()))
        );

        SecurityContext securityContext = securityContextHolderStrategy.createEmptyContext();
        securityContext.setAuthentication(auth);
        securityContextHolderStrategy.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);

        // 5c. Restablecer límite de tasa para la IP tras éxito legítimo
        rateLimiter.reset(clientIp);

        // 5d. Respuesta de sesión con landingPage según rol (openapi.yaml y Nota 08)
        SessionResponse responseBody = SessionResponse.from(identity);

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "UNKNOWN";
    }
}
