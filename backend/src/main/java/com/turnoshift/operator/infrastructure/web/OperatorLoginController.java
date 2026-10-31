package com.turnoshift.operator.infrastructure.web;

import com.turnoshift.identity.domain.SessionSecurityConstants;
import com.turnoshift.operator.application.AuthenticateOperator;
import com.turnoshift.operator.domain.OperatorAuthenticationOutcome;
import com.turnoshift.operator.domain.OperatorAuthenticationResult;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.OperatorLoginInputValidator;
import com.turnoshift.operator.domain.OperatorValidationError;
import com.turnoshift.operator.domain.PlatformOperatorIdentity;
import com.turnoshift.operator.infrastructure.security.OperatorLoginRateLimiter;
import com.turnoshift.security.SecurityProblemHandlers.ProblemDetailDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Controlador para autenticación exclusiva de operadores de plataforma (POST /api/v1/operator/auth/login).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Limitación de tasa por IP exclusiva antes de evaluar credenciales o ejecutar Argon2.</li>
 *   <li>Validación estructural de email y password sin requerir código de organización.</li>
 *   <li>Consumo del caso de uso {@link AuthenticateOperator} sin duplicar lógica de dominio.</li>
 *   <li>Prevención de fijación de sesión mediante rotación (changeSessionId) tras éxito legítimo.</li>
 *   <li>Persistencia explícita del {@link SecurityContext} con autoridad única {@code ROLE_PLATFORM_OPERATOR}.</li>
 *   <li>Mapeo seguro de errores JDBC a 503 SERVICE_UNAVAILABLE sin filtrar SQL ni infraestructura.</li>
 *   <li>Respuestas indistinguibles de 401 AUTHENTICATION_FAILED ante credenciales erróneas o cuenta inactiva.</li>
 *   <li>No creación de sesiones ante peticiones fallidas o rechazadas por tasa.</li>
 * </ul>
 */
@RestController
public class OperatorLoginController {

    private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
            MediaType.parseMediaType("application/problem+json;charset=UTF-8");

    private final AuthenticateOperator authenticateOperator;
    private final OperatorLoginRateLimiter rateLimiter;
    private final SecurityContextRepository securityContextRepository;
    private final Clock clock;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public OperatorLoginController(
            AuthenticateOperator authenticateOperator,
            OperatorLoginRateLimiter rateLimiter,
            SecurityContextRepository securityContextRepository) {
        this(authenticateOperator, rateLimiter, securityContextRepository, Clock.systemUTC());
    }

    @Autowired
    public OperatorLoginController(
            AuthenticateOperator authenticateOperator,
            OperatorLoginRateLimiter rateLimiter,
            SecurityContextRepository securityContextRepository,
            Clock clock) {
        this.authenticateOperator = Objects.requireNonNull(authenticateOperator, "authenticateOperator no puede ser nulo");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter no puede ser nulo");
        this.securityContextRepository = Objects.requireNonNull(securityContextRepository, "securityContextRepository no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    @PostMapping("/api/v1/operator/auth/login")
    public ResponseEntity<?> login(
            @RequestBody(required = false) OperatorLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String clientIp = resolveClientIp(httpRequest);

        // 1. Rate limiting por IP antes de invocar hashing o persistencia
        OperatorLoginRateLimiter.RateLimitResult rateResult = rateLimiter.tryAcquire(clientIp);
        if (rateResult instanceof OperatorLoginRateLimiter.RateLimitResult.Rejected rejected) {
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

        // 2. Validación de entrada estructural (sin organizationCode)
        List<OperatorValidationError> validationErrors = OperatorLoginInputValidator.validate(
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

        // 3. Ejecución del caso de uso de autenticación de operador
        char[] passwordChars = request.password().toCharArray();
        OperatorAuthenticationOutcome outcome;
        try {
            outcome = authenticateOperator.execute(request.email(), passwordChars);
        } catch (OperatorDataAccessException e) {
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
        } finally {
            Arrays.fill(passwordChars, '\0');
        }

        // 4. Verificación del resultado
        if (outcome.result() != OperatorAuthenticationResult.SUCCESS) {
            // INVALID_CREDENTIALS o cuenta inactiva: fallo indistinguible
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

        // 5. Autenticación exitosa
        PlatformOperatorIdentity identity = outcome.identity();

        // 5a. Prevención de fijación de sesión
        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        session.setAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE, clock.millis());

        // 5b. Construcción y persistencia explícita del SecurityContext
        Authentication auth = new UsernamePasswordAuthenticationToken(
                identity,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_OPERATOR"))
        );

        SecurityContext securityContext = securityContextHolderStrategy.createEmptyContext();
        securityContext.setAuthentication(auth);
        securityContextHolderStrategy.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);

        // 5c. Restablecer límite de tasa para la IP tras éxito legítimo
        rateLimiter.reset(clientIp);

        // 5d. Respuesta de sesión de operador
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(OperatorSessionResponse.from(identity));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "UNKNOWN";
    }

    public record FieldErrorDto(String field, String code, String message) {}
}
