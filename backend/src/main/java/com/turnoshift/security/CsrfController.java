package com.turnoshift.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint CSRF: {@code GET /api/v1/auth/csrf}.
 *
 * <p>Definido en openapi.yaml (operationId: getCsrfToken, security: [], tag: infrastructure).
 *
 * <p><b>Comportamiento:</b>
 * <ol>
 *   <li>Si no existe sesión, Spring Session JDBC crea una sesión anónima.</li>
 *   <li>El token CSRF diferido se materializa llamando a {@code csrfToken.getToken()}.</li>
 *   <li>La respuesta devuelve únicamente los campos del esquema {@code CsrfTokenResponse}
 *       definido en openapi.yaml: {@code token}. El nombre del header ({@code X-CSRF-TOKEN})
 *       es parte del contrato y se documenta en la descripción pero no es un campo adicional
 *       del schema.</li>
 *   <li>{@code Cache-Control: no-store} para impedir almacenamiento del token en caché.</li>
 * </ol>
 *
 * <p><b>Nota sobre sesiones anónimas:</b> una sesión anónima puede existir antes del login.
 * No representa un usuario empresarial autenticado (Nota 07, §10.1). El token CSRF de una
 * sesión anónima es válido para el login y las operaciones sin sesión requerida.
 *
 * <p><b>Pendiente:</b> el frontend debe obtener un nuevo token tras login y logout
 * (openapi.yaml, descripción de getCsrfToken).
 */
@RestController
@RequestMapping("/api/v1")
public class CsrfController {

    /**
     * Devuelve el token CSRF activo para la sesión actual (anónima o autenticada).
     * Materializa el token diferido antes de responder.
     *
     * @param csrfToken token CSRF inyectado por Spring Security como atributo de la petición
     * @param response  para añadir la cabecera Cache-Control
     * @return objeto con el campo {@code token} según CsrfTokenResponse del openapi.yaml
     */
    @GetMapping("/auth/csrf")
    public CsrfResponse getCsrfToken(CsrfToken csrfToken, HttpServletResponse response) {
        // Materializar el token diferido (lazy evaluation de Spring Security 6+).
        // Sin esta llamada, el token puede no quedar almacenado en la sesión
        // antes de que el cliente lo use en la siguiente petición.
        String token = csrfToken.getToken();

        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        return new CsrfResponse(token);
    }

    /**
     * Respuesta del endpoint CSRF según el esquema CsrfTokenResponse de openapi.yaml.
     * Solo contiene el campo {@code token}; el nombre del header (X-CSRF-TOKEN) es parte
     * del contrato documentado en openapi.yaml, no un campo adicional de la respuesta.
     */
    public record CsrfResponse(String token) {}
}
