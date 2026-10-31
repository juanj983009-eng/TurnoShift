package com.turnoshift.policy.infrastructure.web;

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

import com.turnoshift.policy.domain.PolicyRepository;
import com.turnoshift.policy.domain.PolicyThresholds;
import com.turnoshift.policy.domain.ValidationFingerprint;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("IT: PolicyController — API HTTP de Lectura, Previsualización y Activación de Políticas")
class PolicyControllerTest {

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

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private record UserSession(
            UUID orgId,
            String orgCode,
            UUID userId,
            String email,
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

        return new UserSession(orgId, orgCode, userId, email, cookieToUse, authCsrf);
    }

    private UUID insertarPolitica(UUID orgId, int versionNumber, int minRest, int weeklyWarning, UUID authorId, String reason) {
        UUID policyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO planning_policy_versions (
                    id, organization_id, version_number, minimum_rest_minutes, weekly_warning_minutes,
                    created_by_user_id, change_reason
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, policyId, orgId, versionNumber, minRest, weeklyWarning, authorId, reason);
        return policyId;
    }

    private void fijarPoliticaActual(UUID orgId, UUID policyId) {
        jdbc.update("""
                UPDATE organizations
                SET current_policy_version_id = ?, lock_version = lock_version + 1, updated_at = SYSUTCDATETIME()
                WHERE id = ?
                """, policyId, orgId);
    }

    // =========================================================================
    // 1. GET /api/v1/policies/current
    // =========================================================================

