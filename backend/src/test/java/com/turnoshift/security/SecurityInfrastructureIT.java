package com.turnoshift.security;

import com.turnoshift.identity.infrastructure.security.Argon2PasswordService;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * IT: Infraestructura de seguridad — Spring Security + Spring Session JDBC + CSRF.
 *
 * <p>Demuestra la infraestructura de seguridad sin login real. Ver Javadoc de clase.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("IT: Seguridad — Spring Security + Spring Session JDBC + CSRF")
class SecurityInfrastructureIT {

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
        // Deshabilitar Secure para MockMvc HTTP (sin TLS) en pruebas
        registry.add("server.servlet.session.cookie.secure", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired Argon2PasswordService passwordService;

    // =========================================================================
    // Flyway y esquema
    // =========================================================================

    @Test
    @DisplayName("Flyway aplica V1–V11 desde base vacía (incluye Spring Session, Platform Operators y Api Operations)")
    void flywayAplicaV1HastaV9() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(versiones).containsExactly("1","2","3","4","5","6","7","8","9","10","11");
    }

    @Test
    @DisplayName("Tablas SPRING_SESSION y SPRING_SESSION_ATTRIBUTES existen")
    void tablasSpringSesionExisten() {
        for (String tabla : List.of("SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES")) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'",
                    Integer.class, tabla);
            assertThat(count).as("Tabla " + tabla + " debe existir").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Índices de Spring Session existen (IX1, IX2, IX3)")
    void indicesSpringSesionExisten() {
        for (String idx : List.of("SPRING_SESSION_IX1","SPRING_SESSION_IX2","SPRING_SESSION_IX3")) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.indexes WHERE name = ?",
                    Integer.class, idx);
            assertThat(count).as("Índice " + idx + " debe existir").isEqualTo(1);
        }
    }

    // =========================================================================
    // GET /api/health — público, sin sesión
    // =========================================================================

    @Test
    @DisplayName("GET /api/health devuelve 200 sin sesión activa")
    void healthSinSesion() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // =========================================================================
    // GET /api/v1/auth/csrf — token y cookie
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/auth/csrf devuelve 200 con campo token no vacío")
    void csrfDevuelveToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Cookie TSESSIONID tiene HttpOnly, SameSite=Lax, Path=/api")
    void csrfEstableceCookieConAtributosCorrectos() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).as("Debe establecer cookie de sesión")
                .isNotNull()
                .contains("TSESSIONID")
                .containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("SameSite=Lax")
                .containsIgnoringCase("Path=/api");
    }

    @Test
    @DisplayName("Cookie de sesión no tiene Domain")
    void cookieSinDomain() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie)
                .as("Cookie no debe tener Domain (host-only)")
                .doesNotContainIgnoringCase("Domain=");
    }

    @Test
    @DisplayName("Cookie de sesión no tiene Max-Age con valor positivo (es de sesión del navegador)")
    void cookieSinMaxAgePersistente() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        // La cookie de sesión no debe tener Max-Age con valor positivo
        if (setCookie != null && setCookie.toLowerCase().contains("max-age=")) {
            // Si existe Max-Age, extraer el valor y verificar que no es positivo
            String lower = setCookie.toLowerCase();
            int idx = lower.indexOf("max-age=") + "max-age=".length();
            int end = lower.indexOf(";", idx);
            String val = (end > 0) ? lower.substring(idx, end).trim() : lower.substring(idx).trim();
            long maxAge = Long.parseLong(val);
            assertThat(maxAge)
                    .as("Max-Age no debe ser positivo (no debe ser persistente)")
                    .isLessThanOrEqualTo(0L);
        }
        // Si no hay Max-Age: correcto — es cookie de sesión del navegador
    }

    @Test
    @DisplayName("GET /api/v1/auth/csrf devuelve Cache-Control: no-store")
    void csrfCacheControlNoStore() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    // =========================================================================
    // Spring Session JDBC — persistencia real
    // =========================================================================

    @Test
    @DisplayName("Sesión creada por GET /csrf tiene MAX_INACTIVE_INTERVAL de 1800 segundos en SPRING_SESSION")
    void sesionTiene1800SegundosDeInactividad() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk());

        Integer maxInactive = jdbc.queryForObject(
                "SELECT TOP 1 MAX_INACTIVE_INTERVAL FROM SPRING_SESSION ORDER BY CREATION_TIME DESC",
                Integer.class);
        assertThat(maxInactive).as("MAX_INACTIVE_INTERVAL debe ser 1800 segundos").isEqualTo(1800);
    }

    @Test
    @DisplayName("Spring Session JDBC guarda sesión real en SPRING_SESSION")
    void sesiónJdbcGuardaSesion() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk());

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
        assertThat(count).as("Debe haber al menos una sesión en SPRING_SESSION").isGreaterThan(0);
    }

    @Test
    @DisplayName("El token CSRF está almacenado como atributo de sesión en SPRING_SESSION_ATTRIBUTES")
    void csrfTokenEnAtributosDeSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk());

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME = ?",
                Integer.class, "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN");
        assertThat(count).as("El token CSRF debe estar almacenado como atributo de sesión").isGreaterThan(0);
    }

    // =========================================================================
    // CSRF — rechazo de escrituras inválidas
    // =========================================================================

    @Test
    @DisplayName("POST sin token CSRF devuelve 403 con código CSRF_INVALID")
    void postSinCsrfDevuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("POST con token CSRF inválido (header X-CSRF-TOKEN incorrecto) devuelve 403 CSRF_INVALID")
    void postConCsrfInvalidoDevuelve403() throws Exception {
        // Con Spring Session JDBC activo en RANDOM_PORT, la sesión vive en la BD, no en el
        // MockHttpServletRequest. Se usa la cookie TSESSIONID del response para correlacionar
        // la siguiente petición con la sesión existente, igual que hace un navegador real.
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie =
                csrfResult.getResponse().getCookie("TSESSIONID");
        assertThat(sessionCookie)
                .as("GET /csrf debe establecer la cookie de sesión TSESSIONID")
                .isNotNull();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(sessionCookie)
                        .header("X-CSRF-TOKEN", "token-invalido-que-no-coincide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("POST con sesión y CSRF válido supera la comprobación CSRF (llega al controlador)")
    void postConCsrfValidoSuperaFiltro() throws Exception {
        // csrf() de spring-security-test inyecta el token correcto automáticamente
        // /api/v1/auth/login aún no está implementado; esperamos cualquier código != 403
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("Con CSRF válido no debe devolver 403 (CSRF superado)")
                .isNotEqualTo(403);
    }

    // =========================================================================
    // Rutas protegidas — 401 sin redirección
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/auth/session sin sesión devuelve 401 SESSION_REQUIRED, no 302")
    void rutaProtegidaSinSesionDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Ruta protegida desconocida sin sesión devuelve 401, no 302 ni 404")
    void rutaDesconocidaSinSesionDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/ruta-protegida-que-no-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    // =========================================================================
    // Sin UserDetailsService predeterminado
    // =========================================================================

    @Test
    @DisplayName("No existe UserDetailsService predeterminado en el contexto Spring")
    void noExisteUserDetailsServicePredeterminado(
            org.springframework.context.ApplicationContext ctx) {
        // Si UserDetailsServiceAutoConfiguration no estuviera excluido,
        // Spring Boot crearía un InMemoryUserDetailsManager y generaría contraseña en el log.
        assertThat(ctx.getBeanNamesForType(
                org.springframework.security.core.userdetails.UserDetailsService.class))
                .as("No debe haber UserDetailsService auto-configurado por Spring Boot")
                .isEmpty();
    }

    // =========================================================================
    // POST /api/v1/auth/login — Integración HTTP completa con SQL Server
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/auth/login exitoso: autentica usuario real, rota sesión y persiste SecurityContext en SQL Server")
    void loginExitoso_autenticaYPersisteSesionEnSqlServer() throws Exception {
        String orgId = UUID.randomUUID().toString();
        String orgCode = "CORP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Corporación Prueba S.A.', 'ACTIVA', 'America/Lima', 10, 1)
                """, orgId, orgCode);

        String userId = UUID.randomUUID().toString();
        String email = "ana." + UUID.randomUUID().toString().substring(0, 6) + "@corpit.example";
        String plainPassword = "ClaveSegura2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Ana López', ?, 'ADMINISTRADOR', 1, 1)
                """, userId, orgId, email, email.toLowerCase(), passwordHash);

        // 1. Obtener CSRF token y cookie de sesión anónima
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie initialCookie = csrfResult.getResponse().getCookie("TSESSIONID");
        assertThat(initialCookie).isNotNull();

        tools.jackson.databind.JsonNode csrfNode = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        String csrfToken = csrfNode.get("token").asString();

        // 2. Realizar POST /api/v1/auth/login
        String loginPayload = """
                {
                  "organizationCode": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(orgCode, email, plainPassword);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.user.id").value(userId))
                .andExpect(jsonPath("$.user.displayName").value("Ana López"))
                .andExpect(jsonPath("$.user.role").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.organization.id").value(orgId))
                .andExpect(jsonPath("$.landingPage").value("/app/inicio"))
                .andReturn();

        // 3. Verificar rotación de sesión: Set-Cookie TSESSIONID emitido
        String setCookieHeader = loginResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).contains("TSESSIONID");

        // 4. Verificar persistencia de SPRING_SECURITY_CONTEXT en SQL Server
        Integer contextCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME = 'SPRING_SECURITY_CONTEXT'",
                Integer.class);
        assertThat(contextCount).as("El SecurityContext debe estar persistido en SPRING_SESSION_ATTRIBUTES").isGreaterThan(0);

        // 5. Verificar que la sesión autenticada permite acceder a rutas protegidas sin 401
        jakarta.servlet.http.Cookie authenticatedCookie = loginResult.getResponse().getCookie("TSESSIONID");
        jakarta.servlet.http.Cookie cookieToUse = authenticatedCookie != null ? authenticatedCookie : initialCookie;

        mockMvc.perform(get("/api/v1/recurso-protegido-prueba")
                        .cookie(cookieToUse))
                .andExpect(status().isNotFound()); // 404 significa que superó el filtro de seguridad (autenticado) y no dio 401
    }

    @Test
    @DisplayName("POST /api/v1/auth/login con credenciales inválidas: responde 401 AUTHENTICATION_FAILED y no crea sesión autenticada")
    void loginCredencialesInvalidas_responde401() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie cookie = csrfResult.getResponse().getCookie("TSESSIONID");
        tools.jackson.databind.JsonNode csrfNode = objectMapper.readTree(csrfResult.getResponse().getContentAsString());
        String csrfToken = csrfNode.get("token").asString();

        String payload = """
                {
                  "organizationCode": "ORG-INVENTADA",
                  "email": "desconocido@test.com",
                  "password": "wrong-password"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/authentication-failed"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Las credenciales son incorrectas o la cuenta no puede iniciar sesión."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login rate limiting: bloquea tras superar el límite de intentos por IP con 429 y Retry-After")
    void loginRateLimiting_bloqueaTrasExcesoDeIntentos() throws Exception {
        String testIp = "198.51.100." + (int)(Math.random() * 200 + 1);

        // Ejecutar 5 intentos desde la misma IP
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .with(request -> {
                                request.setRemoteAddr(testIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "organizationCode": "ORG-TEST",
                                      "email": "test@test.com",
                                      "password": "wrong"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        // El 6º intento desde la misma IP debe ser bloqueado con 429 RATE_LIMITED
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "ORG-TEST",
                                  "email": "test@test.com",
                                  "password": "wrong"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429));
    }

    // =========================================================================
    // I1-AUTH-SESSION: GET /api/v1/auth/session y POST /api/v1/auth/logout
    // =========================================================================

    @Test
    @DisplayName("Ciclo completo I1: Login (200) -> GET /session (200) -> POST /logout (204) -> GET /session (401)")
    void cicloCompleto_loginSessionLogoutVerificado() throws Exception {
        String orgId = UUID.randomUUID().toString();
        String orgCode = "CORP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Ciclo Test S.A.', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, orgCode);

        String userId = UUID.randomUUID().toString();
        String email = "ciclo." + UUID.randomUUID().toString().substring(0, 6) + "@ciclo.example";
        String plainPassword = "ClaveCiclo2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Carlos Ciclo', ?, 'ADMINISTRADOR', 1, 1)
                """, userId, orgId, email, email.toLowerCase(), passwordHash);

        // 1. Obtener CSRF y cookie inicial
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        jakarta.servlet.http.Cookie initialCookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        // 2. Login
        String loginPayload = """
                {
                  "organizationCode": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(orgCode, email, plainPassword);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie authCookie = loginResult.getResponse().getCookie("TSESSIONID");
        jakarta.servlet.http.Cookie cookieToUse = authCookie != null ? authCookie : initialCookie;

        // 3. GET /api/v1/auth/session -> 200 OK con SessionResponse
        mockMvc.perform(get("/api/v1/auth/session")
                        .cookie(cookieToUse))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.user.id").value(userId))
                .andExpect(jsonPath("$.user.displayName").value("Carlos Ciclo"))
                .andExpect(jsonPath("$.user.role").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.organization.id").value(orgId))
                .andExpect(jsonPath("$.organization.displayName").value("Ciclo Test S.A."))
                .andExpect(jsonPath("$.organization.timeZone").value("America/Bogota"))
                .andExpect(jsonPath("$.landingPage").value("/app/inicio"));

        // 4. Obtener CSRF token para el logout asociado a la sesión
        MvcResult postAuthCsrfResult = mockMvc.perform(get("/api/v1/auth/csrf")
                        .cookie(cookieToUse))
                .andExpect(status().isOk())
                .andReturn();
        String logoutCsrfToken = objectMapper.readTree(postAuthCsrfResult.getResponse().getContentAsString()).get("token").asString();

        // 5. POST /api/v1/auth/logout -> 204 No Content
        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(cookieToUse)
                        .header("X-CSRF-TOKEN", logoutCsrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        String setCookieHeader = logoutResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).as("Set-Cookie debe estar presente").isNotNull();
        assertThat(setCookieHeader).contains("TSESSIONID=");
        assertThat(setCookieHeader).contains("Max-Age=0");
        assertThat(setCookieHeader).contains("Path=/api");
        assertThat(setCookieHeader).containsIgnoringCase("HttpOnly");
        assertThat(setCookieHeader).containsIgnoringCase("SameSite=Lax");

        // 6. GET /api/v1/auth/session con la cookie vieja -> 401 SESSION_REQUIRED
        mockMvc.perform(get("/api/v1/auth/session")
                        .cookie(cookieToUse))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/session-required"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout sin sesión autenticada: responde 401 SESSION_REQUIRED")
    void logout_sinSesion_responde401() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        jakarta.servlet.http.Cookie anonymousCookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(anonymousCookie)
                        .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/session-required"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout con CSRF inválido: responde 403 CSRF_INVALID y no invalida sesión")
    void logout_conCsrfInvalido_responde403YConservaSesion() throws Exception {
        String orgId = UUID.randomUUID().toString();
        String orgCode = "CORP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Csrf Test S.A.', 'ACTIVA', 'America/Lima', 10, 1)
                """, orgId, orgCode);

        String userId = UUID.randomUUID().toString();
        String email = "csrf." + UUID.randomUUID().toString().substring(0, 6) + "@csrf.example";
        String plainPassword = "ClaveCsrf2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Laura Csrf', ?, 'ADMINISTRADOR', 1, 1)
                """, userId, orgId, email, email.toLowerCase(), passwordHash);

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        jakarta.servlet.http.Cookie initialCookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        String loginPayload = """
                {
                  "organizationCode": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(orgCode, email, plainPassword);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie authCookie = loginResult.getResponse().getCookie("TSESSIONID");
        jakarta.servlet.http.Cookie cookieToUse = authCookie != null ? authCookie : initialCookie;

        // Logout con token CSRF deliberadamente falso
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(cookieToUse)
                        .header("X-CSRF-TOKEN", "token-falso-manipulado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/csrf-invalid"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(jsonPath("$.status").value(403));

        // Verificar que la sesión permanece activa y puede acceder a GET /session
        mockMvc.perform(get("/api/v1/auth/session")
                        .cookie(cookieToUse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout con campos adicionales: responde 400 VALIDATION_FAILED y conserva sesión")
    void logout_conCamposAdicionales_responde400YConservaSesion() throws Exception {
        String orgId = UUID.randomUUID().toString();
        String orgCode = "CORP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Validation Test S.A.', 'ACTIVA', 'America/Lima', 10, 1)
                """, orgId, orgCode);

        String userId = UUID.randomUUID().toString();
        String email = "val." + UUID.randomUUID().toString().substring(0, 6) + "@val.example";
        String plainPassword = "ClaveVal2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Valeria Test', ?, 'ADMINISTRADOR', 1, 1)
                """, userId, orgId, email, email.toLowerCase(), passwordHash);

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        jakarta.servlet.http.Cookie initialCookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        String loginPayload = """
                {
                  "organizationCode": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(orgCode, email, plainPassword);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie authCookie = loginResult.getResponse().getCookie("TSESSIONID");
        jakarta.servlet.http.Cookie cookieToUse = authCookie != null ? authCookie : initialCookie;

        MvcResult postAuthCsrfResult = mockMvc.perform(get("/api/v1/auth/csrf")
                        .cookie(cookieToUse))
                .andExpect(status().isOk())
                .andReturn();
        String logoutCsrfToken = objectMapper.readTree(postAuthCsrfResult.getResponse().getContentAsString()).get("token").asString();

        // Logout con campo adicional en JSON
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(cookieToUse)
                        .header("X-CSRF-TOKEN", logoutCsrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "campoInesperado": "valor"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("El cuerpo de la petición debe estar vacío."));

        // Verificar que la sesión permanece activa
        mockMvc.perform(get("/api/v1/auth/session")
                        .cookie(cookieToUse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId));
    }

    // =========================================================================
    // I1-SESSION-VALIDATION: Revalidación por solicitud y límite de sesión
    // =========================================================================

    @Test
    @DisplayName("Revalidación IT: Usuario desactivado (is_active=0) invalida sesión y responde 401")
    void revalidacion_usuarioDesactivado_invalidaSesionYResponde401() throws Exception {
        String orgId = UUID.randomUUID().toString();
        String orgCode = "CORP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Desact Corp S.A.', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, orgCode);

        String userId = UUID.randomUUID().toString();
        String email = "desact." + UUID.randomUUID().toString().substring(0, 6) + "@test.com";
        String password = "ClaveValida2026!";
        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Usuario Desact', ?, 'ADMINISTRADOR', 1, 1)
                """, userId, orgId, email, email.toLowerCase(), passwordService.codificar(password));

        // 1. Obtener CSRF y Login exitoso
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        jakarta.servlet.http.Cookie cookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(orgCode, email, password)))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie authCookie = loginResult.getResponse().getCookie("TSESSIONID");
        jakarta.servlet.http.Cookie cookieToUse = authCookie != null ? authCookie : cookie;

        // 2. Primera petición: 200 OK
        mockMvc.perform(get("/api/v1/auth/session").cookie(cookieToUse))
                .andExpect(status().isOk());

        // 3. Desactivar usuario en base de datos
        jdbc.update("UPDATE app_users SET is_active = 0 WHERE id = ?", userId);

        // 4. Siguiente petición protegida: debe responder 401 SESSION_REQUIRED y limpiar cookie
        MvcResult rejectedResult = mockMvc.perform(get("/api/v1/auth/session").cookie(cookieToUse))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401))
                .andReturn();

        String setCookie = rejectedResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("TSESSIONID=");
        assertThat(setCookie).contains("Max-Age=0");
    }

    @Test
    @DisplayName("Revalidación IT: auth_version cambiado invalida sesión y responde 401")
    void revalidacion_authVersionModificado_invalidaSesionYResponde401() throws Exception {
        String orgId = UUID.randomUUID().toString();
        String orgCode = "CORP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'AuthVer Corp S.A.', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, orgCode);

        String userId = UUID.randomUUID().toString();
        String email = "authver." + UUID.randomUUID().toString().substring(0, 6) + "@test.com";
        String password = "ClaveValida2026!";
        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Usuario AuthVer', ?, 'ADMINISTRADOR', 1, 1)
                """, userId, orgId, email, email.toLowerCase(), passwordService.codificar(password));

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        jakarta.servlet.http.Cookie cookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(orgCode, email, password)))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie authCookie = loginResult.getResponse().getCookie("TSESSIONID");
        jakarta.servlet.http.Cookie cookieToUse = authCookie != null ? authCookie : cookie;

        // Incrementar auth_version en base de datos
        jdbc.update("UPDATE app_users SET auth_version = auth_version + 1 WHERE id = ?", userId);

        mockMvc.perform(get("/api/v1/auth/session").cookie(cookieToUse))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("Revalidación IT: Organización suspendida responde 403 ACCESS_DENIED e invalida sesión")
    void revalidacion_organizacionSuspendida_invalidaSesionYResponde403() throws Exception {
        String orgId = UUID.randomUUID().toString();
        String orgCode = "CORP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Susp Corp S.A.', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, orgCode);

        String userId = UUID.randomUUID().toString();
        String email = "susp." + UUID.randomUUID().toString().substring(0, 6) + "@test.com";
        String password = "ClaveValida2026!";
        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Usuario Susp', ?, 'ADMINISTRADOR', 1, 1)
                """, userId, orgId, email, email.toLowerCase(), passwordService.codificar(password));

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        jakarta.servlet.http.Cookie cookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(orgCode, email, password)))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie authCookie = loginResult.getResponse().getCookie("TSESSIONID");
        jakarta.servlet.http.Cookie cookieToUse = authCookie != null ? authCookie : cookie;

        // Suspender la organización
        jdbc.update("UPDATE organizations SET service_status = 'SUSPENDIDA' WHERE id = ?", orgId);

        mockMvc.perform(get("/api/v1/auth/session").cookie(cookieToUse))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403));

        // Verificar que tras ser suspendida, la sesión fue invalidada y ya no permite 200
        mockMvc.perform(get("/api/v1/auth/session").cookie(cookieToUse))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Revalidación IT: Cambio de rol actualiza SecurityContext, authorities y landingPage sin rotar sesión")
    void revalidacion_rolCambiado_actualizaAuthoritiesYLandingPage() throws Exception {
        String orgId = UUID.randomUUID().toString();
        String orgCode = "CORP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Role Corp S.A.', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, orgCode);

        String userId = UUID.randomUUID().toString();
        String email = "role." + UUID.randomUUID().toString().substring(0, 6) + "@test.com";
        String password = "ClaveValida2026!";
        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Usuario Rol', ?, 'ADMINISTRADOR', 1, 1)
                """, userId, orgId, email, email.toLowerCase(), passwordService.codificar(password));

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        jakarta.servlet.http.Cookie cookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(orgCode, email, password)))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie authCookie = loginResult.getResponse().getCookie("TSESSIONID");
        jakarta.servlet.http.Cookie cookieToUse = authCookie != null ? authCookie : cookie;

        // Comprobar estado inicial: ADMINISTRADOR y landingPage /app/inicio
        mockMvc.perform(get("/api/v1/auth/session").cookie(cookieToUse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.landingPage").value("/app/inicio"));

        // Cambiar rol a COLABORADOR en base de datos
        jdbc.update("UPDATE app_users SET role_code = 'COLABORADOR' WHERE id = ?", userId);

        // Siguiente petición: responde 200 OK con el nuevo rol y landingPage /app/mi-horario
        MvcResult updateResult = mockMvc.perform(get("/api/v1/auth/session").cookie(cookieToUse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("COLABORADOR"))
                .andExpect(jsonPath("$.landingPage").value("/app/mi-horario"))
                .andReturn();

        // No debe haber rotado la sesión (no emite nuevo Set-Cookie de sesión)
        String setCookieHeader = updateResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNull();
    }

    @Test
    @DisplayName("Revalidación IT: Atributo authenticatedAt ausente en sesión invalida sesión y responde 401")
    void revalidacion_authenticatedAtAusenteEnSesion_invalidaSesionYResponde401() throws Exception {
        String orgId = UUID.randomUUID().toString();
        String orgCode = "CORP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'NoAuthAt Corp S.A.', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, orgCode);

        String userId = UUID.randomUUID().toString();
        String email = "noauth." + UUID.randomUUID().toString().substring(0, 6) + "@test.com";
        String password = "ClaveValida2026!";
        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Usuario NoAuthAt', ?, 'ADMINISTRADOR', 1, 1)
                """, userId, orgId, email, email.toLowerCase(), passwordService.codificar(password));

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        jakarta.servlet.http.Cookie cookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(orgCode, email, password)))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie authCookie = loginResult.getResponse().getCookie("TSESSIONID");
        jakarta.servlet.http.Cookie cookieToUse = authCookie != null ? authCookie : cookie;

        // Eliminar el atributo authenticatedAt de la tabla SPRING_SESSION_ATTRIBUTES
        jdbc.update("DELETE FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME = ?",
                com.turnoshift.identity.domain.SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE);

        // La siguiente petición protegida debe detectar la ausencia, invalidar y devolver 401
        mockMvc.perform(get("/api/v1/auth/session").cookie(cookieToUse))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }
}



