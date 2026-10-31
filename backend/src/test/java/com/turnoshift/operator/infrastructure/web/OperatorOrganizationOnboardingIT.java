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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integración para la incorporación de organizaciones por el operador de plataforma
 * (POST /api/v1/operator/setup/organizations) sobre SQL Server 2022 real (Testcontainers).
 *
 * <p>Verifica atomicidad mono-transaccional, bloqueos de aplicación de SQL Server (sp_getapplock),
 * deduplicación de idempotencia, serialización concurrente, rechazos deterministas sin fugas
 * y frontera estricta de seguridad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("IT: Incorporación de Organizaciones por Operador — Spring MVC + SQL Server 2022")
class OperatorOrganizationOnboardingIT {

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

    private record OperatorSession(Cookie cookie, String csrfToken, String operatorId) {}
    private record BusinessUserSession(Cookie cookie, String csrfToken) {}

    private String getCsrfToken(Cookie sessionCookie) throws Exception {
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

    private OperatorSession createAndLoginOperator() throws Exception {
        String operatorId = UUID.randomUUID().toString();
        String email = "op." + UUID.randomUUID().toString().substring(0, 6) + "@turnoshift.example";
        String plainPassword = "ClaveOperadorSegura2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO platform_operators
                    (id, email, normalized_email, display_name, password_hash, is_active, auth_version)
                VALUES (?, ?, ?, 'Operador Test', ?, 1, 1)
                """, operatorId, email, email.toLowerCase(), passwordHash);

        Cookie initialCookie = getCookieFromCsrf();
        String csrfToken = getCsrfToken(initialCookie);

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

        Cookie sessionCookie = loginResult.getResponse().getCookie("TSESSIONID");
        String authenticatedCsrf = getCsrfToken(sessionCookie);
        return new OperatorSession(sessionCookie, authenticatedCsrf, operatorId);
    }

    private BusinessUserSession createAndLoginBusinessUser() throws Exception {
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
        String csrfToken = getCsrfToken(initialCookie);

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

        Cookie sessionCookie = loginResult.getResponse().getCookie("TSESSIONID");
        String authenticatedCsrf = getCsrfToken(sessionCookie);
        return new BusinessUserSession(sessionCookie, authenticatedCsrf);
    }

    @Test
    @DisplayName("Happy Path: Creación completa de organización, sede y administrador con idempotencia terminal")
    void happyPath_creacionCompleta_conIdempotenciaTerminal() throws Exception {
        OperatorSession session = createAndLoginOperator();
        String idempotencyKey = UUID.randomUUID().toString();
        String orgCode = "ACME-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        String adminEmail = "admin." + UUID.randomUUID().toString().substring(0, 6) + "@acme.example";

        String requestBody = """
                {
                  "organization": {
                    "code": "%s",
                    "displayName": "Acme Corporation",
                    "timeZone": "America/Bogota"
                  },
                  "site": {
                    "displayName": "Sede Principal Bogota"
                  },
                  "firstAdmin": {
                    "email": "%s",
                    "displayName": "Admin Acme"
                  }
                }
                """.formatted(orgCode, adminEmail);

        // 1. Petición inicial exitosa -> 201 CREATED
        MvcResult result = mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.organization.id").isString())
                .andExpect(jsonPath("$.organization.code").value(orgCode))
                .andExpect(jsonPath("$.organization.displayName").value("Acme Corporation"))
                .andExpect(jsonPath("$.organization.timeZone").value("America/Bogota"))
                .andExpect(jsonPath("$.site.id").isString())
                .andExpect(jsonPath("$.site.displayName").value("Sede Principal Bogota"))
                .andExpect(jsonPath("$.firstAdmin.id").isString())
                .andExpect(jsonPath("$.firstAdmin.email").value(adminEmail))
                .andExpect(jsonPath("$.firstAdmin.displayName").value("Admin Acme"))
                .andExpect(jsonPath("$.firstAdmin.role").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.pendingSetup").isArray())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String orgId = responseNode.get("organization").get("id").asString();
        String siteId = responseNode.get("site").get("id").asString();
        String adminId = responseNode.get("firstAdmin").get("id").asString();

        // Verificar persistencia en base de datos
        // Organizaciones
        var orgRow = jdbc.queryForMap(
                "SELECT code, service_status, max_active_employees FROM organizations WHERE id = ?", orgId);
        assertThat(orgRow.get("code")).isEqualTo(orgCode);
        assertThat(orgRow.get("service_status")).isEqualTo("ACTIVA");
        assertThat(orgRow.get("max_active_employees")).isEqualTo(50);

        // Sedes
        Integer siteCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sites WHERE id = ? AND organization_id = ? AND code = 'PRINCIPAL' AND is_active = 1",
                Integer.class, siteId, orgId);
        assertThat(siteCount).isEqualTo(1);

        // Usuarios (password_hash debe ser NULL)
        var userRow = jdbc.queryForMap(
                "SELECT email, password_hash, role_code, is_active FROM app_users WHERE id = ? AND organization_id = ?",
                adminId, orgId);
        assertThat(userRow.get("email")).isEqualTo(adminEmail);
        assertThat(userRow.get("password_hash")).isNull();
        assertThat(userRow.get("role_code")).isEqualTo("ADMINISTRADOR");
        assertThat((Boolean) userRow.get("is_active")).isTrue();

        // api_operations
        var opRow = jdbc.queryForMap(
                "SELECT status, outcome, http_status, actor_operator_id, actor_user_id, organization_id, response_location " +
                "FROM api_operations WHERE idempotency_key = ?",
                idempotencyKey);
        assertThat(opRow.get("status")).isEqualTo("COMPLETED");
        assertThat(opRow.get("outcome")).isEqualTo("SUCCEEDED");
        assertThat(opRow.get("http_status")).isEqualTo(201);
        assertThat(opRow.get("actor_operator_id").toString()).isEqualToIgnoringCase(session.operatorId());
        assertThat(opRow.get("actor_user_id")).isNull();
        assertThat(opRow.get("organization_id")).isNull();
        assertThat(opRow.get("response_location")).isNull();

        // 2. Replay idéntico con la misma clave -> 201 CREATED con Idempotency-Replayed: true
        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.organization.id").value(orgId))
                .andExpect(jsonPath("$.site.id").value(siteId))
                .andExpect(jsonPath("$.firstAdmin.id").value(adminId));

        // Verificar que no se hayan creado filas duplicadas
        Integer totalOrgs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE code = ?", Integer.class, orgCode);
        assertThat(totalOrgs).isEqualTo(1);
    }

    @Test
    @DisplayName("Fallo técnico: error en base de datos aplica rollback total sin dejar registros en ninguna tabla")
    void falloTecnico_aplicaRollbackTotal() throws Exception {
        OperatorSession session = createAndLoginOperator();
        String idempotencyKey = UUID.randomUUID().toString();
        String failCode = "FAIL-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        String adminEmail = "admin.fail@" + failCode.toLowerCase() + ".example";

        // Agregar constraint temporal que fuerce fallo técnico en INSERT organizations
        jdbc.execute("ALTER TABLE organizations ADD CONSTRAINT chk_test_fail CHECK (code <> '" + failCode + "')");
        try {
            String requestBody = """
                    {
                      "organization": {
                        "code": "%s",
                        "displayName": "Fail Corp",
                        "timeZone": "America/Bogota"
                      },
                      "site": {
                        "displayName": "Sede Principal"
                      },
                      "firstAdmin": {
                        "email": "%s",
                        "displayName": "Admin Fail"
                      }
                    }
                    """.formatted(failCode, adminEmail);

            mockMvc.perform(post("/api/v1/operator/setup/organizations")
                            .cookie(session.cookie())
                            .header("X-CSRF-TOKEN", session.csrfToken())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.status").value(503));

            // Verificar que NINGUNA tabla tiene filas de esta operación fallida (rollback total)
            Integer orgs = jdbc.queryForObject("SELECT COUNT(*) FROM organizations WHERE code = ?", Integer.class, failCode);
            Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE email = ?", Integer.class, adminEmail);
            Integer ops = jdbc.queryForObject("SELECT COUNT(*) FROM api_operations WHERE idempotency_key = ?", Integer.class, idempotencyKey);

            assertThat(orgs).isZero();
            assertThat(users).isZero();
            assertThat(ops).isZero();
        } finally {
            jdbc.execute("ALTER TABLE organizations DROP CONSTRAINT chk_test_fail");
        }
    }

    @Test
    @DisplayName("Conflicto de código: detecta duplicado bajo lock, registra REJECTED en api_operations y no crea entidades")
    void conflictoCodigo_registraRechazoTerminalSinCrearEntidades() throws Exception {
        OperatorSession session = createAndLoginOperator();

        // Preexistencia de una organización con código duplicado
        String existingOrgCode = "EXISTING-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (NEWID(), ?, 'Org Existente', 'ACTIVA', 'America/Bogota', 50, 1)
                """, existingOrgCode);

        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = """
                {
                  "organization": {
                    "code": "%s",
                    "displayName": "Otra Organizacion",
                    "timeZone": "America/Bogota"
                  },
                  "site": {
                    "displayName": "Sede Principal"
                  },
                  "firstAdmin": {
                    "email": "nuevo.admin@example.com",
                    "displayName": "Nuevo Admin"
                  }
                }
                """.formatted(existingOrgCode);

        // 1. Petición inicial -> 409 Conflict
        MvcResult result = mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.code").value("ORGANIZATION_CODE_CONFLICT"))
                .andExpect(jsonPath("$.title").value("Código de organización en uso"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.requestId").isString())
                .andReturn();

        String firstRequestId = objectMapper.readTree(result.getResponse().getContentAsString()).get("requestId").asString();

        // Verificar que no se insertaron entidades en dominio
        Integer orgCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE code = ?", Integer.class, existingOrgCode);
        assertThat(orgCount).isEqualTo(1);

        Integer adminCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE email = 'nuevo.admin@example.com'", Integer.class);
        assertThat(adminCount).isEqualTo(0);

        // Verificar registro REJECTED en api_operations
        var opRow = jdbc.queryForMap(
                "SELECT status, outcome, http_status, response_body FROM api_operations WHERE idempotency_key = ?",
                idempotencyKey);
        assertThat(opRow.get("status")).isEqualTo("COMPLETED");
        assertThat(opRow.get("outcome")).isEqualTo("REJECTED");
        assertThat(opRow.get("http_status")).isEqualTo(409);
        String storedBody = (String) opRow.get("response_body");
        assertThat(storedBody).contains("ORGANIZATION_CODE_CONFLICT");
        assertThat(storedBody).doesNotContain("requestId");
        assertThat(storedBody).doesNotContain("instance");

        // 2. Replay del rechazo con la misma clave -> 409 Conflict con Idempotency-Replayed: true y nuevo requestId
        MvcResult replayResult = mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("ORGANIZATION_CODE_CONFLICT"))
                .andExpect(jsonPath("$.status").value(409))
                .andReturn();

        String secondRequestId = objectMapper.readTree(replayResult.getResponse().getContentAsString()).get("requestId").asString();
        assertThat(secondRequestId).isNotEqualTo(firstRequestId);
    }

    @Test
    @DisplayName("Reuso de clave con distinta huella: devuelve 409 IDEMPOTENCY_KEY_REUSED")
    void claveReutilizadaConDistintaHuella_devuelve409() throws Exception {
        OperatorSession session = createAndLoginOperator();
        String idempotencyKey = UUID.randomUUID().toString();
        String orgCode = "DIFF-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();

        String originalBody = """
                {
                  "organization": {
                    "code": "%s",
                    "displayName": "Original Corp",
                    "timeZone": "America/Bogota"
                  },
                  "site": {
                    "displayName": "Sede Principal"
                  },
                  "firstAdmin": {
                    "email": "admin@diff.example",
                    "displayName": "Admin Uno"
                  }
                }
                """.formatted(orgCode);

        // Ejecución exitosa inicial
        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(originalBody))
                .andExpect(status().isCreated());

        // Segunda ejecución con diferente displayName
        String modifiedBody = """
                {
                  "organization": {
                    "code": "%s",
                    "displayName": "Modified Corp",
                    "timeZone": "America/Bogota"
                  },
                  "site": {
                    "displayName": "Sede Principal"
                  },
                  "firstAdmin": {
                    "email": "admin@diff.example",
                    "displayName": "Admin Uno"
                  }
                }
                """.formatted(orgCode);

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modifiedBody))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.title").value("Clave de idempotencia reutilizada"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Clave expirada (> 24h): devuelve 409 IDEMPOTENCY_KEY_EXPIRED")
    void claveExpirada_devuelve409() throws Exception {
        OperatorSession session = createAndLoginOperator();
        String expiredKey = UUID.randomUUID().toString();

        // Insertar operación expirada
        jdbc.update("""
                INSERT INTO api_operations (
                    id, organization_id, actor_user_id, actor_operator_id, idempotency_key,
                    request_method, request_path, request_fingerprint, request_schema_version,
                    status, outcome, http_status, response_content_type, response_body,
                    created_at, completed_at, expires_at
                ) VALUES (
                    NEWID(), NULL, NULL, ?, ?,
                    'POST', '/api/v1/operator/setup/organizations', 0x010203, '1.0',
                    'COMPLETED', 'SUCCEEDED', 201, 'application/json', '{}',
                    DATEADD(hour, -25, SYSDATETIMEOFFSET()), DATEADD(hour, -25, SYSDATETIMEOFFSET()), DATEADD(hour, -1, SYSDATETIMEOFFSET())
                )
                """, session.operatorId(), expiredKey);

        String requestBody = """
                {
                  "organization": {
                    "code": "EXP-ORG",
                    "displayName": "Exp Corp",
                    "timeZone": "America/Bogota"
                  },
                  "site": {
                    "displayName": "Sede Principal"
                  },
                  "firstAdmin": {
                    "email": "admin@exp.example",
                    "displayName": "Admin Exp"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", expiredKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_EXPIRED"))
                .andExpect(jsonPath("$.title").value("Clave de idempotencia expirada"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Concurrencia con la misma clave: dos hilos concurrentes serializan en sp_getapplock y uno emite replay")
    void concurrenciaMismaClave_serializaYEmiteReplay() throws Exception {
        OperatorSession session = createAndLoginOperator();
        String idempotencyKey = UUID.randomUUID().toString();
        String orgCode = "CONC-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        String adminEmail = "concur." + UUID.randomUUID().toString().substring(0, 6) + "@example.com";

        String requestBody = """
                {
                  "organization": {
                    "code": "%s",
                    "displayName": "Concurrent Corp",
                    "timeZone": "America/Bogota"
                  },
                  "site": {
                    "displayName": "Sede Principal"
                  },
                  "firstAdmin": {
                    "email": "%s",
                    "displayName": "Admin Concur"
                  }
                }
                """.formatted(orgCode, adminEmail);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/operator/setup/organizations")
                                .cookie(session.cookie())
                                .header("X-CSRF-TOKEN", session.csrfToken())
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andReturn();
            }));
        }

        startLatch.countDown();

        int successCount = 0;
        int replayCount = 0;

        for (Future<MvcResult> future : futures) {
            MvcResult res = future.get();
            assertThat(res.getResponse().getStatus()).isEqualTo(201);
            String replayed = res.getResponse().getHeader("Idempotency-Replayed");
            if ("true".equals(replayed)) {
                replayCount++;
            } else {
                successCount++;
            }
        }
        executor.shutdown();

        assertThat(successCount).as("Exactamente 1 petición debió ser la creación original").isEqualTo(1);
        assertThat(replayCount).as("Exactamente 1 petición debió ser el replay idéntico").isEqualTo(1);

        // Verificar que en DB solo existe una entidad
        Integer totalOrgs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE code = ?", Integer.class, orgCode);
        assertThat(totalOrgs).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrencia con el mismo código de organización y claves distintas: el segundo hilo se bloquea y registra conflicto 409")
    void concurrenciaMismoCodigo_serializaYRegistraRechazo() throws Exception {
        OperatorSession session = createAndLoginOperator();
        String sharedCode = "RACE-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();

        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        String requestBody1 = """
                {
                  "organization": {
                    "code": "%s",
                    "displayName": "Race One Corp",
                    "timeZone": "America/Bogota"
                  },
                  "site": {
                    "displayName": "Sede Uno"
                  },
                  "firstAdmin": {
                    "email": "race1@example.com",
                    "displayName": "Admin Uno"
                  }
                }
                """.formatted(sharedCode);

        String requestBody2 = """
                {
                  "organization": {
                    "code": "%s",
                    "displayName": "Race Two Corp",
                    "timeZone": "America/Bogota"
                  },
                  "site": {
                    "displayName": "Sede Dos"
                  },
                  "firstAdmin": {
                    "email": "race2@example.com",
                    "displayName": "Admin Dos"
                  }
                }
                """.formatted(sharedCode);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<MvcResult> future1 = executor.submit(() -> {
            startLatch.await();
            return mockMvc.perform(post("/api/v1/operator/setup/organizations")
                            .cookie(session.cookie())
                            .header("X-CSRF-TOKEN", session.csrfToken())
                            .header("Idempotency-Key", key1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody1))
                    .andReturn();
        });

        Future<MvcResult> future2 = executor.submit(() -> {
            startLatch.await();
            return mockMvc.perform(post("/api/v1/operator/setup/organizations")
                            .cookie(session.cookie())
                            .header("X-CSRF-TOKEN", session.csrfToken())
                            .header("Idempotency-Key", key2)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody2))
                    .andReturn();
        });

        startLatch.countDown();

        MvcResult res1 = future1.get();
        MvcResult res2 = future2.get();
        executor.shutdown();

        List<Integer> statuses = List.of(res1.getResponse().getStatus(), res2.getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);

        // Solo 1 organización creada
        Integer totalOrgs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE code = ?", Integer.class, sharedCode);
        assertThat(totalOrgs).isEqualTo(1);

        // Dos registros en api_operations (uno SUCCEEDED, uno REJECTED)
        Integer totalOperations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_operations WHERE idempotency_key IN (?, ?)", Integer.class, key1, key2);
        assertThat(totalOperations).isEqualTo(2);
    }

    @Test
    @DisplayName("Seguridad: usuario empresarial autenticado recibe 403 ACCESS_DENIED al llamar endpoint de operador")
    void seguridad_usuarioEmpresarialRecibe403() throws Exception {
        BusinessUserSession userSession = createAndLoginBusinessUser();

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(userSession.cookie())
                        .header("X-CSRF-TOKEN", userSession.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": {"code":"NOPE-01","displayName":"X","timeZone":"UTC"},
                                  "site": {"displayName":"S"},
                                  "firstAdmin": {"email":"x@x.com","displayName":"A"}
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Seguridad: operador sin CSRF recibe 403 CSRF_INVALID")
    void seguridad_operadorSinCsrfRecibe403() throws Exception {
        OperatorSession session = createAndLoginOperator();

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(session.cookie())
                        // Sin X-CSRF-TOKEN
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": {"code":"NOPE-01","displayName":"X","timeZone":"UTC"},
                                  "site": {"displayName":"S"},
                                  "firstAdmin": {"email":"x@x.com","displayName":"A"}
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Seguridad: petición sin sesión (con CSRF inicial) recibe 401 SESSION_REQUIRED")
    void seguridad_sinSesionRecibe401() throws Exception {
        Cookie anonCookie = getCookieFromCsrf();
        String anonCsrf = getCsrfToken(anonCookie);

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(anonCookie)
                        .header("X-CSRF-TOKEN", anonCsrf)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Validación: Idempotency-Key con formato no UUID devuelve 400 VALIDATION_FAILED")
    void validacion_idempotencyKeyInvalida_devuelve400() throws Exception {
        OperatorSession session = createAndLoginOperator();

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", "not-a-valid-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": {"code":"CORP-01","displayName":"X","timeZone":"UTC"},
                                  "site": {"displayName":"S"},
                                  "firstAdmin": {"email":"x@x.com","displayName":"A"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("idempotencyKey"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Validación: Campos obligatorios y formatos inválidos devuelven 400 VALIDATION_FAILED con lista de errores")
    void validacion_camposInvalidos_devuelve400() throws Exception {
        OperatorSession session = createAndLoginOperator();

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": {
                                    "code": "a",
                                    "displayName": "",
                                    "timeZone": "Invalid/Zone"
                                  },
                                  "site": {
                                    "displayName": ""
                                  },
                                  "firstAdmin": {
                                    "email": "not-an-email",
                                    "displayName": ""
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }
}
