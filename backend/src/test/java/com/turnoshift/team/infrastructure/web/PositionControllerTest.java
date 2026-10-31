package com.turnoshift.team.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.security.idempotency.BusinessIdempotencyResult;
import com.turnoshift.security.idempotency.BusinessIdempotentOperationExecutor;
import com.turnoshift.team.application.ChangePositionStatusUseCase;
import com.turnoshift.team.application.CreatePositionUseCase;
import com.turnoshift.team.application.GetPositionsUseCase;
import com.turnoshift.team.application.UpdatePositionUseCase;
import com.turnoshift.team.domain.Position;
import com.turnoshift.team.infrastructure.PositionDataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: PositionController — API HTTP de Puestos")
class PositionControllerTest {

    @Mock
    private CreatePositionUseCase createPositionUseCase;

    @Mock
    private UpdatePositionUseCase updatePositionUseCase;

    @Mock
    private ChangePositionStatusUseCase changePositionStatusUseCase;

    @Mock
    private GetPositionsUseCase getPositionsUseCase;

    @Mock
    private BusinessIdempotentOperationExecutor executor;

    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID orgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID posId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID idemKey = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final Instant now = Instant.parse("2026-09-16T15:00:00Z");

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        PositionController controller = new PositionController(
                createPositionUseCase,
                updatePositionUseCase,
                changePositionStatusUseCase,
                getPositionsUseCase,
                executor,
                objectMapper
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        authenticateAs("ADMINISTRADOR");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                userId,
                orgId,
                "admin@empresa.example",
                "Admin Prueba",
                role,
                null,
                1L,
                "Empresa Prueba",
                "America/Bogota"
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                identity,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    // =========================================================================
    // 1. Extracción de identidad y sesión requerida
    // =========================================================================

    @Test
    @DisplayName("Sin sesión autenticada: devuelve 401 SESSION_REQUIRED")
    void sinSesion_devuelve401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/positions"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    // =========================================================================
    // 2. GET /api/v1/positions
    // =========================================================================

    @Test
    @DisplayName("GET /positions: deriva tenant exclusivamente de la sesión y devuelve lista 200")
    void getPositions_exito_derivaTenantDeSesion() throws Exception {
        Position pos1 = Position.reconstruct(posId, orgId, "MED-01", "Médico", true, 0L, now, now);
        when(getPositionsUseCase.execute(orgId, null)).thenReturn(List.of(pos1));

        mockMvc.perform(get("/api/v1/positions"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(posId.toString()))
                .andExpect(jsonPath("$[0].code").value("MED-01"))
                .andExpect(jsonPath("$[0].name").value("Médico"))
                .andExpect(jsonPath("$[0].isActive").value(true));

        verify(getPositionsUseCase).execute(orgId, null);
    }

    @Test
    @DisplayName("GET /positions?active=true: parsea activeFilter booleano")
    void getPositions_filtroActiveValido() throws Exception {
        when(getPositionsUseCase.execute(orgId, Boolean.TRUE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/positions").param("active", "true"))
                .andExpect(status().isOk());

        verify(getPositionsUseCase).execute(orgId, Boolean.TRUE);
    }

    @Test
    @DisplayName("GET /positions?active=invalido: responde 400 VALIDATION_FAILED")
    void getPositions_filtroActiveInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/positions").param("active", "invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /positions?active=: presente pero vacío responde 400 VALIDATION_FAILED")
    void getPositions_filtroActiveVacio_responde400() throws Exception {
        mockMvc.perform(get("/api/v1/positions").param("active", ""))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /positions?active=   : presente solo con espacios responde 400 VALIDATION_FAILED")
    void getPositions_filtroActiveEspacios_responde400() throws Exception {
        mockMvc.perform(get("/api/v1/positions").param("active", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // =========================================================================
    // 3. POST /api/v1/positions
    // =========================================================================

    @Test
    @DisplayName("POST /positions sin Idempotency-Key: responde 400 VALIDATION_FAILED")
    void post_sinIdempotencyKey_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MED-01\",\"name\":\"Médico\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /positions con Idempotency-Key no UUID: responde 400 VALIDATION_FAILED")
    void post_idempotencyKeyInvalida_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/positions")
                        .header("Idempotency-Key", "no-un-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MED-01\",\"name\":\"Médico\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /positions con body ausente o inválido: responde 400 VALIDATION_FAILED")
    void post_bodyInvalido_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/positions")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /positions con código inválido (espacios o formato): responde 400 VALIDATION_FAILED")
    void post_codigoInvalido_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/positions")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MED 01 CON ESPACIOS\",\"name\":\"Médico\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /positions nunca acepta tenant del body del cliente")
    void post_ignoraTenantDelClienteYDerivaDeSesion() throws Exception {
        PositionResponse posResponse = new PositionResponse(posId, "MED-01", "Médico", true, 0L, now, now);
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new PositionExecutionResult.Success(posResponse)));

        mockMvc.perform(post("/api/v1/positions")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"99999999-9999-9999-9999-999999999999\",\"code\":\"MED-01\",\"name\":\"Médico\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(posId.toString()));

        ArgumentCaptor<PositionIdempotentCommand> captor = ArgumentCaptor.forClass(PositionIdempotentCommand.class);
        verify(executor).execute(captor.capture(), any());
        assertThat(captor.getValue().organizationId()).isEqualTo(orgId);
        assertThat(captor.getValue().actorUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("POST /positions exitoso con Proceed: devuelve 201 Created")
    void post_exito_proceedDevuelve201() throws Exception {
        PositionResponse posResponse = new PositionResponse(posId, "MED-01", "Médico", true, 0L, now, now);
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new PositionExecutionResult.Success(posResponse)));

        mockMvc.perform(post("/api/v1/positions")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"med-01\",\"name\":\"Médico\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.code").value("MED-01"));
    }

    @Test
    @DisplayName("POST /positions replay exitoso: devuelve 201 Created con Idempotency-Replayed: true")
    void post_replayExito_devuelve201ConHeader() throws Exception {
        PositionResponse posResponse = new PositionResponse(posId, "MED-01", "Médico", true, 0L, now, now);
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Replay<>(new PositionExecutionResult.Success(posResponse)));

        mockMvc.perform(post("/api/v1/positions")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MED-01\",\"name\":\"Médico\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("MED-01"));
    }

    @Test
    @DisplayName("POST /positions rechazo por código duplicado: devuelve 409 POSITION_CODE_ALREADY_EXISTS")
    void post_rechazoCodigoDuplicado_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new PositionExecutionResult.CodeAlreadyExists()));

        mockMvc.perform(post("/api/v1/positions")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MED-01\",\"name\":\"Médico\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("POSITION_CODE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /positions clave reutilizada con huella distinta: devuelve 409 IDEMPOTENCY_KEY_REUSED")
    void post_claveReutilizada_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.KeyReused<>());

        mockMvc.perform(post("/api/v1/positions")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MED-01\",\"name\":\"Médico\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("POST /positions clave expirada: devuelve 409 IDEMPOTENCY_KEY_EXPIRED")
    void post_claveExpirada_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Expired<>());

        mockMvc.perform(post("/api/v1/positions")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MED-01\",\"name\":\"Médico\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_EXPIRED"));
    }

    // =========================================================================
    // 4. PUT /api/v1/positions/{positionId}
    // =========================================================================

    @Test
    @DisplayName("PUT /positions/{id} con positionId no UUID: responde 400 VALIDATION_FAILED")
    void put_idInvalido_responde400() throws Exception {
        mockMvc.perform(put("/api/v1/positions/no-un-uuid")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PUT /positions/{id} con expectedLockVersion negativa: responde 400 VALIDATION_FAILED")
    void put_lockVersionNegativa_responde400() throws Exception {
        mockMvc.perform(put("/api/v1/positions/" + posId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo Nombre\",\"expectedLockVersion\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PUT /positions/{id} exitoso con Proceed: devuelve 200 OK")
    void put_exito_devuelve200() throws Exception {
        PositionResponse posResponse = new PositionResponse(posId, "MED-01", "Nuevo Nombre", true, 1L, now, now);
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new PositionExecutionResult.Success(posResponse)));

        mockMvc.perform(put("/api/v1/positions/" + posId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.name").value("Nuevo Nombre"))
                .andExpect(jsonPath("$.lockVersion").value(1L));
    }

    @Test
    @DisplayName("PUT /positions/{id} rechazo puesto no encontrado: devuelve 404 POSITION_NOT_FOUND")
    void put_notFound_devuelve404() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new PositionExecutionResult.NotFound()));

        mockMvc.perform(put("/api/v1/positions/" + posId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("POSITION_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /positions/{id} rechazo versión desactualizada: devuelve 409 CONCURRENT_MODIFICATION")
    void put_staleVersion_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new PositionExecutionResult.StaleVersion()));

        mockMvc.perform(put("/api/v1/positions/" + posId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // =========================================================================
    // 5. PATCH /api/v1/positions/{positionId}/status
    // =========================================================================

    @Test
    @DisplayName("PATCH /positions/{id}/status exitoso: devuelve 200 OK con nuevo estado")
    void patch_statusExito_devuelve200() throws Exception {
        PositionResponse posResponse = new PositionResponse(posId, "MED-01", "Médico", false, 1L, now, now);
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new PositionExecutionResult.Success(posResponse)));

        mockMvc.perform(patch("/api/v1/positions/" + posId + "/status")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false,\"expectedLockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    // =========================================================================
    // 6. Error de infraestructura / persistencia: 503 SERVICE_UNAVAILABLE
    // =========================================================================

    @Test
    @DisplayName("Fallo de infraestructura en executor: propaga y responde 503 SERVICE_UNAVAILABLE")
    void falloInfraestructura_responde503() throws Exception {
        when(executor.execute(any(), any()))
                .thenThrow(new PositionDataAccessException(new RuntimeException("Error de base de datos")));

        mockMvc.perform(post("/api/v1/positions")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MED-01\",\"name\":\"Médico\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }
}
