package com.turnoshift.security;

import com.turnoshift.identity.application.RevalidateSession;
import com.turnoshift.identity.application.RevalidationOutcome;
import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.identity.domain.SessionSecurityConstants;
import com.turnoshift.operator.application.OperatorRevalidationOutcome;
import com.turnoshift.operator.application.RevalidateOperatorSession;
import com.turnoshift.operator.domain.PlatformOperatorIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Filtro de seguridad que revalida el límite absoluto de 8 horas y el estado vigente
 * de usuario, organización, rol y auth_version en cada solicitud protegida.
 *
 * <p>Revalida de forma polimórfica identidades empresariales ({@link AuthenticatedIdentity})
 * y de operador de plataforma ({@link PlatformOperatorIdentity}).
 *
 * <p>Se ubica después de que el SecurityContext se ha restaurado desde la sesión
 * y antes de autorizar la ruta.
 */
public class SessionRevalidationFilter extends OncePerRequestFilter {

    private final RevalidateSession revalidateSession;
    private final RevalidateOperatorSession revalidateOperatorSession;
    private final SecurityContextRepository securityContextRepository;
    private final ObjectMapper objectMapper;
    private final boolean cookieSecure;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public SessionRevalidationFilter(
            RevalidateSession revalidateSession,
            SecurityContextRepository securityContextRepository,
            ObjectMapper objectMapper,
            @Value("${server.servlet.session.cookie.secure:true}") boolean cookieSecure) {
        this(revalidateSession, null, securityContextRepository, objectMapper, cookieSecure);
    }

    public SessionRevalidationFilter(
            RevalidateSession revalidateSession,
            RevalidateOperatorSession revalidateOperatorSession,
            SecurityContextRepository securityContextRepository,
            ObjectMapper objectMapper,
            @Value("${server.servlet.session.cookie.secure:true}") boolean cookieSecure) {
        this.revalidateSession = Objects.requireNonNull(revalidateSession, "revalidateSession no puede ser nulo");
        this.revalidateOperatorSession = revalidateOperatorSession;
        this.securityContextRepository = Objects.requireNonNull(securityContextRepository, "securityContextRepository no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        this.cookieSecure = cookieSecure;
    }

    @Override
    public boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "/api/health".equals(uri)
                || "/api/v1/auth/csrf".equals(uri)
                || "/api/v1/auth/login".equals(uri)
                || "/api/v1/auth/logout".equals(uri)
                || "/api/v1/auth/password-reset/inspect".equals(uri)
                || "/api/v1/auth/password-reset/complete".equals(uri)
                || "/api/v1/operator/auth/login".equals(uri)
                || "/api/v1/operator/auth/logout".equals(uri);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = securityContextHolderStrategy.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof AuthenticatedIdentity currentIdentity) {
            HttpSession session = request.getSession(false);
            Object authenticatedAtRaw = (session != null)
                    ? session.getAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE)
                    : null;

            RevalidationOutcome outcome = revalidateSession.execute(currentIdentity, authenticatedAtRaw);

            switch (outcome.status()) {
                case VALID -> filterChain.doFilter(request, response);

                case ROLE_CHANGED -> {
                    AuthenticatedIdentity updatedIdentity = outcome.updatedIdentity();
                    Authentication updatedAuth = new UsernamePasswordAuthenticationToken(
                            updatedIdentity,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + updatedIdentity.role()))
                    );
                    SecurityContext updatedContext = securityContextHolderStrategy.createEmptyContext();
                    updatedContext.setAuthentication(updatedAuth);
                    securityContextHolderStrategy.setContext(updatedContext);
                    securityContextRepository.saveContext(updatedContext, request, response);
                    filterChain.doFilter(request, response);
                }

                case EXPIRED, INVALID -> {
                    if (session != null) {
                        session.invalidate();
                    }
                    securityContextHolderStrategy.clearContext();
                    clearCookie(response);
                    SecurityProblemHandlers.writeProblemDetail(
                            response,
                            objectMapper,
                            HttpStatus.UNAUTHORIZED,
                            "/problems/session-required",
                            "Sesión requerida",
                            "No existe sesión activa. Inicia sesión para continuar.",
                            "SESSION_REQUIRED",
                            request.getRequestURI()
                    );
                }

                case ORGANIZATION_ACCESS_DENIED -> {
                    if (session != null) {
                        session.invalidate();
                    }
                    securityContextHolderStrategy.clearContext();
                    clearCookie(response);
                    SecurityProblemHandlers.writeProblemDetail(
                            response,
                            objectMapper,
                            HttpStatus.FORBIDDEN,
                            "/problems/access-denied",
                            "Acceso denegado",
                            "No tienes permiso para realizar esta acción.",
                            "ACCESS_DENIED",
                            request.getRequestURI()
                    );
                }

                case DATA_ACCESS_ERROR -> {
                    // No invalidar la sesión del cliente por un error técnico transitorio de BD
                    SecurityProblemHandlers.writeProblemDetail(
                            response,
                            objectMapper,
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "/problems/service-unavailable",
                            "Servicio no disponible",
                            "El servicio no está disponible temporalmente. Inténtalo más tarde.",
                            "SERVICE_UNAVAILABLE",
                            request.getRequestURI()
                    );
                }
            }
        } else if (principal instanceof PlatformOperatorIdentity currentOperatorIdentity) {
            if (revalidateOperatorSession == null) {
                filterChain.doFilter(request, response);
                return;
            }

            HttpSession session = request.getSession(false);
            Object authenticatedAtRaw = (session != null)
                    ? session.getAttribute(SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE)
                    : null;

            OperatorRevalidationOutcome outcome = revalidateOperatorSession.execute(currentOperatorIdentity, authenticatedAtRaw);

            switch (outcome) {
                case VALID -> filterChain.doFilter(request, response);

                case EXPIRED, INVALID -> {
                    if (session != null) {
                        session.invalidate();
                    }
                    securityContextHolderStrategy.clearContext();
                    clearCookie(response);
                    SecurityProblemHandlers.writeProblemDetail(
                            response,
                            objectMapper,
                            HttpStatus.UNAUTHORIZED,
                            "/problems/session-required",
                            "Sesión requerida",
                            "No existe sesión activa. Inicia sesión para continuar.",
                            "SESSION_REQUIRED",
                            request.getRequestURI()
                    );
                }

                case DATA_ACCESS_ERROR -> {
                    // No invalidar la sesión del cliente por un error técnico transitorio de BD
                    SecurityProblemHandlers.writeProblemDetail(
                            response,
                            objectMapper,
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "/problems/service-unavailable",
                            "Servicio no disponible",
                            "El servicio no está disponible temporalmente. Inténtalo más tarde.",
                            "SERVICE_UNAVAILABLE",
                            request.getRequestURI()
                    );
                }
            }
        } else {
            // Solicitud sin principal reconocido: continuar para evaluación ordinaria de Spring Security
            filterChain.doFilter(request, response);
        }
    }

    private void clearCookie(HttpServletResponse response) {
        ResponseCookie clearCookie = ResponseCookie.from("TSESSIONID", "")
                .path("/api")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
    }
}
