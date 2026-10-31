package com.turnoshift.identity.infrastructure.web;

import com.turnoshift.identity.application.IssueInitialSetupToken;
import com.turnoshift.identity.domain.InitialSetupTokenEmission;
import com.turnoshift.identity.domain.PasswordBlocklist;
import com.turnoshift.identity.domain.SecureTokenGenerator;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("IT: PasswordResetHttpIT — endpoints HTTP de password-reset en SQL Server real")
class PasswordResetHttpIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense()
                    .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        registry.add("server.servlet.session.cookie.secure", () -> "false");
    }

    @TestConfiguration
    static class TestBlocklistConfig {
        @Bean
        @Primary
        PasswordBlocklist testPasswordBlocklist() {
            PasswordBlocklist mock = Mockito.mock(PasswordBlocklist.class);
            when(mock.estaBloquada(anyString())).thenReturn(false);
            return mock;
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired IssueInitialSetupToken issueInitialSetupToken;
    @Autowired PasswordBlocklist mockBlocklist;
    @Autowired SecureTokenGenerator secureTokenGenerator;

    @BeforeEach
    void resetBlocklist() {
        when(mockBlocklist.estaBloquada(anyString())).thenReturn(false);
    }

    private UUID insertarOrganizacion(String code, String displayName, String status) {
        UUID orgId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, ?, 'America/Bogota', 10, 1)
                """, orgId, code, displayName, status);
        return orgId;
    }

    private UUID insertarUsuario(UUID orgId, String email, String displayName, boolean isActive) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name,
                    password_hash, role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, ?, NULL, 'ADMINISTRADOR', ?, 1)
                """, userId, orgId, email, email.toLowerCase(), displayName, isActive ? 1 : 0);
        return userId;
    }

    private record CsrfSession(String token, Cookie cookie) {}

    private CsrfSession obtenerCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
        Cookie cookie = result.getResponse().getCookie("TSESSIONID");
        return new CsrfSession(token, cookie);
    }

    @Test
    @DisplayName("Flujo completo: inspect 200 -> complete 204 -> BD actualizada -> inspect 422 -> login 200")
    void flujoCompleto_inspectYCompleteExitoso() throws Exception {
        String orgCode = ("HTTP-SETUP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)).toUpperCase(java.util.Locale.ROOT);
        UUID orgId = insertarOrganizacion(orgCode, "Setup Test Org", "ACTIVA");
        String email = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        UUID userId = insertarUsuario(orgId, email, "Admin Inicial", true);

        // 1. Emitir token INITIAL_SETUP
        InitialSetupTokenEmission emission = issueInitialSetupToken.issue(orgId, userId).orElseThrow();
        String rawToken = emission.rawToken();

        // 2. Obtener CSRF
        CsrfSession csrf = obtenerCsrf();

        // 3. POST /api/v1/auth/password-reset/inspect -> 200 OK con INITIAL_SETUP
        mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.purpose").value("INITIAL_SETUP"));

        // 4. POST /api/v1/auth/password-reset/complete -> 204 No Content
        String newPassword = "Mi-Contrasena-Segura-2026!";
        mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "%s"
                                }
                                """.formatted(rawToken, newPassword)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));

        // 5. Verificar estado en SQL Server
        String hashInDb = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE id = ?", String.class, userId);
        assertThat(hashInDb).isNotNull();
        assertThat(hashInDb).startsWith("$argon2id$");

        Integer authVersion = jdbc.queryForObject(
                "SELECT auth_version FROM app_users WHERE id = ?", Integer.class, userId);
        assertThat(authVersion).isEqualTo(2);

        Integer consumedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? AND consumed_at IS NOT NULL",
                Integer.class, userId);
        assertThat(consumedCount).isEqualTo(1);

        // 6. Inspeccionar el mismo token tras ser consumido -> 422 RESET_LINK_INVALID
        mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/reset-link-invalid"))
                .andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"));

        // 7. Login exitoso con la nueva contraseña configurada
        CsrfSession loginCsrf = obtenerCsrf();
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(loginCsrf.cookie())
                        .header("X-CSRF-TOKEN", loginCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(orgCode, email, newPassword)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.organization.id").value(orgId.toString()))
                .andExpect(jsonPath("$.landingPage").value("/app/inicio"));
    }

    @Test
    @DisplayName("Inspect sin CSRF responde 403 CSRF_INVALID")
    void inspect_sinCsrf_responde403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP1"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/csrf-invalid"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("Complete sin CSRF responde 403 CSRF_INVALID")
    void complete_sinCsrf_responde403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP1",
                                  "newPassword": "Valid-Password-2026!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/csrf-invalid"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("Complete con contraseña que viola política responde 400 VALIDATION_FAILED y no consume token")
    void complete_contrasenaInvalida_responde400YNoConsumeToken() throws Exception {
        String orgCode = ("HTTP-FAIL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)).toUpperCase(java.util.Locale.ROOT);
        UUID orgId = insertarOrganizacion(orgCode, "Fail Test Org", "ACTIVA");
        String email = "fail-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        UUID userId = insertarUsuario(orgId, email, "Fail User", true);

        InitialSetupTokenEmission emission = issueInitialSetupToken.issue(orgId, userId).orElseThrow();
        String rawToken = emission.rawToken();

        CsrfSession csrf = obtenerCsrf();

        // Contraseña demasiado corta (< 15 caracteres)
        mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "corta"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].code").value("IDENTITY_PASSWORD_TOO_SHORT"));

        // Verificar que el token NO fue consumido
        Integer consumedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? AND consumed_at IS NOT NULL",
                Integer.class, userId);
        assertThat(consumedCount).isEqualTo(0);

        // Verificar que el hash del usuario sigue siendo NULL
        String hashInDb = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE id = ?", String.class, userId);
        assertThat(hashInDb).isNull();
    }

    @Test
    @DisplayName("Inspect con token inexistente responde 422 RESET_LINK_INVALID")
    void inspect_tokenInexistente_responde422() throws Exception {
        CsrfSession csrf = obtenerCsrf();

        mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP1"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/reset-link-invalid"))
                .andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"));
    }

    @Test
    @DisplayName("Contrato: token con propósito PASSWORD_RESET responde 422 RESET_LINK_INVALID en inspect y complete")
    void inspectYComplete_tokenPasswordReset_responde422ResetLinkInvalid() throws Exception {
        String orgCode = ("HTTP-PR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)).toUpperCase(java.util.Locale.ROOT);
        UUID orgId = insertarOrganizacion(orgCode, "PR Test Org", "ACTIVA");
        String email = "pr-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        UUID userId = insertarUsuario(orgId, email, "PR User", true);

        String rawToken = secureTokenGenerator.generateRawToken();
        byte[] tokenHash = secureTokenGenerator.computeSha256(rawToken);

        UUID tokenId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant expiresAt = now.plus(Duration.ofMinutes(30));

        jdbc.update("""
                INSERT INTO password_reset_tokens (id, organization_id, user_id, token_hash, purpose, created_at, expires_at)
                VALUES (?, ?, ?, ?, 'PASSWORD_RESET', ?, ?)
                """, tokenId, orgId, userId, tokenHash, java.sql.Timestamp.from(now), java.sql.Timestamp.from(expiresAt));

        CsrfSession csrf = obtenerCsrf();

        // 1. Inspect con propósito PASSWORD_RESET -> 422 RESET_LINK_INVALID
        mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/reset-link-invalid"))
                .andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"))
                .andExpect(jsonPath("$.detail").value("El enlace ha vencido o ya fue utilizado."));

        // 2. Complete con propósito PASSWORD_RESET -> 422 RESET_LINK_INVALID
        mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "Valid-Password-12345!"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/reset-link-invalid"))
                .andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"));

        // Verificar que el token NO fue consumido
        Integer consumedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE id = ? AND consumed_at IS NOT NULL",
                Integer.class, tokenId);
        assertThat(consumedCount).isEqualTo(0);

        // Verificar que el password del usuario no cambió
        String hashInDb = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE id = ?", String.class, userId);
        assertThat(hashInDb).isNull();
    }

    @Test
    @DisplayName("Pre-controlador: peticiones con JSON roto, tipo incorrecto o Content-Type incompatible devuelven 400 VALIDATION_FAILED uniforme")
    void preControlador_erroresParseoYContentType_responden400ValidationFailed() throws Exception {
        CsrfSession csrf = obtenerCsrf();

        // JSON malformado
        mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": "))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Content-Type incompatible
        mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("token=123&newPassword=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Tipo incorrecto en token (número)
        mockMvc.perform(post("/api/v1/auth/password-reset/inspect")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": 99999
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("token"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }
}
