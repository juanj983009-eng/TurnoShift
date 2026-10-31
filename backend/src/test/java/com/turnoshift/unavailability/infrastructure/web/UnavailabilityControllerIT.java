package com.turnoshift.unavailability.infrastructure.web;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("IT: UnavailabilityController — Seguridad, RBAC, Multi-tenant y Consulta con SQL Server")
class UnavailabilityControllerIT {

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

    private record OrgFixture(
            UUID orgId,
            String orgCode,
            UUID siteId,
            UUID positionId,
            UUID employeeId,
            UUID unavailabilityId,
            UserSession adminSession,
            UserSession supervisorSession,
            UserSession colaboradorSession
    ) {}

    private UserSession createAndLoginUserForOrg(UUID orgId, String orgCode, String role) throws Exception {
        UUID userId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String email = "user." + suffix.toLowerCase(Locale.ROOT) + "@empresa.example";
        String plainPassword = "PasswordSegura2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name,
                    password_hash, role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Usuario Test', ?, ?, 1, 1)
                """, userId, orgId, email, email.toLowerCase(Locale.ROOT), passwordHash, role);

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie initialCookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String initialCsrf = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

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

    private OrgFixture createOrgFixture() throws Exception {
        UUID orgId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String orgCode = "ORG-" + suffix;

        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Org Test', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, orgCode);

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

        UUID employeeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO employees (id, organization_id, code, display_name, position_id, site_id, is_active)
                VALUES (?, ?, ?, 'Carlos Gómez', ?, ?, 1)
                """, employeeId, orgId, "EMP-" + suffix, positionId, siteId);

        UserSession admin = createAndLoginUserForOrg(orgId, orgCode, "ADMINISTRADOR");
        UserSession supervisor = createAndLoginUserForOrg(orgId, orgCode, "SUPERVISOR");
        UserSession colaborador = createAndLoginUserForOrg(orgId, orgCode, "COLABORADOR");

