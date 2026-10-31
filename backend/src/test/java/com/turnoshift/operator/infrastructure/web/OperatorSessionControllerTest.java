package com.turnoshift.operator.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.operator.domain.PlatformOperatorIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Unit: OperatorSessionController — endpoints GET /api/v1/operator/auth/session y POST /api/v1/operator/auth/logout")
class OperatorSessionControllerTest {

    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    private final UUID operatorId = UUID.randomUUID();
    private final PlatformOperatorIdentity operatorIdentity =
            new PlatformOperatorIdentity(operatorId, "Operador Central", 1L);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        OperatorSessionController controller = new OperatorSessionController(objectMapper, true);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsOperator() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                operatorIdentity,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_OPERATOR"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("GET /session con operador autenticado: responde 200 OK con OperatorSessionResponse")
    void getSession_conOperadorAutenticado_responde200() throws Exception {
        authenticateAsOperator();

        mockMvc.perform(get("/api/v1/operator/auth/session"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.operator.id").value(operatorId.toString()))
                .andExpect(jsonPath("$.operator.displayName").value("Operador Central"))
                .andExpect(jsonPath("$.landingPage").value("/operator/inicio"));
    }

    @Test
    @DisplayName("GET /session sin autenticación: responde 401 SESSION_REQUIRED")
    void getSession_sinAutenticacion_responde401() throws Exception {
        mockMvc.perform(get("/api/v1/operator/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/session-required"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /session con principal no-operador (e.g. AuthenticatedIdentity empresarial): responde 401 SESSION_REQUIRED")
    void getSession_conPrincipalNoOperador_responde401() throws Exception {
        AuthenticatedIdentity businessIdentity = new AuthenticatedIdentity(
                UUID.randomUUID(), UUID.randomUUID(), "user@corp.example",
                "User Corp", "ADMINISTRADOR", null, 1L, "Corp S.A.", "America/Lima"
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(
                businessIdentity, null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        mockMvc.perform(get("/api/v1/operator/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("GET /session no crea sesión HTTP")
    void getSession_noCreaSesion() throws Exception {
        authenticateAsOperator();

        MvcResult result = mockMvc.perform(get("/api/v1/operator/auth/session"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("POST /logout con operador autenticado: invalida sesión, limpia SecurityContext y revoca cookie")
    void logout_conOperadorAutenticado_invalidaSesionYRevocaCookie() throws Exception {
        authenticateAsOperator();

        MvcResult result = mockMvc.perform(post("/api/v1/operator/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("TSESSIONID=");
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).contains("Path=/api");
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("SameSite=Lax");
        assertThat(setCookie).containsIgnoringCase("Secure");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("POST /logout sin sesión autenticada: responde 401 SESSION_REQUIRED")
    void logout_sinAutenticacion_responde401() throws Exception {
        mockMvc.perform(post("/api/v1/operator/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("POST /logout con campos adicionales: responde 400 VALIDATION_FAILED y conserva sesión")
    void logout_conCamposAdicionales_responde400() throws Exception {
        authenticateAsOperator();

        mockMvc.perform(post("/api/v1/operator/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extra\": \"valor-no-permitido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("POST /logout con JSON malformado: responde 400 VALIDATION_FAILED")
    void logout_conJsonMalformado_responde400() throws Exception {
        authenticateAsOperator();

        mockMvc.perform(post("/api/v1/operator/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{json-roto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