    @Test
    @DisplayName("GET /current: sin sesión devuelve 401 SESSION_REQUIRED")
    void getCurrentPolicy_sinSesion_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/policies/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("GET /current: rol COLABORADOR devuelve 403 ACCESS_DENIED")
    void getCurrentPolicy_rolColaborador_devuelve403() throws Exception {
        UserSession colab = createAndLoginUser("COLABORADOR");

        mockMvc.perform(get("/api/v1/policies/current").cookie(colab.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /current: SUPERVISOR sin política configurada devuelve 200 con policy null")
    void getCurrentPolicy_supervisorSinPolitica_devuelve200ConPolicyNull() throws Exception {
        UserSession sup = createAndLoginUser("SUPERVISOR");

        mockMvc.perform(get("/api/v1/policies/current").cookie(sup.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.policy").value(nullValue()));
    }

    @Test
    @DisplayName("GET /current: ADMINISTRADOR con política configurada devuelve 200 con datos completos")
    void getCurrentPolicy_adminConPolitica_devuelve200ConDetalle() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID polId = insertarPolitica(admin.orgId(), 1, 660, 2400, admin.userId(), "Política inicial");
        fijarPoliticaActual(admin.orgId(), polId);

        mockMvc.perform(get("/api/v1/policies/current").cookie(admin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.policy.id").value(polId.toString()))
                .andExpect(jsonPath("$.policy.versionNumber").value(1))
                .andExpect(jsonPath("$.policy.minimumRestMinutes").value(660))
                .andExpect(jsonPath("$.policy.weeklyWarningMinutes").value(2400))
                .andExpect(jsonPath("$.policy.changeReason").value("Política inicial"))
                .andExpect(jsonPath("$.policy.createdByUserId").value(admin.userId().toString()))
                .andExpect(jsonPath("$.policy.effectiveAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET /current: aislamiento multi-tenant estricto entre organizaciones")
    void getCurrentPolicy_aislamientoMultiTenant() throws Exception {
        UserSession adminA = createAndLoginUser("ADMINISTRADOR");
        UserSession adminB = createAndLoginUser("ADMINISTRADOR");

        UUID polA = insertarPolitica(adminA.orgId(), 1, 660, 2400, adminA.userId(), "Org A policy");
        fijarPoliticaActual(adminA.orgId(), polA);

        // Org A ve polA
        mockMvc.perform(get("/api/v1/policies/current").cookie(adminA.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.id").value(polA.toString()));

        // Org B no tiene política y ve null (no ve polA)
        mockMvc.perform(get("/api/v1/policies/current").cookie(adminB.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy").value(nullValue()));
    }

    // =========================================================================
    // 2. GET /api/v1/policies/history
    // =========================================================================

    @Test
    @DisplayName("GET /history: sin sesión devuelve 401 SESSION_REQUIRED")
    void getPolicyHistory_sinSesion_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/policies/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("GET /history: rol COLABORADOR devuelve 403 ACCESS_DENIED")
    void getPolicyHistory_rolColaborador_devuelve403() throws Exception {
        UserSession colab = createAndLoginUser("COLABORADOR");

        mockMvc.perform(get("/api/v1/policies/history").cookie(colab.sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /history: SUPERVISOR sin versiones devuelve 200 con items vacío")
    void getPolicyHistory_supervisorSinVersiones_devuelve200Vacio() throws Exception {
        UserSession sup = createAndLoginUser("SUPERVISOR");

        mockMvc.perform(get("/api/v1/policies/history").cookie(sup.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items", empty()));
    }

    @Test
    @DisplayName("GET /history: ADMINISTRADOR con múltiples versiones devuelve orden descendente por versionNumber")
    void getPolicyHistory_adminConVersiones_devuelveOrdenDescendente() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID pol1 = insertarPolitica(admin.orgId(), 1, 660, 2400, admin.userId(), "v1");
        UUID pol2 = insertarPolitica(admin.orgId(), 2, 720, 2800, admin.userId(), "v2");

        mockMvc.perform(get("/api/v1/policies/history").cookie(admin.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(pol2.toString()))
                .andExpect(jsonPath("$.items[0].versionNumber").value(2))
                .andExpect(jsonPath("$.items[0].minimumRestMinutes").value(720))
                .andExpect(jsonPath("$.items[0].weeklyWarningMinutes").value(2800))
                .andExpect(jsonPath("$.items[1].id").value(pol1.toString()))
                .andExpect(jsonPath("$.items[1].versionNumber").value(1));
    }

    @Test
    @DisplayName("GET /history: aislamiento multi-tenant entre organizaciones")
    void getPolicyHistory_aislamientoMultiTenant() throws Exception {
        UserSession adminA = createAndLoginUser("ADMINISTRADOR");
        UserSession adminB = createAndLoginUser("ADMINISTRADOR");

        insertarPolitica(adminA.orgId(), 1, 660, 2400, adminA.userId(), "Org A v1");

        mockMvc.perform(get("/api/v1/policies/history").cookie(adminA.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));

        mockMvc.perform(get("/api/v1/policies/history").cookie(adminB.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()));
    }

    // =========================================================================
    // 3. POST /api/v1/policies/preview
    // =========================================================================

    @Test
    @DisplayName("POST /preview: sin sesión devuelve 401 SESSION_REQUIRED")
    void preview_sinSesion_devuelve401() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie anonCookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String anonCsrf = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        mockMvc.perform(post("/api/v1/policies/preview")
                        .cookie(anonCookie)
                        .header("X-CSRF-TOKEN", anonCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentPolicyVersionId\":null,\"minimumRestMinutes\":660,\"weeklyWarningMinutes\":2400}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("POST /preview: rol SUPERVISOR devuelve 403 ACCESS_DENIED")
    void preview_supervisor_devuelve403() throws Exception {
        UserSession sup = createAndLoginUser("SUPERVISOR");

        mockMvc.perform(post("/api/v1/policies/preview")
                        .cookie(sup.sessionCookie())
                        .header("X-CSRF-TOKEN", sup.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentPolicyVersionId\":null,\"minimumRestMinutes\":660,\"weeklyWarningMinutes\":2400}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /preview: rol COLABORADOR devuelve 403 ACCESS_DENIED")
    void preview_colaborador_devuelve403() throws Exception {
        UserSession colab = createAndLoginUser("COLABORADOR");

        mockMvc.perform(post("/api/v1/policies/preview")
                        .cookie(colab.sessionCookie())
                        .header("X-CSRF-TOKEN", colab.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentPolicyVersionId\":null,\"minimumRestMinutes\":660,\"weeklyWarningMinutes\":2400}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /preview: ADMINISTRADOR sin CSRF devuelve 403 CSRF_INVALID")
    void preview_adminSinCsrf_devuelve403CsrfInvalid() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        mockMvc.perform(post("/api/v1/policies/preview")
                        .cookie(admin.sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentPolicyVersionId\":null,\"minimumRestMinutes\":660,\"weeklyWarningMinutes\":2400}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("POST /preview: JSON inválido devuelve 400 VALIDATION_FAILED")
    void preview_jsonInvalido_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        mockMvc.perform(post("/api/v1/policies/preview")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /preview: propiedad expectedCurrentPolicyVersionId ausente devuelve 400 VALIDATION_FAILED")
    void preview_expectedVersionAusente_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        // Omitir la clave expectedCurrentPolicyVersionId en el payload
        String payload = """
                {
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400
                }
                """;

        mockMvc.perform(post("/api/v1/policies/preview")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("expectedCurrentPolicyVersionId"));
    }

    @Test
    @DisplayName("POST /preview: expectedCurrentPolicyVersionId no es UUID válido devuelve 400 VALIDATION_FAILED")
    void preview_expectedVersionFormatoInvalido_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        String payload = """
                {
                  "expectedCurrentPolicyVersionId": "no-es-un-uuid",
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400
                }
                """;

        mockMvc.perform(post("/api/v1/policies/preview")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("expectedCurrentPolicyVersionId"));
    }

    @Test
    @DisplayName("POST /preview: umbrales fuera de rango devuelven 400 VALIDATION_FAILED")
    void preview_umbralesFueraDeRango_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        // minimumRestMinutes = 0 fuera de rango [1..1440]
        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 0,
                  "weeklyWarningMinutes": 2400
                }
                """;

        mockMvc.perform(post("/api/v1/policies/preview")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /preview: expectedCurrentPolicyVersionId null explícito sin política previa devuelve 200 con fingerprint")
    void preview_nullExplicitoSinPoliticaPrevia_devuelve200Preview() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400
                }
                """;

        mockMvc.perform(post("/api/v1/policies/preview")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.currentPolicy").value(nullValue()))
                .andExpect(jsonPath("$.proposedValues.minimumRestMinutes").value(660))
                .andExpect(jsonPath("$.proposedValues.weeklyWarningMinutes").value(2400))
                .andExpect(jsonPath("$.delta").value(nullValue()))
                .andExpect(jsonPath("$.canProceed").value(true))
                .andExpect(jsonPath("$.blockers", empty()))
                .andExpect(jsonPath("$.warnings", empty()))
                .andExpect(jsonPath("$.requirements.reasonRequired").value(true))
                .andExpect(jsonPath("$.validationFingerprint").isString());
    }

    @Test
    @DisplayName("POST /preview: expectedCurrentPolicyVersionId null cuando ya existe política devuelve 409 POLICY_CONCURRENT_MODIFICATION")
    void preview_nullExplicitoCuandoExistePolitica_devuelve409Conflicto() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID polId = insertarPolitica(admin.orgId(), 1, 660, 2400, admin.userId(), "v1");
        fijarPoliticaActual(admin.orgId(), polId);

        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 720,
                  "weeklyWarningMinutes": 2400
                }
                """;

        mockMvc.perform(post("/api/v1/policies/preview")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("POLICY_CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("POST /preview: con política previa esperada devuelve 200 con delta y fingerprint calculados")
    void preview_conPoliticaPreviaYDelta_devuelve200() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID polId = insertarPolitica(admin.orgId(), 1, 660, 2400, admin.userId(), "v1");
        fijarPoliticaActual(admin.orgId(), polId);

        String payload = """
                {
                  "expectedCurrentPolicyVersionId": "%s",
                  "minimumRestMinutes": 720,
                  "weeklyWarningMinutes": 2520
                }
                """.formatted(polId);

        mockMvc.perform(post("/api/v1/policies/preview")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.currentPolicy.id").value(polId.toString()))
                .andExpect(jsonPath("$.currentPolicy.versionNumber").value(1))
                .andExpect(jsonPath("$.proposedValues.minimumRestMinutes").value(720))
                .andExpect(jsonPath("$.proposedValues.weeklyWarningMinutes").value(2520))
                .andExpect(jsonPath("$.delta.minimumRestMinutesDelta").value(60))
                .andExpect(jsonPath("$.delta.weeklyWarningMinutesDelta").value(120))
                .andExpect(jsonPath("$.canProceed").value(true))
                .andExpect(jsonPath("$.requirements.reasonRequired").value(true))
                .andExpect(jsonPath("$.validationFingerprint").isString());
    }

    // =========================================================================
    // 4. POST /api/v1/policies/activate
    // =========================================================================

    @Test
    @DisplayName("POST /activate: sin sesión devuelve 401 SESSION_REQUIRED")
    void activate_sinSesion_devuelve401() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie anonCookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String anonCsrf = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted("a".repeat(64));

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(anonCookie)
                        .header("X-CSRF-TOKEN", anonCsrf)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("POST /activate: rol SUPERVISOR devuelve 403 ACCESS_DENIED")
    void activate_rolSupervisor_devuelve403() throws Exception {
        UserSession sup = createAndLoginUser("SUPERVISOR");
        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted("a".repeat(64));

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(sup.sessionCookie())
                        .header("X-CSRF-TOKEN", sup.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /activate: rol COLABORADOR devuelve 403 ACCESS_DENIED")
    void activate_rolColaborador_devuelve403() throws Exception {
        UserSession colab = createAndLoginUser("COLABORADOR");
        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted("a".repeat(64));

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(colab.sessionCookie())
                        .header("X-CSRF-TOKEN", colab.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /activate: ADMINISTRADOR sin CSRF devuelve 403 CSRF_INVALID")
    void activate_adminSinCsrf_devuelve403CsrfInvalid() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted("a".repeat(64));

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("POST /activate: clave de idempotencia ausente devuelve 400 VALIDATION_FAILED")
    void activate_claveIdempotenciaAusente_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted("a".repeat(64));

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /activate: clave de idempotencia con formato inválido devuelve 400 VALIDATION_FAILED")
    void activate_claveIdempotenciaInvalida_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted("a".repeat(64));

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", "no-es-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /activate: JSON inválido devuelve 400 VALIDATION_FAILED")
    void activate_jsonInvalido_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /activate: expectedCurrentPolicyVersionId ausente devuelve 400 VALIDATION_FAILED")
    void activate_expectedVersionAusente_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String badPayload = """
                {
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted("a".repeat(64));

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("expectedCurrentPolicyVersionId"));
    }

    @Test
    @DisplayName("POST /activate: expectedCurrentPolicyVersionId no es UUID válido devuelve 400 VALIDATION_FAILED")
    void activate_expectedVersionFormatoInvalido_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String badPayload = """
                {
                  "expectedCurrentPolicyVersionId": "no-es-uuid",
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted("a".repeat(64));

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("expectedCurrentPolicyVersionId"));
    }

    @Test
    @DisplayName("POST /activate: huella con formato inválido devuelve 400 INVALID_VALIDATION_FINGERPRINT")
    void activate_huellaFormatoInvalido_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String badPayload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica inicial",
                  "validationFingerprint": "huella-invalida"
                }
                """;

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_VALIDATION_FINGERPRINT"));
    }

    @Test
    @DisplayName("POST /activate: huella no coincide con recálculo canónico devuelve 400 INVALID_VALIDATION_FINGERPRINT")
    void activate_huellaNoCoincide_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String badPayload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted("0".repeat(64));

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_VALIDATION_FINGERPRINT"));
    }

    @Test
    @DisplayName("POST /activate: motivo vacío o solo espacios Unicode devuelve 400 REASON_REQUIRED")
    void activate_motivoVacioOEspaciosUnicode_devuelve400() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String validFingerprint = ValidationFingerprint.calculate(
                admin.orgId(), null, new PolicyThresholds(660, 2400)
        ).value();

        String badPayload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "   \\u00A0  ",
                  "validationFingerprint": "%s"
                }
                """.formatted(validFingerprint);

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    @DisplayName("POST /activate: primera solicitud válida crea versión append-only y devuelve 201 Created")
    void activate_primeraSolicitudValida_devuelve201Persistido() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String validFingerprint = ValidationFingerprint.calculate(
                admin.orgId(), null, new PolicyThresholds(660, 2400)
        ).value();

        String key = UUID.randomUUID().toString();
        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica piloto inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted(validFingerprint);

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.policy.versionNumber").value(1))
                .andExpect(jsonPath("$.policy.minimumRestMinutes").value(660))
                .andExpect(jsonPath("$.policy.weeklyWarningMinutes").value(2400))
                .andExpect(jsonPath("$.policy.changeReason").value("Politica piloto inicial"))
                .andExpect(jsonPath("$.policy.createdByUserId").value(admin.userId().toString()))
                .andExpect(jsonPath("$.policy.effectiveAt").isNotEmpty());

        // Verificación en base de datos
        int countVersions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM planning_policy_versions WHERE organization_id = ?",
                Integer.class, admin.orgId()
        );
        assertThat(countVersions).isEqualTo(1);

        UUID currentPolicyId = jdbc.queryForObject(
                "SELECT current_policy_version_id FROM organizations WHERE id = ?",
                UUID.class, admin.orgId()
        );
        assertThat(currentPolicyId).isNotNull();
    }

    @Test
    @DisplayName("POST /activate: replay con misma clave y mismo payload devuelve respuesta idéntica y header Idempotency-Replayed")
    void activate_replayMismaClaveMismoPayload_devuelve201ConHeaderReplayed() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String validFingerprint = ValidationFingerprint.calculate(
                admin.orgId(), null, new PolicyThresholds(660, 2400)
        ).value();

        String key = UUID.randomUUID().toString();
        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica piloto inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted(validFingerprint);

        // Intento 1 (201)
        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        int lockVersion1 = jdbc.queryForObject(
                "SELECT lock_version FROM organizations WHERE id = ?",
                Integer.class, admin.orgId()
        );

        // Intento 2 (Replay)
        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.policy.versionNumber").value(1));

        // Exactamente una versión y un solo incremento de puntero
        int countVersions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM planning_policy_versions WHERE organization_id = ?",
                Integer.class, admin.orgId()
        );
        assertThat(countVersions).isEqualTo(1);

        int lockVersion2 = jdbc.queryForObject(
                "SELECT lock_version FROM organizations WHERE id = ?",
                Integer.class, admin.orgId()
        );
        assertThat(lockVersion2).isEqualTo(lockVersion1);
    }

    @Test
    @DisplayName("POST /activate: misma clave con payload distinto devuelve 409 IDEMPOTENCY_KEY_REUSED")
    void activate_mismaClaveDistintoPayload_devuelve409KeyReused() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String validFingerprint = ValidationFingerprint.calculate(
                admin.orgId(), null, new PolicyThresholds(660, 2400)
        ).value();

        String key = UUID.randomUUID().toString();
        String payload1 = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica inicial",
                  "validationFingerprint": "%s"
                }
                """.formatted(validFingerprint);

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isCreated());

        // Mismo key con payload distinto (distinto motivo)
        String payload2 = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Politica con distinto motivo",
                  "validationFingerprint": "%s"
                }
                """.formatted(validFingerprint);

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload2))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("POST /activate: expectedCurrentPolicyVersionId desactualizado devuelve 409 POLICY_CONCURRENT_MODIFICATION")
    void activate_conflictoExpectedCurrentPolicyVersionId_devuelve409() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        UUID existingPolicyId = insertarPolitica(admin.orgId(), 1, 660, 2400, admin.userId(), "v1");
        fijarPoliticaActual(admin.orgId(), existingPolicyId);

        // Envía expectedCurrentPolicyVersionId: null cuando ya existe v1
        String fingerprint = ValidationFingerprint.calculate(
                admin.orgId(), null, new PolicyThresholds(720, 2520)
        ).value();

        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 720,
                  "weeklyWarningMinutes": 2520,
                  "reason": "Intento sobre v1",
                  "validationFingerprint": "%s"
                }
                """.formatted(fingerprint);

        mockMvc.perform(post("/api/v1/policies/activate")
                        .cookie(admin.sessionCookie())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("POLICY_CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("POST /activate: bloqueo ocupado devuelve 503 POLICY_OPERATION_BUSY con Retry-After: 1")
    void activate_lockOcupado_devuelve503PolicyOperationBusy() throws Exception {
        UserSession admin = createAndLoginUser("ADMINISTRADOR");
        String validFingerprint = ValidationFingerprint.calculate(
                admin.orgId(), null, new PolicyThresholds(660, 2400)
        ).value();

        String payload = """
                {
                  "expectedCurrentPolicyVersionId": null,
                  "minimumRestMinutes": 660,
                  "weeklyWarningMinutes": 2400,
                  "reason": "Lock ocupado",
                  "validationFingerprint": "%s"
                }
                """.formatted(validFingerprint);

        CountDownLatch lockAdquirido = new CountDownLatch(1);
        CountDownLatch peticionCompletada = new CountDownLatch(1);
        ExecutorService threadPool = Executors.newSingleThreadExecutor();

        // Hilo secundario retiene el lock exclusivo sobre la organización
        threadPool.submit(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                policyRepository.acquirePolicyLock(admin.orgId());
                lockAdquirido.countDown();
                try {
                    peticionCompletada.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });

        assertThat(lockAdquirido.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            mockMvc.perform(post("/api/v1/policies/activate")
                            .cookie(admin.sessionCookie())
                            .header("X-CSRF-TOKEN", admin.csrfToken())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("Retry-After", "1"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("POLICY_OPERATION_BUSY"));
        } finally {
            peticionCompletada.countDown();
            threadPool.shutdown();
        }
    }
}
