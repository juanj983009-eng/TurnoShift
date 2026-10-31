package com.turnoshift.operator.infrastructure.web;

import com.turnoshift.operator.domain.PlatformOperatorIdentity;
import com.turnoshift.security.SecurityProblemHandlers.ProblemDetailDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Controlador de sesión para operadores de plataforma (GET /api/v1/operator/auth/session
 * y POST /api/v1/operator/auth/logout).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Consultar el estado de la sesión activa derivando la identidad exclusivamente del {@link PlatformOperatorIdentity}.</li>
 *   <li>Garantizar que la consulta de sesión no crea ni renueva sesiones (sin getSession(true)).</li>
 *   <li>Invalidar la sesión del servidor y limpiar el SecurityContext al cerrar sesión.</li>
 *   <li>Revocar la cookie TSESSIONID con Max-Age=0, Path=/api, HttpOnly, SameSite=Lax y Secure dinámico.</li>
 *   <li>Validar que el cuerpo de logout esté vacío (o {}), rechazando campos adicionales con 400 VALIDATION_FAILED.</li>
 *   <li>Respuestas uniformes con Cache-Control: no-store y RFC 9457 Problem Details.</li>
 * </ul>
 */
@RestController
public class OperatorSessionController {

    private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
            MediaType.parseMediaType("application/problem+json;charset=UTF-8");

    private final ObjectMapper objectMapper;
    private final boolean cookieSecure;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public OperatorSessionController(
            ObjectMapper objectMapper,
            @Value("${server.servlet.session.cookie.secure:true}") boolean cookieSecure) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        this.cookieSecure = cookieSecure;
    }

    /**
     * Devuelve el operador asociado a la sesión activa.
     * Si no existe sesión activa o no es de un operador, responde 401 SESSION_REQUIRED.
     */
    @GetMapping("/api/v1/operator/auth/session")
    public ResponseEntity<?> getSession(HttpServletRequest httpRequest) {
        Authentication auth = securityContextHolderStrategy.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof PlatformOperatorIdentity identity)) {
            return sessionRequiredResponse(httpRequest);
        }

        OperatorSessionResponse responseBody = OperatorSessionResponse.from(identity);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    /**
     * Invalida la sesión activa de operador en el servidor, limpia el SecurityContext y revoca la cookie TSESSIONID.
     * Requiere sesión activa de operador y CSRF válido. Responde 204 No Content.
     */
    @PostMapping("/api/v1/operator/auth/logout")
    public ResponseEntity<?> logout(
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        // 1. Verificar autenticación de operador
        Authentication auth = securityContextHolderStrategy.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof PlatformOperatorIdentity)) {
            return sessionRequiredResponse(httpRequest);
        }

        // 2. Validar cuerpo de la petición (cuerpo ausente, en blanco o "{}" permitido; campos adicionales prohibidos)
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(rawBody);
                if (!node.isObject() || !node.isEmpty()) {
                    return validationFailedResponse(httpRequest, "El cuerpo de la petición debe estar vacío.");
                }
            } catch (JacksonException e) {
                return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
            }
        }

        // 3. Invalidar la sesión del servidor (si existe)
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // 4. Limpiar el SecurityContext
        securityContextHolderStrategy.clearContext();

        // 5. Revocar la cookie de sesión TSESSIONID
        ResponseCookie clearCookie = ResponseCookie.from("TSESSIONID", "")
                .path("/api")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
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
