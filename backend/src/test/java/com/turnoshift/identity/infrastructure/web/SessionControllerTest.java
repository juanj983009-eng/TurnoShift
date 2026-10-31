package com.turnoshift.identity.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Unit: SessionController — endpoints GET /api/v1/auth/session y POST /api/v1/auth/logout")
class SessionControllerTest {

    private ObjectMapper objectMapper;
    private SessionController controllerSecure;
    private SessionController controllerInsecure;
    private MockMvc mockMvcSecure;
    private MockMvc mockMvcInsecure;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID orgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID employeeId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        controllerSecure = new SessionController(objectMapper, true);
        controllerInsecure = new SessionController(objectMapper, false);

        mockMvcSecure = MockMvcBuilders.standaloneSetup(controllerSecure).build();
        mockMvcInsecure = MockMvcBuilders.standaloneSetup(controllerInsecure).build();

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                userId,
                orgId,
                "usuario@empresa.example",
                "Usuario Prueba",
                role,
                employeeId,
                1L,
                "Empresa Demo",
                "America/Bogota"
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                identity,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    // =========================================================================
    // 1. GET /api/v1/auth/session
    // =========================================================================

    @Test
    @DisplayName("GET /auth/session autenticado como ADMINISTRADOR: responde 200 con SessionResponse, Cache-Control no-store y landingPage /app/inicio")
    void getSession_autenticadoAdmin_responde200ConSessionResponse() throws Exception {
        authenticateAs("ADMINISTRADOR");

        mockMvcSecure.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.displayName").value("Usuario Prueba"))
                .andExpect(jsonPath("$.user.role").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.user.employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$.organization.id").value(orgId.toString()))
                .andExpect(jsonPath("$.organization.displayName").value("Empresa Demo"))
                .andExpect(jsonPath("$.organization.timeZone").value("America/Bogota"))
                .andExpect(jsonPath("$.landingPage").value("/app/inicio"));
    }

    @Test
    @DisplayName("GET /auth/session autenticado como COLABORADOR: responde 200 con landingPage /app/mi-horario")
    void getSession_autenticadoColaborador_landingPageMiHorario() throws Exception {
        authenticateAs("COLABORADOR");

        mockMvcSecure.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.landingPage").value("/app/mi-horario"));
    }

    @Test
    @DisplayName("GET /auth/session sin autenticación: responde 401 SESSION_REQUIRED en Problem Details RFC 9457")
    void getSession_sinAutenticacion_responde401SessionRequired() throws Exception {
        mockMvcSecure.perform(get("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/session-required"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("No existe sesión activa. Inicia sesión para continuar."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /auth/session con usuario anónimo: responde 401 SESSION_REQUIRED")
    void getSession_conUsuarioAnonimo_responde401SessionRequired() throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        ));
        SecurityContextHolder.setContext(context);

        mockMvcSecure.perform(get("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /auth/session no crea una sesión HTTP si no existe")
    void getSession_noCreaSesionHttp() throws Exception {
        authenticateAs("ADMINISTRADOR");

        MvcResult result = mockMvcSecure.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                .andReturn();

        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isNull();
    }

    // =========================================================================
    // 2. POST /api/v1/auth/logout
    // =========================================================================

    @Test
    @DisplayName("POST /auth/logout sin cuerpo: invalida sesión, limpia contexto, retorna 204 y limpia cookie con Secure")
    void logout_sinCuerpo_invalidaSesionYLimpiaCookieSecure() throws Exception {
        authenticateAs("ADMINISTRADOR");
        MockHttpSession session = new MockHttpSession();

        MvcResult result = mockMvcSecure.perform(post("/api/v1/auth/logout")
                        .session(session))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        // 1. Sesión HTTP invalidada
        assertThat(session.isInvalid()).isTrue();

        // 2. SecurityContext limpiado
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // 3. Set-Cookie revoca TSESSIONID con Max-Age=0, Path=/api, HttpOnly, SameSite=Lax, Secure
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("TSESSIONID=");
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).contains("Path=/api");
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("SameSite=Lax");
        assertThat(setCookie).containsIgnoringCase("Secure");
    }

    @Test
    @DisplayName("POST /auth/logout con cuerpo '{}': es válido, responde 204 e invalida sesión")
    void logout_conCuerpoVacioJson_esValidoResponde204() throws Exception {
        authenticateAs("ADMINISTRADOR");
        MockHttpSession session = new MockHttpSession();

        mockMvcSecure.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("POST /auth/logout con perfil local (cookieSecure=false): Set-Cookie no contiene 'Secure'")
    void logout_perfilLocal_setCookieSinSecure() throws Exception {
        authenticateAs("ADMINISTRADOR");
        MockHttpSession session = new MockHttpSession();

        MvcResult result = mockMvcInsecure.perform(post("/api/v1/auth/logout")
                        .session(session))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("TSESSIONID=");
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).doesNotContainIgnoringCase("Secure");
    }

    @Test
    @DisplayName("POST /auth/logout sin sesión autenticada: responde 401 SESSION_REQUIRED")
    void logout_sinSesionAutenticada_responde401() throws Exception {
        mockMvcSecure.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/session-required"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /auth/logout con campos adicionales en JSON: responde 400 VALIDATION_FAILED y no invalida sesión")
    void logout_conCamposAdicionales_responde400YNoInvalidaSesion() throws Exception {
        authenticateAs("ADMINISTRADOR");
        MockHttpSession session = new MockHttpSession();

        mockMvcSecure.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "extraField": "maliciousValue"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("El cuerpo de la petición debe estar vacío."));

        // La sesión NO debe ser invalidada ante error 400
        assertThat(session.isInvalid()).isFalse();
        // El SecurityContext NO debe ser limpiado
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("POST /auth/logout con estructura no objeto (array JSON): responde 400 VALIDATION_FAILED")
    void logout_conArrayJson_responde400() throws Exception {
        authenticateAs("ADMINISTRADOR");
        MockHttpSession session = new MockHttpSession();

        mockMvcSecure.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1, 2, 3]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400));

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    @DisplayName("POST /auth/logout con JSON sintácticamente inválido: responde 400 VALIDATION_FAILED")
    void logout_conJsonInvalido_responde400() throws Exception {
        authenticateAs("ADMINISTRADOR");
        MockHttpSession session = new MockHttpSession();

        mockMvcSecure.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400));

        assertThat(session.isInvalid()).isFalse();
    }
}