        UUID unavailId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO employee_unavailabilities (
                    id, organization_id, employee_id, starts_at, ends_at,
                    reason_code, restricted_note, created_by_user_id,
                    created_at, updated_at, lock_version
                ) VALUES (?, ?, ?, '2026-07-01 08:00:00 +00:00', '2026-07-01 17:00:00 +00:00',
                    'VACACIONES', 'Nota confidencial I2.4-c1', ?, SYSUTCDATETIME(), SYSUTCDATETIME(), 0)
                """, unavailId, orgId, employeeId, admin.userId());

        return new OrgFixture(orgId, orgCode, siteId, positionId, employeeId, unavailId, admin, supervisor, colaborador);
    }

    // =========================================================================
    // 1. Roles de Gestión (ADMINISTRADOR y SUPERVISOR) -> 200 OK
    // =========================================================================

    @Test
    @DisplayName("ADMINISTRADOR y SUPERVISOR pueden consultar los 3 endpoints y reciben 200 OK con datos protegidos")
    void administradorYSupervisor_puedenConsultar_200Ok() throws Exception {
        OrgFixture fixture = createOrgFixture();

        // 1. Administrador consulta lista de empleado
        mockMvc.perform(get("/api/v1/employees/" + fixture.employeeId() + "/unavailabilities")
                        .cookie(fixture.adminSession().sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(fixture.unavailabilityId().toString())))
                .andExpect(jsonPath("$.items[0].reasonCode", is("VACACIONES")))
                .andExpect(jsonPath("$.items[0].restrictedNote", is("Nota confidencial I2.4-c1")));

        // 2. Administrador consulta ventana organizacional
        mockMvc.perform(get("/api/v1/unavailabilities")
                        .cookie(fixture.adminSession().sessionCookie())
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(fixture.unavailabilityId().toString())));

        // 3. Administrador consulta detalle por ID
        mockMvc.perform(get("/api/v1/unavailabilities/" + fixture.unavailabilityId())
                        .cookie(fixture.adminSession().sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id", is(fixture.unavailabilityId().toString())))
                .andExpect(jsonPath("$.reasonCode", is("VACACIONES")))
                .andExpect(jsonPath("$.restrictedNote", is("Nota confidencial I2.4-c1")));

        // 4. Supervisor consulta los 3 endpoints
        mockMvc.perform(get("/api/v1/employees/" + fixture.employeeId() + "/unavailabilities")
                        .cookie(fixture.supervisorSession().sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].restrictedNote", is("Nota confidencial I2.4-c1")));

        mockMvc.perform(get("/api/v1/unavailabilities")
                        .cookie(fixture.supervisorSession().sessionCookie())
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));

        mockMvc.perform(get("/api/v1/unavailabilities/" + fixture.unavailabilityId())
                        .cookie(fixture.supervisorSession().sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(fixture.unavailabilityId().toString())));
    }

    // =========================================================================
    // 2. COLABORADOR y PLATFORM_OPERATOR -> 403 ACCESS_DENIED
    // =========================================================================

    @Test
    @DisplayName("COLABORADOR recibe 403 ACCESS_DENIED en los 3 endpoints")
    void colaborador_recibe403AccessDenied() throws Exception {
        OrgFixture fixture = createOrgFixture();

        // 1. Por empleado
        mockMvc.perform(get("/api/v1/employees/" + fixture.employeeId() + "/unavailabilities")
                        .cookie(fixture.colaboradorSession().sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));

        // 2. Ventana organizacional
        mockMvc.perform(get("/api/v1/unavailabilities")
                        .cookie(fixture.colaboradorSession().sessionCookie())
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-02T00:00:00Z"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));

        // 3. Detalle por ID
        mockMvc.perform(get("/api/v1/unavailabilities/" + fixture.unavailabilityId())
                        .cookie(fixture.colaboradorSession().sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("PLATFORM_OPERATOR recibe 403 ACCESS_DENIED en los 3 endpoints")
    void platformOperator_recibe403AccessDenied() throws Exception {
        OrgFixture fixture = createOrgFixture();
        OperatorSession operator = createAndLoginOperator();

        // 1. Por empleado
        mockMvc.perform(get("/api/v1/employees/" + fixture.employeeId() + "/unavailabilities")
                        .cookie(operator.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));

        // 2. Ventana organizacional
        mockMvc.perform(get("/api/v1/unavailabilities")
                        .cookie(operator.sessionCookie())
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-02T00:00:00Z"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));

        // 3. Detalle por ID
        mockMvc.perform(get("/api/v1/unavailabilities/" + fixture.unavailabilityId())
                        .cookie(operator.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    // =========================================================================
    // 3. Sin Sesión -> 401 SESSION_REQUIRED
    // =========================================================================

    @Test
    @DisplayName("Usuario sin sesión recibe 401 SESSION_REQUIRED en los 3 endpoints")
    void sinSesion_recibe401SessionRequired() throws Exception {
        UUID someId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/employees/" + someId + "/unavailabilities"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("SESSION_REQUIRED")));

        mockMvc.perform(get("/api/v1/unavailabilities")
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-02T00:00:00Z"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("SESSION_REQUIRED")));

        mockMvc.perform(get("/api/v1/unavailabilities/" + someId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("SESSION_REQUIRED")));
    }

    // =========================================================================
    // 4. Aislamiento Cross-Tenant Estricto -> 404
    // =========================================================================

    @Test
    @DisplayName("Aislamiento Cross-Tenant: consultar recurso de otra organización devuelve 404")
    void aislamientoCrossTenant_devuelve404() throws Exception {
        OrgFixture fixtureA = createOrgFixture();
        OrgFixture fixtureB = createOrgFixture();

        // 1. Admin de Org B intenta consultar indisponibilidad de Org A
        mockMvc.perform(get("/api/v1/unavailabilities/" + fixtureA.unavailabilityId())
                        .cookie(fixtureB.adminSession().sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("UNAVAILABILITY_NOT_FOUND")));

        // 2. Admin de Org B intenta listar indisponibilidades del empleado de Org A
        mockMvc.perform(get("/api/v1/employees/" + fixtureA.employeeId() + "/unavailabilities")
                        .cookie(fixtureB.adminSession().sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("EMPLOYEE_NOT_FOUND")));
    }

    // =========================================================================
    // 5. Mutaciones: RBAC (ADMINISTRADOR y SUPERVISOR vs COLABORADOR)
    // =========================================================================

    @Test
    @DisplayName("ROLE_SUPERVISOR: Puede crear, modificar y cancelar indisponibilidad (201 Created y 200 OK)")
    void supervisorPuedeCrearModificarYCancelarIndisponibilidad() throws Exception {
        OrgFixture fixture = createOrgFixture();
        UserSession supervisor = fixture.supervisorSession();

        Instant startsAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(8, ChronoUnit.HOURS);

        // 1. Crear como SUPERVISOR -> 201 Created
        UUID createKey = UUID.randomUUID();
        String createPayload = """
                {
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "reasonCode": "PERMISO_PERSONAL",
                  "restrictedNote": "Permiso especial de supervisor"
                }
                """.formatted(startsAt, endsAt);

        MvcResult createResult = mockMvc.perform(post("/api/v1/employees/" + fixture.employeeId() + "/unavailabilities")
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken())
                        .header("Idempotency-Key", createKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.reasonCode", is("PERMISO_PERSONAL")))
                .andExpect(jsonPath("$.lockVersion", is(0)))
                .andReturn();

        String createdIdStr = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asString();
        UUID createdId = UUID.fromString(createdIdStr);

        // 2. Modificar como SUPERVISOR -> 200 OK
        UUID updateKey = UUID.randomUUID();
        Instant newStartsAt = startsAt.plus(1, ChronoUnit.DAYS);
        Instant newEndsAt = newStartsAt.plus(6, ChronoUnit.HOURS);

        String updatePayload = """
                {
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "reasonCode": "MEDICO",
                  "restrictedNote": "Cita médica reprogramada",
                  "expectedLockVersion": 0
                }
                """.formatted(newStartsAt, newEndsAt);

        mockMvc.perform(put("/api/v1/unavailabilities/" + createdId)
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken())
                        .header("Idempotency-Key", updateKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.reasonCode", is("MEDICO")))
                .andExpect(jsonPath("$.lockVersion", is(1)));

        // 3. Cancelar como SUPERVISOR -> 200 OK
        UUID cancelKey = UUID.randomUUID();
        String cancelPayload = """
                {
                  "expectedLockVersion": 1
                }
                """;

        mockMvc.perform(post("/api/v1/unavailabilities/" + createdId + "/cancel")
                        .cookie(supervisor.sessionCookie())
                        .header("X-CSRF-TOKEN", supervisor.csrfToken())
                        .header("Idempotency-Key", cancelKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPayload))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.cancelled", is(true)))
                .andExpect(jsonPath("$.lockVersion", is(2)));
    }

    @Test
    @DisplayName("ROLE_COLABORADOR: Rechazado con 403 ACCESS_DENIED en mutaciones (create, update, cancel)")
    void colaboradorRechazadoCon403EnMutaciones() throws Exception {
        OrgFixture fixture = createOrgFixture();
        UserSession colaborador = fixture.colaboradorSession();

        Instant startsAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(4, ChronoUnit.HOURS);

        // 1. Create -> 403
        String createPayload = """
                {
                  "startsAt": "%s",
                  "endsAt": "%s"
                }
                """.formatted(startsAt, endsAt);

        mockMvc.perform(post("/api/v1/employees/" + fixture.employeeId() + "/unavailabilities")
                        .cookie(colaborador.sessionCookie())
                        .header("X-CSRF-TOKEN", colaborador.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));

        // 2. Update -> 403
        String updatePayload = """
                {
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "expectedLockVersion": 0
                }
                """.formatted(startsAt, endsAt);

        mockMvc.perform(put("/api/v1/unavailabilities/" + fixture.unavailabilityId())
                        .cookie(colaborador.sessionCookie())
                        .header("X-CSRF-TOKEN", colaborador.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));

        // 3. Cancel -> 403
        String cancelPayload = """
                {
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(post("/api/v1/unavailabilities/" + fixture.unavailabilityId() + "/cancel")
                        .cookie(colaborador.sessionCookie())
                        .header("X-CSRF-TOKEN", colaborador.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPayload))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("Sin CSRF: Mutaciones rechazadas con 403 CSRF_INVALID")
    void sinCsrfRechazadoCon403() throws Exception {
        OrgFixture fixture = createOrgFixture();
        UserSession admin = fixture.adminSession();

        // 1. Create sin CSRF -> 403
        mockMvc.perform(post("/api/v1/employees/" + fixture.employeeId() + "/unavailabilities")
                        .cookie(admin.sessionCookie())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("CSRF_INVALID")));

        // 2. Update sin CSRF -> 403
        mockMvc.perform(put("/api/v1/unavailabilities/" + fixture.unavailabilityId())
                        .cookie(admin.sessionCookie())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("CSRF_INVALID")));

