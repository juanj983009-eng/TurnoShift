package com.turnoshift.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Manejadores de errores de seguridad que producen respuestas Problem Details
 * (RFC 9457) con Content-Type application/problem+json.
 *
 * <p>Sin redirecciones HTML. Sin excepciones expuestas en el cuerpo.
 * Los códigos de aplicación son estables y diferenciados.
 *
 * <p>Implementa dos contratos:
 * <ul>
 *   <li>{@link AuthenticationEntryPoint} — sesión ausente o no autenticada → 401 SESSION_REQUIRED.</li>
 *   <li>{@link AccessDeniedHandler} — acceso denegado → 403 ACCESS_DENIED o 403 CSRF_INVALID.</li>
 * </ul>
 *
 * <p>CSRF: {@link CsrfException} proviene de {@code CsrfFilter} antes de llegar al
 * {@code ExceptionTranslationFilter}. Para capturarla correctamente se usa
 * {@code accessDeniedHandler} que comprueba {@code instanceof CsrfException}.
 *
 * <p>Jackson: inyecta exclusivamente el {@link ObjectMapper} gestionado por Spring Boot.
 */
public final class SecurityProblemHandlers {

    private SecurityProblemHandlers() {}

    /**
     * DTO para serialización RFC 9457 Problem Details.
     */
    public record ProblemDetailDto(
            String type,
            String title,
            int status,
            String detail,
            String instance,
            String code,
            String requestId,
            List<Object> errors
    ) {}

    // =========================================================================
    // 401 — Sesión requerida
    // =========================================================================

    public static class SessionRequiredEntryPoint implements AuthenticationEntryPoint {

        private final ObjectMapper objectMapper;

        public SessionRequiredEntryPoint(ObjectMapper objectMapper) {
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        }

        @Override
        public void commence(HttpServletRequest request,
                             HttpServletResponse response,
                             AuthenticationException authException) throws IOException {
            writeProblemDetail(response, objectMapper, HttpStatus.UNAUTHORIZED,
                    "/problems/session-required",
                    "Sesión requerida",
                    "No existe sesión activa. Inicia sesión para continuar.",
                    "SESSION_REQUIRED",
                    request.getRequestURI());
        }
    }

    // =========================================================================
    // 403 — Acceso denegado o CSRF inválido
    // =========================================================================

    public static class TurnoShiftAccessDeniedHandler implements AccessDeniedHandler {

        private final ObjectMapper objectMapper;

        public TurnoShiftAccessDeniedHandler(ObjectMapper objectMapper) {
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        }

        @Override
        public void handle(HttpServletRequest request,
                           HttpServletResponse response,
                           AccessDeniedException accessDeniedException) throws IOException {
            // CsrfException es subtipo de AccessDeniedException y es lanzada por CsrfFilter.
            if (accessDeniedException instanceof CsrfException) {
                writeProblemDetail(response, objectMapper, HttpStatus.FORBIDDEN,
                        "/problems/csrf-invalid",
                        "Protección CSRF inválida",
                        "El token CSRF está ausente o ha vencido.",
                        "CSRF_INVALID",
                        request.getRequestURI());
            } else {
                writeProblemDetail(response, objectMapper, HttpStatus.FORBIDDEN,
                        "/problems/access-denied",
                        "Acceso denegado",
                        "No tienes permiso para realizar esta acción.",
                        "ACCESS_DENIED",
                        request.getRequestURI());
            }
        }
    }

    // =========================================================================
    // Utilidad de serialización Problem Details (RFC 9457)
    // =========================================================================

    public static void writeProblemDetail(HttpServletResponse response,
                                          ObjectMapper objectMapper,
                                          HttpStatus status,
                                          String type,
                                          String title,
                                          String detail,
                                          String code,
                                          String instance) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        String requestId = UUID.randomUUID().toString();
        ProblemDetailDto body = new ProblemDetailDto(
                type,
                title,
                status.value(),
                detail,
                instance,
                code,
                requestId,
                List.of()
        );

        try {
            objectMapper.writeValue(response.getWriter(), body);
        } catch (JacksonException e) {
            throw new IOException("Error serializando Problem Details", e);
        }
    }
}
