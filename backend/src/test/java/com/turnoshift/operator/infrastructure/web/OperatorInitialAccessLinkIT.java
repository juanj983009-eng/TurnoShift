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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración para la emisión del enlace de acceso inicial del primer administrador
 * (POST /api/v1/operator/setup/organizations/{orgId}/initial-access-link)
 * sobre SQL Server 2022 real (Testcontainers).
 *
 * <p>Verifica atomicidad mono-transaccional, bloqueo jerárquico anti-deadlock (sp_getapplock),
 * deduplicación de idempotencia, emisión de reemplazo deliberada, concurrencia optimista,
 * redacción estricta de secretos en reposo y frontera de seguridad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("IT: Emisión de Enlace de Acceso Inicial — Spring MVC + SQL Server 2022")
class OperatorInitialAccessLinkIT {

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
    private record OrgWithAdmin(UUID orgId, UUID adminUserId) {}

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

    private OrgWithAdmin createTestOrganizationAndAdmin(String status, boolean userActive, boolean hasPassword, String roleCode, long lockVersion) {
        UUID orgId = UUID.randomUUID();
        String orgCode = "ORG-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Org Test', ?, 'America/Lima', 50, 1)
                """, orgId, orgCode, status);

        UUID siteId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sites (id, organization_id, code, name, is_active)
                VALUES (?, ?, 'PRINCIPAL', 'Sede Principal', 1)
                """, siteId, orgId);