        // 3. Cancel sin CSRF -> 403
        mockMvc.perform(post("/api/v1/unavailabilities/" + fixture.unavailabilityId() + "/cancel")
                        .cookie(admin.sessionCookie())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("CSRF_INVALID")));
    }

    @Test
    @DisplayName("Idempotencia real: Replay y reuso de clave con persistencia real en BD")
    void idempotenciaRealReplayYKeyReusedEnBD() throws Exception {
        OrgFixture fixture = createOrgFixture();
        UserSession admin = fixture.adminSession();
        UUID idemKey = UUID.randomUUID();

        Instant startsAt = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(8, ChronoUnit.HOURS);

        String payload = """
                {
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "reasonCode": "VACACIONES",
                  "restrictedNote": "Vacaciones programadas"
                }
                """.formatted(startsAt, endsAt);

        // 1. Primer envío: 201 Created sin cabecera Idempotency-Replayed
        MvcResult firstResult = mockMvc.perform(post("/api/v1/employees/" + fixture.employeeId() + "/unavailabilities")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andReturn();

        String createdId = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("id").asString();

        // 2. Replay idéntico: 201 Created con Idempotency-Replayed: true
        mockMvc.perform(post("/api/v1/employees/" + fixture.employeeId() + "/unavailabilities")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id", is(createdId)));

        // Verificar en BD que la tabla employee_unavailabilities sólo tiene 1 registro con ese ID
        Integer unavailCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM employee_unavailabilities WHERE id = ?",
                Integer.class, UUID.fromString(createdId)
        );
        assertThat(unavailCount).isEqualTo(1);

        // Verificar que api_operations tiene la operación registrada
        Integer opCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_operations WHERE organization_id = ? AND idempotency_key = ?",
                Integer.class, fixture.orgId(), idemKey
        );
        assertThat(opCount).isEqualTo(1);

        // 3. Reuso de la misma clave con payload distinto -> 409 IDEMPOTENCY_KEY_REUSED
        String differentPayload = """
                {
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "reasonCode": "MEDICO"
                }
                """.formatted(startsAt, endsAt);

        mockMvc.perform(post("/api/v1/employees/" + fixture.employeeId() + "/unavailabilities")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentPayload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_KEY_REUSED")));
    }
}
