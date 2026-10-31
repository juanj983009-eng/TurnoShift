package com.turnoshift.operator.infrastructure.web;

import com.turnoshift.operator.application.IncorporateOrganization;
import com.turnoshift.operator.domain.IncorporateOrganizationCommand;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.OrganizationOnboardingResult;
import com.turnoshift.operator.domain.PlatformOperatorIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Unit: OperatorOrganizationSetupController — endpoint POST /api/v1/operator/setup/organizations")
class OperatorOrganizationSetupControllerTest {

    private IncorporateOrganization incorporateOrganization;
    private MockMvc mockMvc;

    private static final UUID OPERATOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String IDEMPOTENCY_KEY = "22222222-2222-2222-2222-222222222222";

    private static final String VALID_BODY = """
            {
              "organization": {
                "code": "CAFE-DEMO",
                "displayName": "Café Demo S.A.",
                "timeZone": "America/Lima"
              },
              "site": {
                "displayName": "Sede Central Lima"
              },
              "firstAdmin": {
                "email": "admin@cafedemo.example",
                "displayName": "Ana García"
              }
            }
            """;

    @BeforeEach
    void setUp() {
        incorporateOrganization = mock(IncorporateOrganization.class);
        OperatorOrganizationSetupController controller =
                new OperatorOrganizationSetupController(incorporateOrganization);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        PlatformOperatorIdentity identity = new PlatformOperatorIdentity(OPERATOR_ID, "Operador Principal", 1L);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                identity, null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_OPERATOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Petición válida exitosa responde 201 Created con X-Request-ID y sin Location ni Replayed")
    void peticionValida_responde201() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        IncorporateOrganizationResponse response = new IncorporateOrganizationResponse(
                new IncorporateOrganizationResponse.OrganizationSummary(orgId, "CAFE-DEMO", "Café Demo S.A.", "America/Lima"),
                new IncorporateOrganizationResponse.SiteSummary(siteId, "Sede Central Lima"),
                new IncorporateOrganizationResponse.UserSummary(adminId, "Ana García", "admin@cafedemo.example", "ADMINISTRADOR"),
                IncorporateOrganizationResponse.DEFAULT_PENDING_SETUP
        );

        when(incorporateOrganization.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(IncorporateOrganizationCommand.class), any(byte[].class)))
                .thenReturn(new OrganizationOnboardingResult.Success(response, false));

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.organization.code").value("CAFE-DEMO"))
                .andExpect(jsonPath("$.site.displayName").value("Sede Central Lima"))
                .andExpect(jsonPath("$.firstAdmin.role").value("ADMINISTRADOR"));
    }

    @Test
    @DisplayName("Replay exitoso responde 201 Created con cabecera Idempotency-Replayed: true")
    void replayExitoso_responde201ConCabeceraReplayed() throws Exception {
        IncorporateOrganizationResponse response = new IncorporateOrganizationResponse(
                new IncorporateOrganizationResponse.OrganizationSummary(UUID.randomUUID(), "CAFE-DEMO", "Café Demo S.A.", "America/Lima"),
                new IncorporateOrganizationResponse.SiteSummary(UUID.randomUUID(), "Sede Central Lima"),
                new IncorporateOrganizationResponse.UserSummary(UUID.randomUUID(), "Ana García", "admin@cafedemo.example", "ADMINISTRADOR"),
                IncorporateOrganizationResponse.DEFAULT_PENDING_SETUP
        );

        when(incorporateOrganization.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(IncorporateOrganizationCommand.class), any(byte[].class)))
                .thenReturn(new OrganizationOnboardingResult.Success(response, true));

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.organization.code").value("CAFE-DEMO"));
    }

    @Test
    @DisplayName("Conflicto de código responde 409 ORGANIZATION_CODE_CONFLICT")
    void conflictoCodigo_responde409() throws Exception {
        when(incorporateOrganization.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(IncorporateOrganizationCommand.class), any(byte[].class)))
                .thenReturn(new OrganizationOnboardingResult.CodeConflict("CAFE-DEMO", "El código 'CAFE-DEMO' ya está asignado a otra organización.", false));

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.code").value("ORGANIZATION_CODE_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("El código 'CAFE-DEMO' ya está asignado a otra organización."));
    }

    @Test
    @DisplayName("Replay de conflicto de código responde 409 con Idempotency-Replayed: true")
    void replayConflictoCodigo_responde409ConReplayed() throws Exception {
        when(incorporateOrganization.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(IncorporateOrganizationCommand.class), any(byte[].class)))
                .thenReturn(new OrganizationOnboardingResult.CodeConflict("CAFE-DEMO", "El código 'CAFE-DEMO' ya está asignado a otra organización.", true));

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("ORGANIZATION_CODE_CONFLICT"));
    }

    @Test
    @DisplayName("Clave reutilizada responde 409 IDEMPOTENCY_KEY_REUSED")
    void claveReutilizada_responde409() throws Exception {
        when(incorporateOrganization.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(IncorporateOrganizationCommand.class), any(byte[].class)))
                .thenReturn(new OrganizationOnboardingResult.IdempotencyKeyReused());

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("Clave expirada responde 409 IDEMPOTENCY_KEY_EXPIRED")
    void claveExpirada_responde409() throws Exception {
        when(incorporateOrganization.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(IncorporateOrganizationCommand.class), any(byte[].class)))
                .thenReturn(new OrganizationOnboardingResult.IdempotencyKeyExpired());

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_EXPIRED"));
    }

    @Test
    @DisplayName("Idempotency-Key ausente responde 400 VALIDATION_FAILED")
    void idempotencyKeyAusente_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("idempotencyKey"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    @DisplayName("Campos obligatorios ausentes en cuerpo responden 400 VALIDATION_FAILED")
    void camposAusentes_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("Sin sesión de operador responde 401 SESSION_REQUIRED")
    void sinSesion_responde401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("Fallo de acceso a datos responde 503 SERVICE_UNAVAILABLE")
    void falloAccesoDatos_responde503() throws Exception {
        when(incorporateOrganization.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(IncorporateOrganizationCommand.class), any(byte[].class)))
                .thenThrow(new OperatorDataAccessException());

        mockMvc.perform(post("/api/v1/operator/setup/organizations")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }
}
