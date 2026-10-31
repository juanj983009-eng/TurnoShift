package com.turnoshift.operator.infrastructure.web;

import com.turnoshift.identity.infrastructure.security.Argon2PasswordService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integración sobre SQL Server real (Testcontainers) para la seguridad,
 * autenticación, estado de sesión, logout y revalidación de operadores de plataforma.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("IT: Sesión y Seguridad de Operador — Spring Security + Spring Session JDBC")
class OperatorSecuritySessionIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        registry.add("server.servlet.session.cookie.secure", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired Argon2PasswordService passwordService;

    private String getCsrfTokenAndCookie(Cookie sessionCookie) throws Exception {
        var builder = get("/api/v1/auth/csrf");
        if (sessionCookie != null) {
            builder.cookie(sessionCookie);
        }
        MvcResult csrfResult = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();
    }

    private Cookie getCookieFromCsrf() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return csrfResult.getResponse().getCookie("TSESSIONID");
    }

    @Test
    @DisplayName("Flujo completo: Login operador -> Session operador -> Logout operador, con rotación y persistencia en SPRING_SESSION")
    void flujoCompleto_loginSessionLogout_conPersistenciaYRotacion() throws Exception {
        String operatorId = UUID.randomUUID().toString();
        String email = "op." + UUID.randomUUID().toString().substring(0, 6) + "@turnoshift.example";
        String plainPassword = "ClaveOperadorSegura2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO platform_operators
                    (id, email, normalized_email, display_name, password_hash, is_active, auth_version)
                VALUES (?, ?, ?, 'Operador Integracion', ?, 1, 1)
                """, operatorId, email, email.toLowerCase(), passwordHash);

        // 1. Obtener CSRF y cookie anónima inicial
        Cookie initialCookie = getCookieFromCsrf();
        String csrfToken = getCsrfTokenAndCookie(initialCookie);

        // 2. Login de operador
        String loginPayload = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, plainPassword);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/operator/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.operator.id").value(operatorId))
                .andExpect(jsonPath("$.operator.displayName").value("Operador Integracion"))
                .andExpect(jsonPath("$.landingPage").value("/operator/inicio"))
                .andReturn();

        // Verificar rotación de sesión: se entrega nueva cookie TSESSIONID
        Cookie authCookie = loginResult.getResponse().getCookie("TSESSIONID");
        assertThat(authCookie).as("Cookie de sesión autenticada debe estar presente").isNotNull();
        assertThat(authCookie.getValue()).as("El ID de sesión debió rotar").isNotEqualTo(initialCookie.getValue());

        // Verificar persistencia de sesión en base de datos (SPRING_SESSION_ATTRIBUTES)
        Integer attributeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME = 'SPRING_SECURITY_CONTEXT'",
                Integer.class
        );
        assertThat(attributeCount).as("La sesión autenticada debe persistirse en SQL Server").isGreaterThan(0);

        // 3. GET /api/v1/operator/auth/session con la cookie autenticada -> 200 OK
        mockMvc.perform(get("/api/v1/operator/auth/session")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.operator.id").value(operatorId))
                .andExpect(jsonPath("$.operator.displayName").value("Operador Integracion"))
                .andExpect(jsonPath("$.landingPage").value("/operator/inicio"));

        // 4. Logout de operador: obtener CSRF asociado a la sesión
        String logoutCsrfToken = getCsrfTokenAndCookie(authCookie);

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/operator/auth/logout")
                        .cookie(authCookie)
                        .header("X-CSRF-TOKEN", logoutCsrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        String setCookieHeader = logoutResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).contains("TSESSIONID=");
        assertThat(setCookieHeader).contains("Max-Age=0");
        assertThat(setCookieHeader).contains("Path=/api");

        // 5. GET /api/v1/operator/auth/session con la cookie anterior -> 401 SESSION_REQUIRED
        mockMvc.perform(get("/api/v1/operator/auth/session")
                        .cookie(authCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Bloqueo cruzado: operador autenticado intentando acceder a rutas empresariales recibe 403 ACCESS_DENIED")
    void bloqueoCruzado_operadorAccedeARutasEmpresariales_recibe403() throws Exception {
        String operatorId = UUID.randomUUID().toString();
        String email = "op.cross." + UUID.randomUUID().toString().substring(0, 6) + "@turnoshift.example";
        String plainPassword = "ClaveOperadorSegura2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO platform_operators
                    (id, email, normalized_email, display_name, password_hash, is_active, auth_version)
                VALUES (?, ?, ?, 'Operador Cross', ?, 1, 1)
                """, operatorId, email, email.toLowerCase(), passwordHash);

        Cookie initialCookie = getCookieFromCsrf();
        String csrfToken = getCsrfTokenAndCookie(initialCookie);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/operator/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, plainPassword)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie operatorCookie = loginResult.getResponse().getCookie("TSESSIONID");

        // Intento 1: Operador llama GET /api/v1/auth/session (sesión empresarial) -> 403 ACCESS_DENIED
        mockMvc.perform(get("/api/v1/auth/session")
                        .cookie(operatorCookie))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/access-denied"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403));

        // Intento 2: Operador llama POST /api/v1/auth/logout (logout empresarial) -> 403 ACCESS_DENIED
        String operatorCsrf = getCsrfTokenAndCookie(operatorCookie);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(operatorCookie)
                        .header("X-CSRF-TOKEN", operatorCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Bloqueo cruzado: usuario empresarial intentando acceder a rutas de operador recibe 403 ACCESS_DENIED")
    void bloqueoCruzado_usuarioEmpresarialAccedeARutasOperador_recibe403() throws Exception {
        String orgId = UUID.randomUUID().toString();
        String orgCode = "CORP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Corp Test S.A.', 'ACTIVA', 'America/Lima', 10, 1)
                """, orgId, orgCode);

        String userId = UUID.randomUUID().toString();
        String userEmail = "corp.user." + UUID.randomUUID().toString().substring(0, 6) + "@corp.example";
        String plainPassword = "ClaveEmpresarial2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Admin Corp', ?, 'ADMINISTRADOR', 1, 1)
                """, userId, orgId, userEmail, userEmail.toLowerCase(), passwordHash);

        Cookie initialCookie = getCookieFromCsrf();
        String csrfToken = getCsrfTokenAndCookie(initialCookie);

        // Login empresarial
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(orgCode, userEmail, plainPassword)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie businessCookie = loginResult.getResponse().getCookie("TSESSIONID");

        // Intento 1: Usuario empresarial llama GET /api/v1/operator/auth/session -> 403 ACCESS_DENIED
        mockMvc.perform(get("/api/v1/operator/auth/session")
                        .cookie(businessCookie))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/access-denied"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403));

        // Intento 2: Usuario empresarial llama POST /api/v1/operator/auth/logout -> 403 ACCESS_DENIED
        String businessCsrf = getCsrfTokenAndCookie(businessCookie);
        mockMvc.perform(post("/api/v1/operator/auth/logout")
                        .cookie(businessCookie)
                        .header("X-CSRF-TOKEN", businessCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Revocación inmediata de sesión al cambiar auth_version del operador en base de datos")
    void revocacionInmediata_alCambiarAuthVersion() throws Exception {
        String operatorId = UUID.randomUUID().toString();
        String email = "op.ver." + UUID.randomUUID().toString().substring(0, 6) + "@turnoshift.example";
        String plainPassword = "ClaveOperadorSegura2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO platform_operators
                    (id, email, normalized_email, display_name, password_hash, is_active, auth_version)
                VALUES (?, ?, ?, 'Operador Version', ?, 1, 1)
                """, operatorId, email, email.toLowerCase(), passwordHash);

        Cookie initialCookie = getCookieFromCsrf();
        String csrfToken = getCsrfTokenAndCookie(initialCookie);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/operator/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, plainPassword)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie operatorCookie = loginResult.getResponse().getCookie("TSESSIONID");

        // Sesión activa antes del cambio
        mockMvc.perform(get("/api/v1/operator/auth/session").cookie(operatorCookie))
                .andExpect(status().isOk());

        // Modificar auth_version en base de datos (revocación global)
        jdbc.update("UPDATE platform_operators SET auth_version = auth_version + 1 WHERE id = ?", operatorId);

        // Próxima petición debe ser rechazada por el SessionRevalidationFilter con 401 SESSION_REQUIRED
        mockMvc.perform(get("/api/v1/operator/auth/session").cookie(operatorCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Revocación inmediata de sesión al desactivar el operador en base de datos")
    void revocacionInmediata_alDesactivarOperador() throws Exception {
        String operatorId = UUID.randomUUID().toString();
        String email = "op.deact." + UUID.randomUUID().toString().substring(0, 6) + "@turnoshift.example";
        String plainPassword = "ClaveOperadorSegura2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO platform_operators
                    (id, email, normalized_email, display_name, password_hash, is_active, auth_version)
                VALUES (?, ?, ?, 'Operador Desactivar', ?, 1, 1)
                """, operatorId, email, email.toLowerCase(), passwordHash);

        Cookie initialCookie = getCookieFromCsrf();
        String csrfToken = getCsrfTokenAndCookie(initialCookie);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/operator/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, plainPassword)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie operatorCookie = loginResult.getResponse().getCookie("TSESSIONID");

        // Sesión activa antes de la desactivación
        mockMvc.perform(get("/api/v1/operator/auth/session").cookie(operatorCookie))
                .andExpect(status().isOk());

        // Desactivar operador en base de datos
        jdbc.update("UPDATE platform_operators SET is_active = 0 WHERE id = ?", operatorId);

        // Próxima petición rechazada con 401 SESSION_REQUIRED
        mockMvc.perform(get("/api/v1/operator/auth/session").cookie(operatorCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("CSRF inválido o ausente en login y logout responde 403 CSRF_INVALID")
    void csrfInvalidoOAusente_responde403() throws Exception {
        // Login sin CSRF -> 403 CSRF_INVALID
        mockMvc.perform(post("/api/v1/operator/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "op@turnoshift.example",
                                  "password": "ClaveSegura12345!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/csrf-invalid"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(jsonPath("$.status").value(403));

        // Logout sin CSRF -> 403 CSRF_INVALID
        mockMvc.perform(post("/api/v1/operator/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("Rate limit de operador: 5 intentos fallidos permitidos, 6to responde 429 RATE_LIMITED con Retry-After")
    void rateLimit_alcanzaLimite_responde429() throws Exception {
        Cookie cookie = getCookieFromCsrf();
        String csrfToken = getCsrfTokenAndCookie(cookie);
        String clientIp = "192.0.2." + (int)(Math.random() * 200 + 10);

        String badPayload = """
                {
                  "email": "noexiste@turnoshift.example",
                  "password": "ClaveIncorrecta123!"
                }
                """;

        // 5 intentos fallidos
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/operator/auth/login")
                            .with(req -> {
                                req.setRemoteAddr(clientIp);
                                return req;
                            })
                            .cookie(cookie)
                            .header("X-CSRF-TOKEN", csrfToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badPayload))
                    .andExpect(status().isUnauthorized());
        }

        // Intento 6 debe ser bloqueado por el limitador con 429
        mockMvc.perform(post("/api/v1/operator/auth/login")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/rate-limited"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429));
    }
}
