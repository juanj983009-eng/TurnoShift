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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
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
@DisplayName("IT: EmployeeController — HTTP Seguro e Idempotente de Colaboradores con SQL Server")
class EmployeeControllerIT {

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
            String csrfToken,
            UUID siteId,
            UUID positionId
    ) {}

    private record OperatorSession(
            UUID operatorId,
            Cookie sessionCookie,
            String csrfToken
    ) {}

    private UserSession createAndLoginUser(String role) throws Exception {
        return createAndLoginUserWithLimits(role, 10);
    }

    private UserSession createAndLoginUserWithLimits(String role, int maxEmployees) throws Exception {
        UUID orgId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String orgCode = "ORG-" + suffix;

        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Org Test', 'ACTIVA', 'America/Bogota', ?, 1)
                """, orgId, orgCode, maxEmployees);

        UUID siteId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sites (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, 'Sede Principal', 1)
                """, siteId, orgId, "SEDE-" + suffix);

        UUID positionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO positions (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, 'Puesto General', 1)
                """, positionId, orgId, "POS-" + suffix);

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

        // 2. Login
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

        return new UserSession(orgId, orgCode, userId, email, cookieToUse, authCsrf, siteId, positionId);
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
    // 1. Administrador autenticado con CSRF crea colaborador y replay no duplica
    // =========================================================================

    @Test
    @DisplayName("1. Administrador autenticado con CSRF crea colaborador y un replay no duplica filas")
    void adminAutenticadoConCsrf_creaColaboradorYReplayNoDuplicaFilas() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID idempotencyKey = UUID.randomUUID();

        String payload = """
                {
                  "code": "EMP-001",
                  "displayName": "Carlos Mendoza",
                  "positionId": "%s"
                }
                """.formatted(admin.positionId());

        // Primer envío: 201 Created
        mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.code").value("EMP-001"))
                .andExpect(jsonPath("$.displayName").value("Carlos Mendoza"))
                .andExpect(jsonPath("$.positionId").value(admin.positionId().toString()))
                .andExpect(jsonPath("$.siteId").value(admin.siteId().toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.lockVersion").value(0));

        // Verificar BD
        Integer countEmployees = jdbc.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE organization_id = ? AND code = 'EMP-001'",
                Integer.class, admin.orgId());
        assertThat(countEmployees).isEqualTo(1);

        Map<String, Object> op = jdbc.queryForMap(
                "SELECT status, outcome, actor_user_id, actor_operator_id FROM api_operations WHERE idempotency_key = ?",
                idempotencyKey);
        assertThat(op.get("status")).isEqualTo("COMPLETED");
        assertThat(op.get("outcome")).isEqualTo("SUCCEEDED");
        assertThat(UUID.fromString(op.get("actor_user_id").toString())).isEqualTo(admin.userId());
        assertThat(op.get("actor_operator_id")).isNull();

        // Replay con misma clave y mismo payload: 201 Created con Idempotency-Replayed
        mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("EMP-001"));

        // No duplica filas en BD
        Integer countEmployeesAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE organization_id = ? AND code = 'EMP-001'",
                Integer.class, admin.orgId());
        assertThat(countEmployeesAfter).isEqualTo(1);
    }

    // =========================================================================
    // 2. Supervisor puede GET pero recibe 403 en escritura
    // =========================================================================

    @Test
    @DisplayName("2. Supervisor puede GET pero recibe 403 en escritura")
    void supervisor_puedeGetPeroRecibe403EnEscritura() throws Exception {
        UserSession supervisor = createAndLoginUser("SUPERVISOR");

        // GET permitido
        mockMvc.perform(get("/api/v1/employees")
                        .cookie(supervisor.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        // POST denegado
        mockMvc.perform(post("/api/v1/employees")
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SUP-01\",\"displayName\":\"Supervisor Emp\",\"positionId\":\"" + supervisor.positionId() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // PATCH denegado
        mockMvc.perform(patch("/api/v1/employees/" + UUID.randomUUID())
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // POST activate denegado
        mockMvc.perform(post("/api/v1/employees/" + UUID.randomUUID() + "/activate")
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // POST deactivate denegado
        mockMvc.perform(post("/api/v1/employees/" + UUID.randomUUID() + "/deactivate")
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // POST deactivation-preview denegado
        mockMvc.perform(post("/api/v1/employees/" + UUID.randomUUID() + "/deactivation-preview")
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // =========================================================================
    // 3. Colaborador recibe 403 en todas las rutas /api/v1/employees/**
    // =========================================================================

    @Test
    @DisplayName("3. Colaborador recibe 403 ACCESS_DENIED en todas las rutas de empleados")
    void colaborador_recibe403EnTodasLasRutas() throws Exception {
        UserSession colab = createAndLoginUser("COLABORADOR");

        // GET /employees
        mockMvc.perform(get("/api/v1/employees").cookie(colab.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // GET /employees/{id}
        mockMvc.perform(get("/api/v1/employees/" + UUID.randomUUID()).cookie(colab.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // POST /employees
        mockMvc.perform(post("/api/v1/employees")
                        .cookie(colab.sessionCookie())
                        .header("X-CSRF-TOKEN", colab.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"COL-01\",\"displayName\":\"Colaborador Test\",\"positionId\":\"" + colab.positionId() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // PATCH /employees/{id}
        mockMvc.perform(patch("/api/v1/employees/" + UUID.randomUUID())
                        .cookie(colab.sessionCookie())
                        .header("X-CSRF-TOKEN", colab.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // =========================================================================
    // 4. Sin CSRF en escritura devuelve 403 CSRF_INVALID
    // =========================================================================

    @Test
    @DisplayName("4. Sin CSRF en escritura devuelve 403 CSRF_INVALID")
    void sinCsrfEnEscritura_devuelve403CsrfInvalid() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOCSRF-01\",\"displayName\":\"Sin CSRF\",\"positionId\":\"" + admin.positionId() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE code = 'NOCSRF-01'", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    // =========================================================================
    // 5. Sin sesión devuelve 401 SESSION_REQUIRED
    // =========================================================================

    @Test
    @DisplayName("5. Sin sesión devuelve 401 SESSION_REQUIRED")
    void sinSesion_devuelve401SessionRequired() throws Exception {
        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));

        mockMvc.perform(post("/api/v1/employees")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOAUTH-01\",\"displayName\":\"No Auth\",\"positionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    // =========================================================================
    // 6. Operador de plataforma no accede
    // =========================================================================

    @Test
    @DisplayName("6. Operador de plataforma no accede (403 ACCESS_DENIED)")
    void operadorDePlataforma_noAccede() throws Exception {
        OperatorSession operator = createAndLoginOperator();

        mockMvc.perform(get("/api/v1/employees")
                        .cookie(operator.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/employees")
                        .cookie(operator.sessionCookie())
                        .header("X-CSRF-TOKEN", operator.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"OP-01\",\"displayName\":\"Operador Emp\",\"positionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // =========================================================================
    // 7. Mismo Idempotency-Key con payload distinto devuelve 409
    // =========================================================================

    @Test
    @DisplayName("7. Mismo Idempotency-Key con payload distinto devuelve 409 IDEMPOTENCY_KEY_REUSED")
    void mismoIdempotencyKey_conPayloadDistinto_devuelve409Reused() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID sharedKey = UUID.randomUUID();

        // 1. Envío inicial exitoso
        mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", sharedKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-A\",\"displayName\":\"Empleado A\",\"positionId\":\"" + admin.positionId() + "\"}"))
                .andExpect(status().isCreated());

        // 2. Reutilización con payload diferente
        mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", sharedKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-B\",\"displayName\":\"Empleado B\",\"positionId\":\"" + admin.positionId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // EMP-B no se insertó
        Integer countB = jdbc.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE code = 'EMP-B'", Integer.class);
        assertThat(countB).isEqualTo(0);
    }

    // =========================================================================
    // 8. Reversión determinista y replay de rechazo
    // =========================================================================

    @Test
    @DisplayName("8. Conflicto de código duplicado se guarda y reproduce como REJECTED")
    void rechazoDeterminista_codigoDuplicado_seGuardaYReproduceComoRejected() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        // 1. Crear primer empleado
        UUID key1 = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key1.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DUP-01\",\"displayName\":\"Original\",\"positionId\":\"" + admin.positionId() + "\"}"))
                .andExpect(status().isCreated());

        // 2. Segundo intento con mismo código pero clave distinta
        UUID keyConflict = UUID.randomUUID();
        String conflictPayload = "{\"code\":\"DUP-01\",\"displayName\":\"Otro Original\",\"positionId\":\"" + admin.positionId() + "\"}";

        mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", keyConflict.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_CODE_ALREADY_EXISTS"));

        // Verificar registro de operación rechazada
        Map<String, Object> opConflict = jdbc.queryForMap(
                "SELECT outcome, http_status, response_body FROM api_operations WHERE idempotency_key = ?",
                keyConflict);
        assertThat(opConflict.get("outcome")).isEqualTo("REJECTED");
        assertThat(opConflict.get("http_status")).isEqualTo(409);
        String bodyConflict = (String) opConflict.get("response_body");
        assertThat(bodyConflict).contains("EMPLOYEE_CODE_ALREADY_EXISTS");

        // 3. Replay de rechazo determinista
        mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", keyConflict.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictPayload))
                .andExpect(status().isConflict())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("EMPLOYEE_CODE_ALREADY_EXISTS"));
    }

    // =========================================================================
    // 9. Aislamiento multi-tenant estricto
    // =========================================================================

    @Test
    @DisplayName("9. Aislamiento multi-tenant: Org B no puede leer, modificar, activar, desactivar ni previsualizar empleado de Org A")
    void aislamientoMultiTenant_orgBNoAccedeAEmpleadoDeOrgA() throws Exception {
        UserSession adminA = createAndLoginUser("ADMINISTRADOR");
        UserSession adminB = createAndLoginUser("ADMINISTRADOR");

        // Crear empleado en Org A
        MvcResult resCreate = mockMvc.perform(post("/api/v1/employees")
                        .cookie(adminA.sessionCookie())
                        .header("X-CSRF-TOKEN", adminA.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ISO-01\",\"displayName\":\"Empleado Org A\",\"positionId\":\"" + adminA.positionId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String employeeIdA = objectMapper.readTree(resCreate.getResponse().getContentAsString()).get("id").asString();

        // 1. GET /employees/{id} desde Org B devuelve 404
        mockMvc.perform(get("/api/v1/employees/" + employeeIdA).cookie(adminB.sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"));

        // 2. PATCH /employees/{id} desde Org B devuelve 404
        mockMvc.perform(patch("/api/v1/employees/" + employeeIdA)
                        .cookie(adminB.sessionCookie())
                        .header("X-CSRF-TOKEN", adminB.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Hacker Name\",\"expectedLockVersion\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"));

        // 3. POST /activate desde Org B devuelve 404
        mockMvc.perform(post("/api/v1/employees/" + employeeIdA + "/activate")
                        .cookie(adminB.sessionCookie())
                        .header("X-CSRF-TOKEN", adminB.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"));

        // 4. POST /deactivate desde Org B devuelve 404
        mockMvc.perform(post("/api/v1/employees/" + employeeIdA + "/deactivate")
                        .cookie(adminB.sessionCookie())
                        .header("X-CSRF-TOKEN", adminB.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"));

        // 5. POST /deactivation-preview desde Org B devuelve 404
        mockMvc.perform(post("/api/v1/employees/" + employeeIdA + "/deactivation-preview")
                        .cookie(adminB.sessionCookie())
                        .header("X-CSRF-TOKEN", adminB.csrfToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"));

        // 6. GET /employees desde Org B no lista el empleado de Org A
        MvcResult resListB = mockMvc.perform(get("/api/v1/employees").cookie(adminB.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode nodeB = objectMapper.readTree(resListB.getResponse().getContentAsString());
        List<String> codesB = new ArrayList<>();
        for (JsonNode item : nodeB) {
            codesB.add(item.get("code").asString());
        }
        assertThat(codesB).doesNotContain("ISO-01");
    }

    // =========================================================================
    // 10. Concurrencia y versionado optimista en PATCH, activate y deactivate
    // =========================================================================

    @Test
    @DisplayName("10. Control de concurrencia optimista con expectedLockVersion en PATCH, activate y deactivate")
    void concurrenciaOptimista_patchActivateDeactivate() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        // 1. Crear colaborador (lockVersion = 0)
        MvcResult resCreate = mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"VER-01\",\"displayName\":\"Versión Inicial\",\"positionId\":\"" + admin.positionId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String employeeId = objectMapper.readTree(resCreate.getResponse().getContentAsString()).get("id").asString();

        // 2. PATCH con expectedLockVersion errónea (1) -> 409 CONCURRENT_MODIFICATION
        mockMvc.perform(patch("/api/v1/employees/" + employeeId)
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Intento Fallido\",\"expectedLockVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        // 3. PATCH con expectedLockVersion correcta (0) -> 200 OK (lockVersion pasa a 1)
        mockMvc.perform(patch("/api/v1/employees/" + employeeId)
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Versión Modificada\",\"expectedLockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Versión Modificada"))
                .andExpect(jsonPath("$.lockVersion").value(1));

        // 4. Desactivar con expectedLockVersion obsoleta (0) -> 409 CONCURRENT_MODIFICATION
        mockMvc.perform(post("/api/v1/employees/" + employeeId + "/deactivate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        // 5. Desactivar con expectedLockVersion correcta (1) -> 200 OK (lockVersion pasa a 2, active = false)
        mockMvc.perform(post("/api/v1/employees/" + employeeId + "/deactivate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.lockVersion").value(2));

        // 6. Activar con expectedLockVersion correcta (2) -> 200 OK (lockVersion pasa a 3, active = true)
        mockMvc.perform(post("/api/v1/employees/" + employeeId + "/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.lockVersion").value(3));
    }

    // =========================================================================
    // 11. Previsualización de desactivación
    // =========================================================================

    @Test
    @DisplayName("11. Previsualización de desactivación devuelve 200 con canDeactivate y blockers sin mutar datos")
    void previsualizacionDesactivacion_devuelve200SinMutarDatos() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        // Crear colaborador
        MvcResult resCreate = mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"PREV-01\",\"displayName\":\"Empleado Preview\",\"positionId\":\"" + admin.positionId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String employeeId = objectMapper.readTree(resCreate.getResponse().getContentAsString()).get("id").asString();

        // Ejecutar previsualización (sin Idempotency-Key)
        mockMvc.perform(post("/api/v1/employees/" + employeeId + "/deactivation-preview")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.employeeId").value(employeeId))
                .andExpect(jsonPath("$.displayName").value("Empleado Preview"))
                .andExpect(jsonPath("$.canDeactivate").value(true))
                .andExpect(jsonPath("$.blockers").isArray());

        // Comprobar que en BD sigue activo y lockVersion es 0
        Map<String, Object> empData = jdbc.queryForMap(
                "SELECT is_active, lock_version FROM employees WHERE id = ?",
                UUID.fromString(employeeId));
        assertThat((Boolean) empData.get("is_active")).isTrue();
        assertThat(((Number) empData.get("lock_version")).longValue()).isEqualTo(0L);
    }

    // =========================================================================
    // 12. Límite de licencias de empleados
    // =========================================================================

    @Test
    @DisplayName("12. Límite de licencias: rechaza inserción cuando se alcanza max_active_employees")
    void limiteLicencias_rechazaInsercionAlAlcanzarMaximo() throws Exception {
        // Organización con límite de 1 colaborador activo
        UserSession admin = createAndLoginUserWithLimits("ADMINISTRADOR", 1);

        // 1. Crear primer colaborador activo -> 201 Created
        MvcResult resCreate1 = mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LIC-01\",\"displayName\":\"Primer Empleado\",\"positionId\":\"" + admin.positionId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String empId1 = objectMapper.readTree(resCreate1.getResponse().getContentAsString()).get("id").asString();

        // 2. Intentar crear segundo colaborador activo -> 409 EMPLOYEE_LICENSE_LIMIT_REACHED
        mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LIC-02\",\"displayName\":\"Segundo Empleado\",\"positionId\":\"" + admin.positionId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LICENSE_LIMIT_REACHED"));

        // 3. Desactivar primer colaborador -> 200 OK
        mockMvc.perform(post("/api/v1/employees/" + empId1 + "/deactivate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isOk());

        // 4. Ahora crear el segundo colaborador tiene éxito -> 201 Created
        mockMvc.perform(post("/api/v1/employees")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LIC-02\",\"displayName\":\"Segundo Empleado\",\"positionId\":\"" + admin.positionId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LIC-02"));
    }
}
