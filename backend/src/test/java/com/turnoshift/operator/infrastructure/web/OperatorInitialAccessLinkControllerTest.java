package com.turnoshift.operator.infrastructure.web;

import com.turnoshift.identity.domain.TokenDataAccessException;
import com.turnoshift.operator.application.EmitInitialAccessLink;
import com.turnoshift.operator.domain.EmitInitialAccessLinkCommand;
import com.turnoshift.operator.domain.EmitInitialAccessLinkResult;
import com.turnoshift.operator.domain.OperatorDataAccessException;
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

import java.time.Instant;
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

@DisplayName("Unit: OperatorInitialAccessLinkController — endpoint POST /api/v1/operator/setup/organizations/{orgId}/initial-access-link")
class OperatorInitialAccessLinkControllerTest {

    private EmitInitialAccessLink emitInitialAccessLink;
    private MockMvc mockMvc;

    private static final UUID OPERATOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String IDEMPOTENCY_KEY = "22222222-2222-2222-2222-222222222222";
    private static final String ORG_ID = "33333333-3333-3333-3333-333333333333";
    private static final String ADMIN_ID = "44444444-4444-4444-4444-444444444444";

    private static final String VALID_BODY = """
            {
              "adminUserId": "%s",
              "expectedVersion": 0
            }
            """.formatted(ADMIN_ID);

    @BeforeEach
    void setUp() {
        emitInitialAccessLink = mock(EmitInitialAccessLink.class);
        OperatorInitialAccessLinkController controller =
                new OperatorInitialAccessLinkController(emitInitialAccessLink);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        PlatformOperatorIdentity identity = new PlatformOperatorIdentity(OPERATOR_ID, "Operador Test", 1L);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                identity, null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_OPERATOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Petición válida exitosa responde 200 OK con setupLink, expiresAt y cabeceras de seguridad")
    void peticionValida_responde200() throws Exception {
        String setupLink = "https://app.turnoshift.example/setup#token=testRawToken";
        Instant expiresAt = Instant.parse("2026-09-16T12:30:00Z");

        when(emitInitialAccessLink.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(EmitInitialAccessLinkCommand.class), any(byte[].class)))
                .thenReturn(new EmitInitialAccessLinkResult.Success(setupLink, expiresAt));

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.setupLink").value(setupLink))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-16T12:30:00Z"));
    }

    @Test
    @DisplayName("Replay con misma clave responde 409 INITIAL_LINK_ALREADY_EMITTED con cabecera Idempotency-Replayed: true")
    void replayMismaClave_responde409() throws Exception {
        String msg = "El enlace inicial ya fue emitido para esta clave de idempotencia.";
        when(emitInitialAccessLink.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(EmitInitialAccessLinkCommand.class), any(byte[].class)))
                .thenReturn(new EmitInitialAccessLinkResult.LinkAlreadyEmitted(msg));

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.type").value("/problems/initial-link-already-emitted"))
                .andExpect(jsonPath("$.code").value("INITIAL_LINK_ALREADY_EMITTED"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(msg));
    }

    @Test
    @DisplayName("Reuso de clave con huella diferente responde 409 IDEMPOTENCY_KEY_REUSED")
    void reusoClave_responde409() throws Exception {
        when(emitInitialAccessLink.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(EmitInitialAccessLinkCommand.class), any(byte[].class)))
                .thenReturn(new EmitInitialAccessLinkResult.IdempotencyKeyReused());

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("Clave expirada responde 409 IDEMPOTENCY_KEY_EXPIRED")
    void claveExpirada_responde409() throws Exception {
        when(emitInitialAccessLink.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(EmitInitialAccessLinkCommand.class), any(byte[].class)))
                .thenReturn(new EmitInitialAccessLinkResult.IdempotencyKeyExpired());

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_EXPIRED"));
    }

    @Test
    @DisplayName("Organización inexistente responde 404 RESOURCE_NOT_FOUND")
    void orgInexistente_responde404() throws Exception {
        when(emitInitialAccessLink.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(EmitInitialAccessLinkCommand.class), any(byte[].class)))
                .thenReturn(new EmitInitialAccessLinkResult.OrganizationNotFound(UUID.fromString(ORG_ID)));

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Administrador inexistente responde 404 RESOURCE_NOT_FOUND")
    void adminInexistente_responde404() throws Exception {
        when(emitInitialAccessLink.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(EmitInitialAccessLinkCommand.class), any(byte[].class)))
                .thenReturn(new EmitInitialAccessLinkResult.AdminNotFound(UUID.fromString(ORG_ID), UUID.fromString(ADMIN_ID)));

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Transición de estado inválida responde 409 INVALID_STATE_TRANSITION")
    void transicionInvalida_responde409() throws Exception {
        when(emitInitialAccessLink.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(EmitInitialAccessLinkCommand.class), any(byte[].class)))
                .thenReturn(new EmitInitialAccessLinkResult.InvalidStateTransition("El administrador ya tiene contraseña configurada.", false));

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("El administrador ya tiene contraseña configurada."));
    }

    @Test
    @DisplayName("Conflicto de concurrencia optimista responde 409 CONCURRENT_MODIFICATION")
    void conflictoVersion_responde409() throws Exception {
        when(emitInitialAccessLink.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(EmitInitialAccessLinkCommand.class), any(byte[].class)))
                .thenReturn(new EmitInitialAccessLinkResult.VersionConflict(0L, 1L, "La versión esperada (0) no coincide con la versión actual (1) del administrador.", false));

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("Falta de cabecera Idempotency-Key responde 400 VALIDATION_FAILED")
    void faltaIdempotencyKey_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("idempotencyKey"));
    }

    @Test
    @DisplayName("expectedVersion negativo en cuerpo responde 400 VALIDATION_FAILED")
    void expectedVersionNegativo_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminUserId": "%s",
                                  "expectedVersion": -1
                                }
                                """.formatted(ADMIN_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));
    }

    @Test
    @DisplayName("Sin sesión activa de operador responde 401 SESSION_REQUIRED")
    void sinSesion_responde401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("Error técnico de base de datos responde 503 SERVICE_UNAVAILABLE")
    void errorTecnico_responde503() throws Exception {
        when(emitInitialAccessLink.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(EmitInitialAccessLinkCommand.class), any(byte[].class)))
                .thenThrow(new OperatorDataAccessException());

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Fallo técnico por TokenDataAccessException responde 503 SERVICE_UNAVAILABLE seguro con nuevo requestId y sin detalles internos")
    void errorTecnicoTokenDataAccessException_responde503Seguro() throws Exception {
        when(emitInitialAccessLink.execute(eq(OPERATOR_ID), eq(UUID.fromString(IDEMPOTENCY_KEY)), any(EmitInitialAccessLinkCommand.class), any(byte[].class)))
                .thenThrow(new TokenDataAccessException());

        mockMvc.perform(post("/api/v1/operator/setup/organizations/{orgId}/initial-access-link", ORG_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.type").value("/problems/service-unavailable"))
                .andExpect(jsonPath("$.title").value("Servicio no disponible"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("El servicio no está disponible temporalmente. Intente más tarde."))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }
}
