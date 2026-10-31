package com.turnoshift.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit: SecurityProblemHandlers — respuestas Problem Details (RFC 9457)")
class SecurityProblemHandlersTest {

    private ObjectMapper objectMapper;
    private SecurityProblemHandlers.SessionRequiredEntryPoint entryPoint;
    private SecurityProblemHandlers.TurnoShiftAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        entryPoint = new SecurityProblemHandlers.SessionRequiredEntryPoint(objectMapper);
        accessDeniedHandler = new SecurityProblemHandlers.TurnoShiftAccessDeniedHandler(objectMapper);
    }

    @Test
    @DisplayName("401 SESSION_REQUIRED — status 401, Content-Type problem+json, JSON válido y sin mensaje de excepción interna")
    void sessionRequiredEntryPoint_respondeStatus401YProblemJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // URI con caracteres especiales (comillas, barras invertidas, etiquetas HTML) para verificar escape JSON
        request.setRequestURI("/api/v1/resource/\"special_id\"/path\\with\\slashes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        BadCredentialsException internalException =
                new BadCredentialsException("SECRET_INTERNAL_DATABASE_EXCEPTION_DETAILS_12345");

        entryPoint.commence(request, response, internalException);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");

        String jsonBody = response.getContentAsString();

        // Verificar que el cuerpo es JSON válido y parseable
        JsonNode root = objectMapper.readTree(jsonBody);
        assertThat(root.get("type").asString()).isEqualTo("/problems/session-required");
        assertThat(root.get("title").asString()).isEqualTo("Sesión requerida");
        assertThat(root.get("status").asInt()).isEqualTo(401);
        assertThat(root.get("detail").asString()).isEqualTo("No existe sesión activa. Inicia sesión para continuar.");
        assertThat(root.get("instance").asString()).isEqualTo("/api/v1/resource/\"special_id\"/path\\with\\slashes");
        assertThat(root.get("code").asString()).isEqualTo("SESSION_REQUIRED");
        assertThat(root.get("requestId").asString()).isNotBlank();
        assertThat(root.get("errors").isArray()).isTrue();
        assertThat(root.get("errors")).isEmpty();

        // Excepción interna no expuesta
        assertThat(jsonBody).doesNotContain("SECRET_INTERNAL_DATABASE_EXCEPTION_DETAILS_12345");
    }

    @Test
    @DisplayName("403 CSRF_INVALID — status 403, Content-Type problem+json, código CSRF_INVALID y sin detalles internos")
    void accessDeniedHandler_csrfInvalido_respondeStatus403YCsrfInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsrfException csrfException = new CsrfException("INTERNAL_CSRF_SECRET_HASH_FAIL") {};

        accessDeniedHandler.handle(request, response, csrfException);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");

        String jsonBody = response.getContentAsString();
        JsonNode root = objectMapper.readTree(jsonBody);

        assertThat(root.get("type").asString()).isEqualTo("/problems/csrf-invalid");
        assertThat(root.get("title").asString()).isEqualTo("Protección CSRF inválida");
        assertThat(root.get("status").asInt()).isEqualTo(403);
        assertThat(root.get("detail").asString()).isEqualTo("El token CSRF está ausente o ha vencido.");
        assertThat(root.get("instance").asString()).isEqualTo("/api/v1/auth/login");
        assertThat(root.get("code").asString()).isEqualTo("CSRF_INVALID");
        assertThat(root.get("requestId").asString()).isNotBlank();
        assertThat(root.get("errors").isArray()).isTrue();

        assertThat(jsonBody).doesNotContain("INTERNAL_CSRF_SECRET_HASH_FAIL");
    }

    @Test
    @DisplayName("403 ACCESS_DENIED — status 403, Content-Type problem+json, código ACCESS_DENIED")
    void accessDeniedHandler_accesoDenegado_respondeStatus403YAccessDenied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/protected/action");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AccessDeniedException accessDeniedException = new AccessDeniedException("INTERNAL_PERMISSION_TREE_EX");

        accessDeniedHandler.handle(request, response, accessDeniedException);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");

        String jsonBody = response.getContentAsString();
        JsonNode root = objectMapper.readTree(jsonBody);

        assertThat(root.get("type").asString()).isEqualTo("/problems/access-denied");
        assertThat(root.get("title").asString()).isEqualTo("Acceso denegado");
        assertThat(root.get("status").asInt()).isEqualTo(403);
        assertThat(root.get("code").asString()).isEqualTo("ACCESS_DENIED");
        assertThat(root.get("instance").asString()).isEqualTo("/api/v1/protected/action");

        assertThat(jsonBody).doesNotContain("INTERNAL_PERMISSION_TREE_EX");
    }
}
