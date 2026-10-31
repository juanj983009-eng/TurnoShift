package com.turnoshift.organization.infrastructure.web;

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

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("IT: OrganizationConfigurationController — HTTP Seguro e Idempotente con SQL Server")
class OrganizationConfigurationControllerIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        registry.add("server.servlet.session.cookie.secure", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Argon2PasswordService passwordService;

    record UserSession(UUID orgId, String orgCode, UUID userId, String email, Cookie sessionCookie, String csrfToken, UUID siteId, String siteCode) {}
    record OperatorSession(UUID opId, Cookie sessionCookie, String csrfToken) {}

    private UserSession createAndLoginUser(String role) throws Exception {
        UUID orgId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String orgCode = "ORG-HTTP-" + suffix;
        String orgDisplayName = "Org " + suffix;

        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites, lock_version)
                VALUES (?, ?, ?, 'ACTIVA', 'America/Bogota', 10, 1, 0)
                """, orgId, orgCode, orgDisplayName);

        UUID siteId = UUID.randomUUID();
        String siteCode = "SITE-" + suffix;
        String siteName = "Sede Central " + suffix;
        jdbc.update("""
                INSERT INTO sites (organization_id, id, code, name, is_active, lock_version)
                VALUES (?, ?, ?, ?, 1, 0)
                """, orgId, siteId, siteCode, siteName);

        UUID userId = UUID.randomUUID();
        String email = "user." + suffix.toLowerCase(Locale.ROOT) + "@empresa.example";
        String plainPassword = "PasswordSegura2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name,
                    password_hash, role_code, is_active, auth_version, lock_version)
                VALUES (?, ?, ?, ?, 'Usuario Test', ?, ?, 1, 1, 0)
                """, userId, orgId, email, email.toLowerCase(Locale.ROOT), passwordHash, role);

        // 1. Obtener token CSRF y cookie anónima inicial
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie initialCookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String initialCsrf = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        // 2. Ejecutar login
        String loginJson = """
                {
                  "organizationCode": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(orgCode, email, plainPassword);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", initialCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        Cookie authCookie = loginResult.getResponse().getCookie("TSESSIONID");
        Cookie cookieToUse = authCookie != null ? authCookie : initialCookie;

        // 3. Obtener token CSRF para la sesión autenticada
        MvcResult authCsrfResult = mockMvc.perform(get("/api/v1/auth/csrf").cookie(cookieToUse))
                .andExpect(status().isOk())
                .andReturn();
        String authCsrf = objectMapper.readTree(authCsrfResult.getResponse().getContentAsString()).get("token").asString();

        return new UserSession(orgId, orgCode, userId, email, cookieToUse, authCsrf, siteId, siteCode);
    }

    private OperatorSession createAndLoginOperator() throws Exception {
        UUID opId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String email = "op." + suffix.toLowerCase(Locale.ROOT) + "@turnoshift.example";
        String plainPassword = "OpPasswordSegura2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO platform_operators (id, email, normalized_email, display_name, password_hash, is_active, auth_version)
                VALUES (?, ?, ?, 'Operador Test', ?, 1, 1)
                """, opId, email, email.toLowerCase(Locale.ROOT), passwordHash);

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie initialCookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String initialCsrf = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        String loginJson = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, plainPassword);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/operator/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", initialCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        Cookie authCookie = loginResult.getResponse().getCookie("TSESSIONID");
        Cookie cookieToUse = authCookie != null ? authCookie : initialCookie;

        MvcResult authCsrfResult = mockMvc.perform(get("/api/v1/auth/csrf").cookie(cookieToUse))
                .andExpect(status().isOk())
                .andReturn();
        String authCsrf = objectMapper.readTree(authCsrfResult.getResponse().getContentAsString()).get("token").asString();

        return new OperatorSession(opId, cookieToUse, authCsrf);
    }

    // =========================================================================
    // 1. Lectura aislada entre dos organizaciones
    // =========================================================================

    @Test
    @DisplayName("1. Lectura aislada entre dos organizaciones: no se mezclan datos")
    void lecturaAislada_dosOrganizaciones() throws Exception {
        UserSession adminA = createAndLoginUser("ADMINISTRADOR");
        UserSession adminB = createAndLoginUser("ADMINISTRADOR");

        // Lectura Org A
        mockMvc.perform(get("/api/v1/organization/configuration")
                        .cookie(adminA.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.organization.id").value(adminA.orgId().toString()))
                .andExpect(jsonPath("$.organization.displayName").value("Org " + adminA.orgCode().substring(9)))
                .andExpect(jsonPath("$.organization.timezoneId").value("America/Bogota"))
                .andExpect(jsonPath("$.organization.lockVersion").value(0))
                .andExpect(jsonPath("$.activeSite.id").value(adminA.siteId().toString()))
                .andExpect(jsonPath("$.activeSite.code").value(adminA.siteCode()))
                .andExpect(jsonPath("$.activeSite.lockVersion").value(0))
                .andExpect(jsonPath("$.service_status").doesNotExist())
                .andExpect(jsonPath("$.max_active_employees").doesNotExist());

        // Lectura Org B
        mockMvc.perform(get("/api/v1/organization/configuration")
                        .cookie(adminB.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization.id").value(adminB.orgId().toString()))
                .andExpect(jsonPath("$.organization.displayName").value("Org " + adminB.orgCode().substring(9)))
                .andExpect(jsonPath("$.activeSite.id").value(adminB.siteId().toString()))
                .andExpect(jsonPath("$.activeSite.code").value(adminB.siteCode()));
    }

    // =========================================================================
    // 2. Admin actualiza displayName e idempotencia no incrementa versión de nuevo
    // =========================================================================

    @Test
    @DisplayName("2. Admin actualiza displayName y replay con misma clave devuelve 200 sin duplicar incremento")
    void adminActualizaDisplayName_replaySinIncrementoExtra() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID key = UUID.randomUUID();

        String payload = """
                {
                  "displayName": "Nuevo Nombre Empresa",
                  "expectedLockVersion": 0
                }
                """;

        // Primer intento: exitoso 200
        mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.id").value(admin.orgId().toString()))
                .andExpect(jsonPath("$.displayName").value("Nuevo Nombre Empresa"))
                .andExpect(jsonPath("$.lockVersion").value(1));

        // Verificar en BD
        Map<String, Object> dbRow = jdbc.queryForMap("SELECT display_name, lock_version FROM organizations WHERE id = ?", admin.orgId());
        assertThat(dbRow.get("display_name")).isEqualTo("Nuevo Nombre Empresa");
        assertThat(((Number) dbRow.get("lock_version")).longValue()).isEqualTo(1L);

        // Replay con la misma clave y payload: devuelve 200 con Idempotency-Replayed: true
        mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.displayName").value("Nuevo Nombre Empresa"))
                .andExpect(jsonPath("$.lockVersion").value(1));

        // En BD sigue siendo versión 1 (no se incrementó de nuevo)
        Map<String, Object> dbRowReplay = jdbc.queryForMap("SELECT display_name, lock_version FROM organizations WHERE id = ?", admin.orgId());
        assertThat(((Number) dbRowReplay.get("lock_version")).longValue()).isEqualTo(1L);
    }

    // =========================================================================
    // 3. Admin actualiza nombre de sede activa y replay no duplica incremento
    // =========================================================================

    @Test
    @DisplayName("3. Admin actualiza nombre de sede activa y replay devuelve 200 sin incrementar versión otra vez")
    void adminActualizaSedeActiva_replaySinIncrementoExtra() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID key = UUID.randomUUID();

        String payload = """
                {
                  "name": "Sede Central Renovada",
                  "expectedLockVersion": 0
                }
                """;

        // Primer intento
        mockMvc.perform(patch("/api/v1/organization/configuration/active-site/name")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.id").value(admin.siteId().toString()))
                .andExpect(jsonPath("$.code").value(admin.siteCode()))
                .andExpect(jsonPath("$.name").value("Sede Central Renovada"))
                .andExpect(jsonPath("$.lockVersion").value(1));

        // Replay
        mockMvc.perform(patch("/api/v1/organization/configuration/active-site/name")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.name").value("Sede Central Renovada"))
                .andExpect(jsonPath("$.lockVersion").value(1));

        // Verificar BD
        Map<String, Object> siteRow = jdbc.queryForMap("SELECT name, lock_version, is_active FROM sites WHERE id = ?", admin.siteId());
        assertThat(siteRow.get("name")).isEqualTo("Sede Central Renovada");
        assertThat(((Number) siteRow.get("lock_version")).longValue()).isEqualTo(1L);
        assertThat(siteRow.get("is_active")).isEqualTo(true);
    }

    // =========================================================================
    // 4. Rechazo por conflicto de versión se persiste en api_operations y se replaya
    // =========================================================================

    @Test
    @DisplayName("4. Conflicto de versión (409) se persiste en api_operations y se replaya sin filtrar secretos")
    void rechazoPorConcurrencia_sePersisteYReplaya() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID keyFirst = UUID.randomUUID();

        // Incrementamos la versión de la organización a 1
        mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", keyFirst.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Primer Cambio\",\"expectedLockVersion\":0}"))
                .andExpect(status().isOk());

        // Ahora intentamos con una clave nueva pero con expectedLockVersion = 0 (obsoleta)
        UUID keyStale = UUID.randomUUID();
        String stalePayload = "{\"displayName\":\"Cambio En Conflicto\",\"expectedLockVersion\":0}";

        mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", keyStale.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stalePayload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        // Verificar que en api_operations quedó guardado como REJECTED 409
        Map<String, Object> opRow = jdbc.queryForMap(
                "SELECT outcome, http_status, response_body, request_path FROM api_operations WHERE idempotency_key = ?",
                keyStale
        );
        assertThat(opRow.get("outcome")).isEqualTo("REJECTED");
        assertThat(((Number) opRow.get("http_status")).intValue()).isEqualTo(409);
        assertThat(opRow.get("request_path")).isEqualTo("/api/v1/organization/configuration/display-name");
        String responseBody = (String) opRow.get("response_body");
        assertThat(responseBody).contains("CONCURRENT_MODIFICATION");
        assertThat(responseBody).doesNotContain("requestId").doesNotContain("instance");

        // Replay del rechazo
        mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", keyStale.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stalePayload))
                .andExpect(status().isConflict())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    // =========================================================================
    // 5. Clave reutilizada con payload distinto devuelve 409 IDEMPOTENCY_KEY_REUSED
    // =========================================================================

    @Test
    @DisplayName("5. Clave reutilizada con payload distinto devuelve 409 IDEMPOTENCY_KEY_REUSED")
    void claveReutilizadaConDistintoPayload_devuelve409() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID key = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nombre Uno\",\"expectedLockVersion\":0}"))
                .andExpect(status().isOk());

        // Misma clave con payload modificado
        mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nombre Dos\",\"expectedLockVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    // =========================================================================
    // 6. Error técnico en persistencia devuelve 503 y aplica rollback total
    // =========================================================================

    @Test
    @DisplayName("6. Error técnico en persistencia devuelve 503 y aplica rollback total")
    void errorTecnicoPersistencia_devuelve503YRollback() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID keyFail = UUID.randomUUID();

        // Trigger temporal que fuerza error en UPDATE de organizations
        jdbc.execute("CREATE TRIGGER trg_fail_org_update ON organizations AFTER UPDATE AS BEGIN RAISERROR('Fallo forzado en organizaciones', 16, 1); END;");

        try {
            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .cookie(admin.sessionCookie())
                            .header("X-CSRF-TOKEN", admin.csrfToken())
                            .header("Idempotency-Key", keyFail.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Nombre Error\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

            // Verificar que no se alteró la organización (rollback)
            Map<String, Object> orgRow = jdbc.queryForMap(
                    "SELECT display_name, lock_version FROM organizations WHERE id = ?",
                    admin.orgId()
            );
            assertThat(orgRow.get("display_name")).isNotEqualTo("Nombre Error");
            assertThat(((Number) orgRow.get("lock_version")).longValue()).isEqualTo(0L);

            // Verificar que no quedó fila residual en api_operations (rollback)
            Integer countOp = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM api_operations WHERE idempotency_key = ?",
                    Integer.class,
                    keyFail
            );
            assertThat(countOp).isEqualTo(0);
        } finally {
            jdbc.execute("IF OBJECT_ID('trg_fail_org_update', 'TR') IS NOT NULL DROP TRIGGER trg_fail_org_update;");
        }
    }

    // =========================================================================
    // 7. Pruebas de Autorización y Seguridad (Supervisor, Operador, Sin Sesión, CSRF)
    // =========================================================================

    @Test
    @DisplayName("7. Seguridad: supervisor puede leer pero recibe 403 en PATCH")
    void seguridad_supervisorPuedeLeerPeroNoEscribir() throws Exception {
        UserSession supervisor = createAndLoginUser("SUPERVISOR");

        // Lectura permitida
        mockMvc.perform(get("/api/v1/organization/configuration")
                        .cookie(supervisor.sessionCookie()))
                .andExpect(status().isOk());

        // Escritura denegada
        mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Intento Supervisor\",\"expectedLockVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("8. Seguridad: petición de administrador sin CSRF recibe 403 CSRF_INVALID")
    void seguridad_adminSinCsrf_recibe403() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                        .cookie(admin.sessionCookie())
                        // Sin cabecera X-CSRF-TOKEN
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Intento Sin CSRF\",\"expectedLockVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("9. Seguridad: operador de plataforma recibe 403 ACCESS_DENIED en configuración")
    void seguridad_operadorPlataforma_recibe403() throws Exception {
        OperatorSession op = createAndLoginOperator();

        mockMvc.perform(get("/api/v1/organization/configuration")
                        .cookie(op.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("10. Seguridad: petición anónima sin sesión recibe 401 SESSION_REQUIRED")
    void seguridad_sinSesion_recibe401() throws Exception {
        mockMvc.perform(get("/api/v1/organization/configuration"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }
}
