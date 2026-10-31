package com.turnoshift.team.infrastructure.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("IT: PositionController — HTTP Seguro e Idempotente de Puestos con SQL Server")
class PositionControllerIT {

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

    private record UserSession(
            UUID orgId,
            String orgCode,
            UUID userId,
            String email,
            Cookie sessionCookie,
            String csrfToken
    ) {}

    private record OperatorSession(
            UUID operatorId,
            Cookie sessionCookie,
            String csrfToken
    ) {}

    private UserSession createAndLoginUser(String role) throws Exception {
        UUID orgId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String orgCode = "ORG-" + suffix;

        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Org Test', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, orgCode);

        UUID userId = UUID.randomUUID();
        String email = "user." + suffix.toLowerCase(Locale.ROOT) + "@empresa.example";
        String plainPassword = "PasswordSegura2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name,
                    password_hash, role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Usuario Test', ?, ?, 1, 1)
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

        return new UserSession(orgId, orgCode, userId, email, cookieToUse, authCsrf);
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
    // 1. Administrador autenticado con CSRF crea puesto y replay no duplica
    // =========================================================================

    @Test
    @DisplayName("1. Administrador autenticado con CSRF crea puesto y un replay no duplica filas")
    void adminAutenticadoConCsrf_creaPuestoYReplayNoDuplicaFilas() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID idempotencyKey = UUID.randomUUID();

        String payload = """
                {
                  "code": "adm-01",
                  "name": "Administrativo"
                }
                """;

        // Primer envío: 201 Created
        mockMvc.perform(post("/api/v1/positions")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.code").value("ADM-01"))
                .andExpect(jsonPath("$.name").value("Administrativo"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.lockVersion").value(0));

        // Verificar BD
        Integer countPositions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM positions WHERE organization_id = ? AND code = 'ADM-01'",
                Integer.class, admin.orgId());
        assertThat(countPositions).isEqualTo(1);

        Map<String, Object> op = jdbc.queryForMap(
                "SELECT status, outcome, actor_user_id, actor_operator_id FROM api_operations WHERE idempotency_key = ?",
                idempotencyKey);
        assertThat(op.get("status")).isEqualTo("COMPLETED");
        assertThat(op.get("outcome")).isEqualTo("SUCCEEDED");
        assertThat(UUID.fromString(op.get("actor_user_id").toString())).isEqualTo(admin.userId());
        assertThat(op.get("actor_operator_id")).isNull();

        // Replay con misma clave y mismo payload: 201 Created con Idempotency-Replayed
        mockMvc.perform(post("/api/v1/positions")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("ADM-01"));

        // No duplica filas en BD
        Integer countPositionsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM positions WHERE organization_id = ? AND code = 'ADM-01'",
                Integer.class, admin.orgId());
        assertThat(countPositionsAfter).isEqualTo(1);
    }

    // =========================================================================
    // 2. Supervisor puede GET pero recibe 403 en escritura
    // =========================================================================

    @Test
    @DisplayName("2. Supervisor puede GET pero recibe 403 en escritura")
    void supervisor_puedeGetPeroRecibe403EnEscritura() throws Exception {
        UserSession supervisor = createAndLoginUser("SUPERVISOR");

        // GET permitido
        mockMvc.perform(get("/api/v1/positions")
                        .cookie(supervisor.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        // POST denegado
        mockMvc.perform(post("/api/v1/positions")
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SUP-01\",\"name\":\"Supervisor Puesto\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // PUT denegado
        mockMvc.perform(put("/api/v1/positions/" + UUID.randomUUID())
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // PATCH denegado
        mockMvc.perform(patch("/api/v1/positions/" + UUID.randomUUID() + "/status")
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false,\"expectedLockVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // =========================================================================
    // 3. Sin CSRF en escritura devuelve 403 CSRF_INVALID
    // =========================================================================

    @Test
    @DisplayName("3. Sin CSRF en escritura devuelve 403 CSRF_INVALID")
    void sinCsrfEnEscritura_devuelve403CsrfInvalid() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        mockMvc.perform(post("/api/v1/positions")
                        .cookie(admin.sessionCookie())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DOC-01\",\"name\":\"Doctor\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM positions WHERE code = 'DOC-01'", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    // =========================================================================
    // 4. Sin sesión devuelve 401 SESSION_REQUIRED
    // =========================================================================

    @Test
    @DisplayName("4. Sin sesión devuelve 401 SESSION_REQUIRED")
    void sinSesion_devuelve401SessionRequired() throws Exception {
        mockMvc.perform(get("/api/v1/positions"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));

        mockMvc.perform(post("/api/v1/positions")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DOC-01\",\"name\":\"Doctor\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    // =========================================================================
    // 5. Operador de plataforma no accede
    // =========================================================================

    @Test
    @DisplayName("5. Operador de plataforma no accede (403 ACCESS_DENIED)")
    void operadorDePlataforma_noAccede() throws Exception {
        OperatorSession operator = createAndLoginOperator();

        mockMvc.perform(get("/api/v1/positions")
                        .cookie(operator.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/positions")
                        .cookie(operator.sessionCookie())
                        .header("X-CSRF-TOKEN", operator.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"OP-01\",\"name\":\"Operador Puesto\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // =========================================================================
    // 6. Mismo Idempotency-Key con payload distinto devuelve 409
    // =========================================================================

    @Test
    @DisplayName("6. Mismo Idempotency-Key con payload distinto devuelve 409 IDEMPOTENCY_KEY_REUSED")
    void mismoIdempotencyKey_conPayloadDistinto_devuelve409Reused() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID sharedKey = UUID.randomUUID();

        // 1. Envío inicial exitoso
        mockMvc.perform(post("/api/v1/positions")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", sharedKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AUX-01\",\"name\":\"Auxiliar Uno\"}"))
                .andExpect(status().isCreated());

        // 2. Reutilización con payload diferente
        mockMvc.perform(post("/api/v1/positions")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", sharedKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AUX-02\",\"name\":\"Auxiliar Dos\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // AUX-02 no se insertó
        Integer countAux2 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM positions WHERE code = 'AUX-02'", Integer.class);
        assertThat(countAux2).isEqualTo(0);
    }

    // =========================================================================
    // 7. Rechazos deterministas se guardan/reproducen como REJECTED sin sensibles
    // =========================================================================

    @Test
    @DisplayName("7. Conflicto de código, not found y versión desactualizada se guardan y reproducen como REJECTED sin requestId/instance persistidos")
    void rechazosDeterministas_seGuardanYReproducenComoRejectedSinCamposSensibles() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        // 7a. Conflicto de código
        UUID key1 = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/positions")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key1.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TEC-01\",\"name\":\"Técnico\"}"))
                .andExpect(status().isCreated());

        UUID keyConflict = UUID.randomUUID();
        String conflictPayload = "{\"code\":\"TEC-01\",\"name\":\"Otro Técnico\"}";

        mockMvc.perform(post("/api/v1/positions")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", keyConflict.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POSITION_CODE_ALREADY_EXISTS"));

        // Inspeccionar registro persistido de keyConflict
        Map<String, Object> opConflict = jdbc.queryForMap(
                "SELECT outcome, http_status, response_content_type, response_body FROM api_operations WHERE idempotency_key = ?",
                keyConflict);
        assertThat(opConflict.get("outcome")).isEqualTo("REJECTED");
        assertThat(opConflict.get("http_status")).isEqualTo(409);
        assertThat(opConflict.get("response_content_type")).isEqualTo("application/problem+json");
        String bodyConflict = (String) opConflict.get("response_body");
        assertThat(bodyConflict).contains("POSITION_CODE_ALREADY_EXISTS");
        assertThat(bodyConflict).doesNotContain("requestId");
        assertThat(bodyConflict).doesNotContain("instance");
        assertThat(bodyConflict).doesNotContain("password");
        assertThat(bodyConflict).doesNotContain("token");

        // Replay de conflicto genera nuevo requestId e instance
        mockMvc.perform(post("/api/v1/positions")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", keyConflict.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictPayload))
                .andExpect(status().isConflict())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("POSITION_CODE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/v1/positions"));

        // 7b. Not Found
        UUID keyNotFound = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();
        String putNotFoundPayload = "{\"name\":\"Nombre Actualizado\",\"expectedLockVersion\":0}";

        mockMvc.perform(put("/api/v1/positions/" + nonExistentId)
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", keyNotFound.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putNotFoundPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSITION_NOT_FOUND"));

        Map<String, Object> opNotFound = jdbc.queryForMap(
                "SELECT outcome, http_status, response_body FROM api_operations WHERE idempotency_key = ?",
                keyNotFound);
        assertThat(opNotFound.get("outcome")).isEqualTo("REJECTED");
        assertThat(opNotFound.get("http_status")).isEqualTo(404);
        assertThat(((String) opNotFound.get("response_body"))).doesNotContain("requestId").doesNotContain("instance");

        // Replay de not found
        mockMvc.perform(put("/api/v1/positions/" + nonExistentId)
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", keyNotFound.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putNotFoundPayload))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("POSITION_NOT_FOUND"));

        // 7c. Versión desactualizada
        UUID tecId = UUID.fromString(jdbc.queryForObject(
                "SELECT id FROM positions WHERE organization_id = ? AND code = 'TEC-01'",
                String.class, admin.orgId()));

        // Actualizar exitosamente para avanzar lock_version a 1
        mockMvc.perform(put("/api/v1/positions/" + tecId)
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Técnico Nivel 1\",\"expectedLockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockVersion").value(1));

        // Intento con versión obsoleta 0
        UUID keyStale = UUID.randomUUID();
        String stalePayload = "{\"name\":\"Técnico Conflicto\",\"expectedLockVersion\":0}";

        mockMvc.perform(put("/api/v1/positions/" + tecId)
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", keyStale.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stalePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        Map<String, Object> opStale = jdbc.queryForMap(
                "SELECT outcome, http_status, response_body FROM api_operations WHERE idempotency_key = ?",
                keyStale);
        assertThat(opStale.get("outcome")).isEqualTo("REJECTED");
        assertThat(opStale.get("http_status")).isEqualTo(409);
        assertThat(((String) opStale.get("response_body"))).doesNotContain("requestId").doesNotContain("instance");

        // Replay de concurrencia
        mockMvc.perform(put("/api/v1/positions/" + tecId)
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", keyStale.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stalePayload))
                .andExpect(status().isConflict())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // =========================================================================
    // 8. Error de infraestructura devuelve 503 y rollback
    // =========================================================================

    @Test
    @DisplayName("8. Error de infraestructura devuelve 503 y rollback total")
    void errorInfraestructura_devuelve503YRollback() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID keyFail = UUID.randomUUID();

        // Creamos un trigger que fuerza error en INSERT
        jdbc.execute("CREATE TRIGGER trg_fail_positions ON positions AFTER INSERT AS BEGIN RAISERROR('Fallo forzado', 16, 1); END;");

        try {
            mockMvc.perform(post("/api/v1/positions")
                            .cookie(admin.sessionCookie())
                            .header("X-CSRF-TOKEN", admin.csrfToken())
                            .header("Idempotency-Key", keyFail.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"ERR-01\",\"name\":\"Error Test\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

            // Verificar que no se persistió la posición ni la operación (rollback)
            Integer countPos = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM positions WHERE code = 'ERR-01'", Integer.class);
            assertThat(countPos).isEqualTo(0);

            Integer countOp = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM api_operations WHERE idempotency_key = ?",
                    Integer.class, keyFail);
            assertThat(countOp).isEqualTo(0);
        } finally {
            jdbc.execute("IF OBJECT_ID('trg_fail_positions', 'TR') IS NOT NULL DROP TRIGGER trg_fail_positions;");
        }
    }

    // =========================================================================
    // 9. Lectura aislada por organización
    // =========================================================================

    @Test
    @DisplayName("9. Lectura aislada por organización")
    void lecturaAisladaPorOrganizacion() throws Exception {
        UserSession adminA = createAndLoginUser("ADMINISTRADOR");
        UserSession adminB = createAndLoginUser("ADMINISTRADOR");

        // Crear puesto en Org A
        mockMvc.perform(post("/api/v1/positions")
                        .cookie(adminA.sessionCookie())
                        .header("X-CSRF-TOKEN", adminA.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MED-A\",\"name\":\"Médico Org A\"}"))
                .andExpect(status().isCreated());

        // Crear puesto en Org B
        mockMvc.perform(post("/api/v1/positions")
                        .cookie(adminB.sessionCookie())
                        .header("X-CSRF-TOKEN", adminB.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MED-B\",\"name\":\"Médico Org B\"}"))
                .andExpect(status().isCreated());

        // Admin A solo ve su puesto
        MvcResult resA = mockMvc.perform(get("/api/v1/positions").cookie(adminA.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn();
        tools.jackson.databind.JsonNode nodeA = objectMapper.readTree(resA.getResponse().getContentAsString());
        assertThat(nodeA.isArray()).isTrue();
        java.util.List<String> codesA = new java.util.ArrayList<>();
        for (tools.jackson.databind.JsonNode item : nodeA) {
            codesA.add(item.get("code").asString());
        }
        assertThat(codesA).contains("MED-A").doesNotContain("MED-B");

        // Admin B solo ve su puesto
        MvcResult resB = mockMvc.perform(get("/api/v1/positions").cookie(adminB.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn();
        tools.jackson.databind.JsonNode nodeB = objectMapper.readTree(resB.getResponse().getContentAsString());
        assertThat(nodeB.isArray()).isTrue();
        java.util.List<String> codesB = new java.util.ArrayList<>();
        for (tools.jackson.databind.JsonNode item : nodeB) {
            codesB.add(item.get("code").asString());
        }
        assertThat(codesB).contains("MED-B").doesNotContain("MED-A");
    }
}