        UUID adminId = UUID.randomUUID();
        String email = "admin." + UUID.randomUUID().toString().substring(0, 6) + "@test.example";
        String passwordHash = hasPassword ? passwordService.codificar("Password123!") : null;

        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version, lock_version)
                VALUES (?, ?, ?, ?, 'Admin Test', ?, ?, ?, 0, ?)
                """, adminId, orgId, email, email.toLowerCase(), passwordHash, roleCode, userActive ? 1 : 0, lockVersion);

        return new OrgWithAdmin(orgId, adminId);
    }

    private byte[] computeSha256(String text) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("1. Happy path: emisión exitosa genera enlace con hash SHA-256, avanza lock_version y sanitiza api_operations")
    void happyPath_emisionExitosa_generaEnlaceConHashPersistido() throws Exception {
        OperatorSession op = createAndLoginOperator();
        OrgWithAdmin target = createTestOrganizationAndAdmin("ACTIVA", true, false, "ADMINISTRADOR", 0L);

        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = """
                {
                  "adminUserId": "%s",
                  "expectedVersion": 0
                }
                """.formatted(target.adminUserId());

        MvcResult result = mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", target.orgId())
                        .cookie(op.cookie())
                        .header("X-CSRF-TOKEN", op.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.setupLink").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String setupLink = responseNode.get("setupLink").asString();
        assertThat(setupLink).contains("#token=");
        String rawToken = setupLink.substring(setupLink.indexOf("#token=") + 7);
        assertThat(rawToken).hasSize(43); // 256 bits URL-safe Base64

        // 1. Verificar hash SHA-256 en password_reset_tokens
        byte[] expectedHash = computeSha256(rawToken);
        List<byte[]> tokenHashes = jdbc.query(
                "SELECT token_hash FROM password_reset_tokens WHERE organization_id = ? AND user_id = ? AND revoked_at IS NULL",
                (rs, rowNum) -> rs.getBytes("token_hash"),
                target.orgId(), target.adminUserId()
        );
        assertThat(tokenHashes).hasSize(1);
        assertThat(tokenHashes.get(0)).isEqualTo(expectedHash);

        // 2. Verificar avance de lock_version de 0 a 1 en app_users
        Long lockVersion = jdbc.queryForObject(
                "SELECT lock_version FROM app_users WHERE organization_id = ? AND id = ?",
                Long.class, target.orgId(), target.adminUserId()
        );
        assertThat(lockVersion).isEqualTo(1L);

        // 3. Verificar api_operations: SUCCEEDED, 200, secretRedacted=true, y cero texto plano del secreto
        List<String> bodies = jdbc.query(
                "SELECT response_body FROM api_operations WHERE actor_operator_id = ? AND idempotency_key = ?",
                (rs, rowNum) -> rs.getString("response_body"),
                UUID.fromString(op.operatorId()), UUID.fromString(idempotencyKey)
        );
        assertThat(bodies).hasSize(1);
        String savedBody = bodies.get(0);
        assertThat(savedBody).contains("\"secretRedacted\":true");
        assertThat(savedBody).doesNotContain(rawToken);
        assertThat(savedBody).doesNotContain(setupLink);
    }

    @Test
    @DisplayName("2. Replay con la misma clave: devuelve 409 INITIAL_LINK_ALREADY_EMITTED con Idempotency-Replayed: true sin revocar ni duplicar")
    void replayMismaClave_devuelve409SinGenerarNuevoEnlaceNiRevocarElActual() throws Exception {
        OperatorSession op = createAndLoginOperator();
        OrgWithAdmin target = createTestOrganizationAndAdmin("ACTIVA", true, false, "ADMINISTRADOR", 0L);

        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = """
                {
                  "adminUserId": "%s",
                  "expectedVersion": 0
                }
                """.formatted(target.adminUserId());

        // Primera petición: 200 OK
        MvcResult firstResult = mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", target.orgId())
                        .cookie(op.cookie())
                        .header("X-CSRF-TOKEN", op.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
        String reqId1 = firstResult.getResponse().getHeader("X-Request-ID");

        // Segunda petición (replay con misma clave y cuerpo): 409 INITIAL_LINK_ALREADY_EMITTED
        MvcResult replayResult = mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", target.orgId())
                        .cookie(op.cookie())
                        .header("X-CSRF-TOKEN", op.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("INITIAL_LINK_ALREADY_EMITTED"))
                .andExpect(jsonPath("$.type").value("/problems/initial-link-already-emitted"))
                .andReturn();

        String reqId2 = replayResult.getResponse().getHeader("X-Request-ID");
        assertThat(reqId2).isNotEqualTo(reqId1);

        // Verificar que sigue habiendo exactamente 1 token y NO fue revocado
        Integer activeTokens = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE organization_id = ? AND user_id = ? AND revoked_at IS NULL",
                Integer.class, target.orgId(), target.adminUserId()
        );
        assertThat(activeTokens).isEqualTo(1);
    }

    @Test
    @DisplayName("3. Emisión deliberada de reemplazo: nueva clave con versión 1 revoca enlace anterior y avanza versión a 2")
    void emisionDeliberadaReemplazo_revocaEnlaceAnterior() throws Exception {
        OperatorSession op = createAndLoginOperator();
        OrgWithAdmin target = createTestOrganizationAndAdmin("ACTIVA", true, false, "ADMINISTRADOR", 0L);

        // Emisión 1 (versión 0)
        String key1 = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", target.orgId())
                        .cookie(op.cookie())
                        .header("X-CSRF-TOKEN", op.csrfToken())
                        .header("Idempotency-Key", key1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUserId": "%s",
                                  "expectedVersion": 0
                                }
                                """.formatted(target.adminUserId())))
                .andExpect(status().isOk());

        // Emisión deliberada de reemplazo (clave nueva, versión esperada 1)
        String key2 = UUID.randomUUID().toString();
        MvcResult replacementResult = mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", target.orgId())
                        .cookie(op.cookie())
                        .header("X-CSRF-TOKEN", op.csrfToken())
                        .header("Idempotency-Key", key2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUserId": "%s",
                                  "expectedVersion": 1
                                }
                                """.formatted(target.adminUserId())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(replacementResult.getResponse().getContentAsString());
        String newLink = responseNode.get("setupLink").asString();
        String newRawToken = newLink.substring(newLink.indexOf("#token=") + 7);
        byte[] newHash = computeSha256(newRawToken);

        // Verificar tokens: 1 revocado (el primero), 1 activo (el nuevo)
        Integer totalTokens = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE organization_id = ? AND user_id = ?",
                Integer.class, target.orgId(), target.adminUserId()
        );
        Integer revokedTokens = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE organization_id = ? AND user_id = ? AND revoked_at IS NOT NULL",
                Integer.class, target.orgId(), target.adminUserId()
        );
        Integer activeTokens = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE organization_id = ? AND user_id = ? AND revoked_at IS NULL",
                Integer.class, target.orgId(), target.adminUserId()
        );

        assertThat(totalTokens).isEqualTo(2);
        assertThat(revokedTokens).isEqualTo(1);
        assertThat(activeTokens).isEqualTo(1);

        byte[] activeHash = jdbc.queryForObject(
                "SELECT token_hash FROM password_reset_tokens WHERE organization_id = ? AND user_id = ? AND revoked_at IS NULL",
                byte[].class, target.orgId(), target.adminUserId()
        );
        assertThat(activeHash).isEqualTo(newHash);

        // Verificar que lock_version avanzó a 2
        Long version = jdbc.queryForObject(
                "SELECT lock_version FROM app_users WHERE organization_id = ? AND id = ?",
                Long.class, target.orgId(), target.adminUserId()
        );
        assertThat(version).isEqualTo(2L);
    }

    @Test
    @DisplayName("4. Versión desactualizada: devuelve 409 CONCURRENT_MODIFICATION")
    void versionDesactualizada_devuelve409Conflicto() throws Exception {
        OperatorSession op = createAndLoginOperator();
        // lock_version actual en BD es 1
        OrgWithAdmin target = createTestOrganizationAndAdmin("ACTIVA", true, false, "ADMINISTRADOR", 1L);

        String idempotencyKey = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", target.orgId())
                        .cookie(op.cookie())
                        .header("X-CSRF-TOKEN", op.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUserId": "%s",
                                  "expectedVersion": 0
                                }
                                """.formatted(target.adminUserId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.type").value("/problems/concurrent-modification"))
                .andExpect(jsonPath("$.detail").value("La versión esperada (0) no coincide con la versión actual (1) del administrador."));

        // Verificar que se registró el REJECTED en api_operations
        List<String> outcomes = jdbc.query(
                "SELECT outcome FROM api_operations WHERE actor_operator_id = ? AND idempotency_key = ?",
                (rs, rowNum) -> rs.getString("outcome"),
                UUID.fromString(op.operatorId()), UUID.fromString(idempotencyKey)
        );
        assertThat(outcomes).containsExactly("REJECTED");
    }

    @Test
    @DisplayName("5. Administrador con contraseña ya configurada: devuelve 409 INVALID_STATE_TRANSITION")
    void administradorConPasswordYaConfigurada_devuelve409() throws Exception {
        OperatorSession op = createAndLoginOperator();
        // hasPassword = true
        OrgWithAdmin target = createTestOrganizationAndAdmin("ACTIVA", true, true, "ADMINISTRADOR", 0L);

        String idempotencyKey = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", target.orgId())
                        .cookie(op.cookie())
                        .header("X-CSRF-TOKEN", op.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUserId": "%s",
                                  "expectedVersion": 0
                                }
                                """.formatted(target.adminUserId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("El administrador ya tiene contraseña configurada."));
    }

    @Test
    @DisplayName("6. Organización inexistente: devuelve 404 RESOURCE_NOT_FOUND")
    void organizacionInexistente_devuelve404() throws Exception {
        OperatorSession op = createAndLoginOperator();
        UUID nonExistentOrgId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", nonExistentOrgId)
                        .cookie(op.cookie())
                        .header("X-CSRF-TOKEN", op.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUserId": "%s",
                                  "expectedVersion": 0
                                }
                                """.formatted(adminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("7. Administrador inexistente o en otra organización: devuelve 404 RESOURCE_NOT_FOUND")
    void administradorInexistenteOEnOtraOrg_devuelve404() throws Exception {
        OperatorSession op = createAndLoginOperator();
        OrgWithAdmin org1 = createTestOrganizationAndAdmin("ACTIVA", true, false, "ADMINISTRADOR", 0L);
        OrgWithAdmin org2 = createTestOrganizationAndAdmin("ACTIVA", true, false, "ADMINISTRADOR", 0L);

        // Solicitar admin de org2 en la ruta de org1
        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", org1.orgId())
                        .cookie(op.cookie())
                        .header("X-CSRF-TOKEN", op.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUserId": "%s",
                                  "expectedVersion": 0
                                }
                                """.formatted(org2.adminUserId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("8. Concurrencia con la misma clave: serializa con sp_getapplock y devuelve exactamente 1 éxito (200) y 1 replay (409)")
    void concurrenciaMismaClave_serializaYDevuelve409AlSegundo() throws Exception {
        OperatorSession op = createAndLoginOperator();
        OrgWithAdmin target = createTestOrganizationAndAdmin("ACTIVA", true, false, "ADMINISTRADOR", 0L);

        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = """
                {
                  "adminUserId": "%s",
                  "expectedVersion": 0
                }
                """.formatted(target.adminUserId());

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                MvcResult res = mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", target.orgId())
                                .cookie(op.cookie())
                                .header("X-CSRF-TOKEN", op.csrfToken())
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andReturn();
                return res.getResponse().getStatus();
            }));
        }

        latch.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        executor.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        // Exactamente 1 token activo en base de datos
        Integer tokenCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE organization_id = ? AND user_id = ? AND revoked_at IS NULL",
                Integer.class, target.orgId(), target.adminUserId()
        );
        assertThat(tokenCount).isEqualTo(1);
    }

    @Test
    @DisplayName("9. Concurrencia con la misma versión pero distintas claves: serializa y uno falla por conflicto de versión")
    void concurrenciaMismaVersionDistintasClaves_serializaYUnoFallaPorVersion() throws Exception {
        OperatorSession op = createAndLoginOperator();
        OrgWithAdmin target = createTestOrganizationAndAdmin("ACTIVA", true, false, "ADMINISTRADOR", 0L);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                MvcResult res = mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", target.orgId())
                                .cookie(op.cookie())
                                .header("X-CSRF-TOKEN", op.csrfToken())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "adminUserId": "%s",
                                          "expectedVersion": 0
                                        }
                                        """.formatted(target.adminUserId())))
                        .andReturn();
                return res.getResponse().getStatus();
            }));
        }

        latch.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        executor.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        // lock_version avanzó exactamente a 1
        Long version = jdbc.queryForObject(
                "SELECT lock_version FROM app_users WHERE organization_id = ? AND id = ?",
                Long.class, target.orgId(), target.adminUserId()
        );
        assertThat(version).isEqualTo(1L);
    }

    @Test
    @DisplayName("10. Seguridad: usuario empresarial recibe 403 ACCESS_DENIED")
    void seguridad_usuarioEmpresarialRecibe403() throws Exception {
        BusinessUserSession user = createAndLoginBusinessUser();
        UUID orgId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", orgId)
                        .cookie(user.cookie())
                        .header("X-CSRF-TOKEN", user.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUserId": "%s",
                                  "expectedVersion": 0
                                }
                                """.formatted(adminId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("11. Seguridad: operador sin cabecera CSRF recibe 403 CSRF_INVALID")
    void seguridad_operadorSinCsrfRecibe403() throws Exception {
        OperatorSession op = createAndLoginOperator();
        UUID orgId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", orgId)
                        .cookie(op.cookie())
                        // Sin cabecera X-CSRF-TOKEN
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUserId": "%s",
                                  "expectedVersion": 0
                                }
                                """.formatted(adminId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("12. Seguridad: petición anónima sin sesión recibe 401 SESSION_REQUIRED")
    void seguridad_sinSesionRecibe401() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Cookie anonCookie = getCookieFromCsrf();
        String csrfToken = getCsrfToken(anonCookie);

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", orgId)
                        .cookie(anonCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUserId": "%s",
                                  "expectedVersion": 0
                                }
                                """.formatted(adminId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("13. Auditoría de seguridad: verificación de ausencia total de secreto en base de datos")
    void auditoriaSeguridad_verificacionAusenciaDeSecretoEnBD() throws Exception {
        OperatorSession op = createAndLoginOperator();
        OrgWithAdmin target = createTestOrganizationAndAdmin("ACTIVA", true, false, "ADMINISTRADOR", 0L);

        String idempotencyKey = UUID.randomUUID().toString();
        MvcResult res = mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", target.orgId())
                        .cookie(op.cookie())
                        .header("X-CSRF-TOKEN", op.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUserId": "%s",
                                  "expectedVersion": 0
                                }
                                """.formatted(target.adminUserId())))
                .andExpect(status().isOk())
                .andReturn();

        String setupLink = objectMapper.readTree(res.getResponse().getContentAsString()).get("setupLink").asString();
        String rawToken = setupLink.substring(setupLink.indexOf("#token=") + 7);

        // Comprobación 1: el secreto no está en api_operations
        List<String> opBodies = jdbc.query(
                "SELECT response_body FROM api_operations WHERE actor_operator_id = ?",
                (rs, rowNum) -> rs.getString("response_body"),
                UUID.fromString(op.operatorId())
        );
        for (String body : opBodies) {
            assertThat(body).doesNotContain(rawToken);
            assertThat(body).doesNotContain("token=");
        }

        // Comprobación 2: el secreto no está en ninguna columna de texto de password_reset_tokens
        // (solo existe token_hash que es BINARY(32))
        List<String> purposes = jdbc.query(
                "SELECT purpose FROM password_reset_tokens WHERE organization_id = ?",
                (rs, rowNum) -> rs.getString("purpose"),
                target.orgId()
        );
        assertThat(purposes).containsOnly("INITIAL_SETUP");
    }

    @Test
    @DisplayName("14. Fallo técnico en token: TokenDataAccessException responde 503 SERVICE_UNAVAILABLE y aplica rollback total")
    void falloTecnicoEnToken_responde503YAplicaRollbackTotal() throws Exception {
        OperatorSession op = createAndLoginOperator();
        OrgWithAdmin target = createTestOrganizationAndAdmin("ACTIVA", true, false, "ADMINISTRADOR", 0L);

        // Pre-insertar un token previo para verificar que su revocación también sufre rollback
        UUID priorTokenId = UUID.randomUUID();
        byte[] priorHash = new byte[32];
        priorHash[0] = 42;
        jdbc.update("""
                INSERT INTO password_reset_tokens
                    (id, organization_id, user_id, token_hash, purpose, expires_at, created_at)
                VALUES (?, ?, ?, ?, 'INITIAL_SETUP', DATEADD(minute, 30, SYSUTCDATETIME()), SYSUTCDATETIME())
                """, priorTokenId, target.orgId(), target.adminUserId(), priorHash);

        String idempotencyKey = UUID.randomUUID().toString();

        // Agregar constraint temporal que permita el token previo pero fuerce fallo en el nuevo INSERT
        jdbc.execute("ALTER TABLE password_reset_tokens WITH NOCHECK ADD CONSTRAINT chk_test_token_fail CHECK (id = '" + priorTokenId + "')");
        try {
            mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", target.orgId())
                            .cookie(op.cookie())
                            .header("X-CSRF-TOKEN", op.csrfToken())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "adminUserId": "%s",
                                      "expectedVersion": 0
                                    }
                                    """.formatted(target.adminUserId())))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().exists("X-Request-ID"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.type").value("/problems/service-unavailable"))
                    .andExpect(jsonPath("$.title").value("Servicio no disponible"))
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty())
                    .andExpect(jsonPath("$.errors").isEmpty())
                    .andExpect(jsonPath("$.detail").value("El servicio no está disponible temporalmente. Intente más tarde."));

            // 1. Verificación de rollback en tokens: solo existe el token previo (0 tokens nuevos insertados)
            Integer totalTokens = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM password_reset_tokens WHERE organization_id = ?",
                    Integer.class, target.orgId());
            assertThat(totalTokens).isEqualTo(1);

            // 2. Verificación de rollback en revocación: el token previo NO quedó revocado (revoked_at sigue siendo NULL)
            Integer unrevoked = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM password_reset_tokens WHERE id = ? AND revoked_at IS NULL",
                    Integer.class, priorTokenId);
            assertThat(unrevoked).isEqualTo(1);

            // 3. Verificación de rollback en versión: lock_version sigue siendo 0
            Long lockVersion = jdbc.queryForObject(
                    "SELECT lock_version FROM app_users WHERE id = ?",
                    Long.class, target.adminUserId());
            assertThat(lockVersion).isEqualTo(0L);

            // 4. Verificación de rollback en api_operations: 0 registros para esta clave
            Integer totalOps = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM api_operations WHERE idempotency_key = ?",
                    Integer.class, UUID.fromString(idempotencyKey));
            assertThat(totalOps).isZero();
        } finally {
            jdbc.execute("ALTER TABLE password_reset_tokens DROP CONSTRAINT chk_test_token_fail");
        }
    }
}
