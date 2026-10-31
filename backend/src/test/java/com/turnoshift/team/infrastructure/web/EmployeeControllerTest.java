package com.turnoshift.team.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.security.idempotency.BusinessIdempotencyDataAccessException;
import com.turnoshift.security.idempotency.BusinessIdempotencyResult;
import com.turnoshift.security.idempotency.BusinessIdempotentOperationExecutor;
import com.turnoshift.team.application.ActivateEmployeeUseCase;
import com.turnoshift.team.application.CreateEmployeeUseCase;
import com.turnoshift.team.application.DeactivateEmployeeUseCase;
import com.turnoshift.team.application.GetEmployeeUseCase;
import com.turnoshift.team.application.GetEmployeesUseCase;
import com.turnoshift.team.application.UpdateEmployeeUseCase;
import com.turnoshift.team.domain.DeactivationPreview;
import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeNotFoundException;
import com.turnoshift.team.infrastructure.EmployeeDataAccessException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: EmployeeController — API HTTP de Colaboradores")
class EmployeeControllerTest {

    @Mock
    private CreateEmployeeUseCase createEmployeeUseCase;

    @Mock
    private GetEmployeesUseCase getEmployeesUseCase;

    @Mock
    private GetEmployeeUseCase getEmployeeUseCase;

    @Mock
    private UpdateEmployeeUseCase updateEmployeeUseCase;

    @Mock
    private ActivateEmployeeUseCase activateEmployeeUseCase;

    @Mock
    private DeactivateEmployeeUseCase deactivateEmployeeUseCase;

    @Mock
    private BusinessIdempotentOperationExecutor executor;

    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID orgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID empId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID posId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID siteId = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private final UUID idemKey = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private final Instant now = Instant.parse("2026-09-24T12:00:00Z");

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        EmployeeController controller = new EmployeeController(
                createEmployeeUseCase,
                getEmployeesUseCase,
                getEmployeeUseCase,
                updateEmployeeUseCase,
                activateEmployeeUseCase,
                deactivateEmployeeUseCase,
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

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    // =========================================================================
    // 2. GET /api/v1/employees
    // =========================================================================

    @Test
    @DisplayName("GET /employees: deriva tenant de sesión y devuelve lista 200")
    void getEmployees_exito_derivaTenant() throws Exception {
        Employee emp = Employee.reconstruct(empId, orgId, "EMP-01", "Juan Pérez", posId, siteId, true, 0L, now, now);
        when(getEmployeesUseCase.execute(orgId, null)).thenReturn(List.of(emp));

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(empId.toString()))
                .andExpect(jsonPath("$[0].code").value("EMP-01"))
                .andExpect(jsonPath("$[0].displayName").value("Juan Pérez"))
                .andExpect(jsonPath("$[0].positionId").value(posId.toString()))
                .andExpect(jsonPath("$[0].siteId").value(siteId.toString()))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].lockVersion").value(0L));

        verify(getEmployeesUseCase).execute(orgId, null);
    }

    @Test
    @DisplayName("GET /employees?active=true: parsea activeFilter booleano true")
    void getEmployees_filtroActiveTrue() throws Exception {
        when(getEmployeesUseCase.execute(orgId, Boolean.TRUE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/employees").param("active", "true"))
                .andExpect(status().isOk());

        verify(getEmployeesUseCase).execute(orgId, Boolean.TRUE);
    }

    @Test
    @DisplayName("GET /employees?active=false: parsea activeFilter booleano false")
    void getEmployees_filtroActiveFalse() throws Exception {
        when(getEmployeesUseCase.execute(orgId, Boolean.FALSE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/employees").param("active", "false"))
                .andExpect(status().isOk());

        verify(getEmployeesUseCase).execute(orgId, Boolean.FALSE);
    }

    @Test
    @DisplayName("GET /employees?active=invalido: responde 400 VALIDATION_FAILED")
    void getEmployees_filtroActiveInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/employees").param("active", "invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /employees?active=: presente pero vacío responde 400 VALIDATION_FAILED")
    void getEmployees_filtroActiveVacio() throws Exception {
        mockMvc.perform(get("/api/v1/employees").param("active", ""))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /employees?active=   : solo espacios responde 400 VALIDATION_FAILED")
    void getEmployees_filtroActiveEspacios() throws Exception {
        mockMvc.perform(get("/api/v1/employees").param("active", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // =========================================================================
    // 3. GET /api/v1/employees/{employeeId}
    // =========================================================================

    @Test
    @DisplayName("GET /employees/{id}: UUID inválido responde 400 VALIDATION_FAILED")
    void getEmployee_idInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/employees/no-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /employees/{id}: colaborador no encontrado responde 404 EMPLOYEE_NOT_FOUND")
    void getEmployee_noEncontrado() throws Exception {
        when(getEmployeeUseCase.execute(orgId, empId))
                .thenThrow(new EmployeeNotFoundException(empId));

        mockMvc.perform(get("/api/v1/employees/" + empId))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /employees/{id}: colaborador encontrado responde 200")
    void getEmployee_exito() throws Exception {
        Employee emp = Employee.reconstruct(empId, orgId, "EMP-01", "Juan Pérez", posId, siteId, true, 0L, now, now);
        when(getEmployeeUseCase.execute(orgId, empId)).thenReturn(emp);

        mockMvc.perform(get("/api/v1/employees/" + empId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(empId.toString()))
                .andExpect(jsonPath("$.code").value("EMP-01"))
                .andExpect(jsonPath("$.displayName").value("Juan Pérez"));
    }

    // =========================================================================
    // 4. POST /api/v1/employees
    // =========================================================================

    @Test
    @DisplayName("POST /employees sin Idempotency-Key: responde 400 VALIDATION_FAILED")
    void post_sinIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /employees con Idempotency-Key no UUID: responde 400 VALIDATION_FAILED")
    void post_idempotencyKeyInvalida() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", "no-un-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /employees con body ausente: responde 400 VALIDATION_FAILED")
    void post_bodyAusente() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /employees con JSON malformado: responde 400 VALIDATION_FAILED")
    void post_jsonMalformado() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /employees sin positionId: responde 400 VALIDATION_FAILED")
    void post_sinPositionId() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /employees con código vacío o espacios: responde 400 VALIDATION_FAILED")
    void post_codigoVacio_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"   \",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /employees con código mayor a 50 caracteres: responde 400 VALIDATION_FAILED")
    void post_codigoDemasiadoLargo_responde400() throws Exception {
        String longCode = "A".repeat(51);
        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + longCode + "\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /employees con displayName vacío: responde 400 VALIDATION_FAILED")
    void post_displayNameVacio() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"   \",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /employees ignora tenant del cliente y deriva de sesión")
    void post_derivaTenantDeSesion() throws Exception {
        EmployeeResponse response = new EmployeeResponse(empId, "EMP-01", "Juan Pérez", posId, siteId, true, 0L);
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.Success(response)));

        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"99999999-9999-9999-9999-999999999999\",\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(empId.toString()));

        ArgumentCaptor<EmployeeIdempotentCommand> captor = ArgumentCaptor.forClass(EmployeeIdempotentCommand.class);
        verify(executor).execute(captor.capture(), any());
        assertThat(captor.getValue().organizationId()).isEqualTo(orgId);
        assertThat(captor.getValue().actorUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("POST /employees exitoso con Proceed: devuelve 201 Created sin header de replay")
    void post_exito_proceedDevuelve201() throws Exception {
        EmployeeResponse response = new EmployeeResponse(empId, "EMP-01", "Juan Pérez", posId, siteId, true, 0L);
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.Success(response)));

        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"emp-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.code").value("EMP-01"))
                .andExpect(jsonPath("$.displayName").value("Juan Pérez"));
    }

    @Test
    @DisplayName("POST /employees replay exitoso: devuelve 201 Created con Idempotency-Replayed: true")
    void post_replayExito_devuelve201ConHeader() throws Exception {
        EmployeeResponse response = new EmployeeResponse(empId, "EMP-01", "Juan Pérez", posId, siteId, true, 0L);
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Replay<>(new EmployeeExecutionResult.Success(response)));

        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("EMP-01"));
    }

    @Test
    @DisplayName("POST /employees código duplicado: devuelve 409 EMPLOYEE_CODE_ALREADY_EXISTS")
    void post_codigoDuplicado_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.CodeAlreadyExists()));

        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_CODE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /employees puesto no encontrado: devuelve 404 POSITION_NOT_FOUND")
    void post_puestoNoEncontrado_devuelve404() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.PositionNotFound()));

        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSITION_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /employees puesto inactivo: devuelve 409 POSITION_INACTIVE")
    void post_puestoInactivo_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.PositionInactive()));

        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POSITION_INACTIVE"));
    }

    @Test
    @DisplayName("POST /employees sin sede activa: devuelve 409 NO_ACTIVE_SITE")
    void post_sinSedeActiva_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.NoActiveSite()));

        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_SITE"));
    }

    @Test
    @DisplayName("POST /employees límite de licencias excedido: devuelve 409 EMPLOYEE_LICENSE_LIMIT_REACHED")
    void post_limiteLicencias_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.LicenseLimitExceeded()));

        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LICENSE_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("POST /employees clave reutilizada: devuelve 409 IDEMPOTENCY_KEY_REUSED")
    void post_claveReutilizada_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.KeyReused<>());

        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("POST /employees clave expirada: devuelve 409 IDEMPOTENCY_KEY_EXPIRED")
    void post_claveExpirada_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Expired<>());

        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_EXPIRED"));
    }

    // =========================================================================
    // 5. PATCH /api/v1/employees/{employeeId}
    // =========================================================================

    @Test
    @DisplayName("PATCH /employees/{id} con employeeId inválido: responde 400 VALIDATION_FAILED")
    void patch_idInvalido() throws Exception {
        mockMvc.perform(patch("/api/v1/employees/no-un-uuid")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PATCH /employees/{id} sin Idempotency-Key: responde 400 VALIDATION_FAILED")
    void patch_sinIdempotencyKey() throws Exception {
        mockMvc.perform(patch("/api/v1/employees/" + empId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PATCH /employees/{id} sin expectedLockVersion: responde 400 VALIDATION_FAILED")
    void patch_sinExpectedLockVersion() throws Exception {
        mockMvc.perform(patch("/api/v1/employees/" + empId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo Nombre\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PATCH /employees/{id} con expectedLockVersion negativa: responde 400 VALIDATION_FAILED")
    void patch_lockVersionNegativa() throws Exception {
        mockMvc.perform(patch("/api/v1/employees/" + empId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PATCH /employees/{id} sin displayName ni positionId: responde 400 VALIDATION_FAILED")
    void patch_sinCamposAModificar() throws Exception {
        mockMvc.perform(patch("/api/v1/employees/" + empId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PATCH /employees/{id} exitoso con Proceed: devuelve 200 OK")
    void patch_exito_devuelve200() throws Exception {
        EmployeeResponse response = new EmployeeResponse(empId, "EMP-01", "Nombre Actualizado", posId, siteId, true, 1L);
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.Success(response)));

        mockMvc.perform(patch("/api/v1/employees/" + empId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nombre Actualizado\",\"expectedLockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.displayName").value("Nombre Actualizado"))
                .andExpect(jsonPath("$.lockVersion").value(1L));
    }

    @Test
    @DisplayName("PATCH /employees/{id} colaborador no encontrado: devuelve 404 EMPLOYEE_NOT_FOUND")
    void patch_notFound_devuelve404() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.NotFound()));

        mockMvc.perform(patch("/api/v1/employees/" + empId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /employees/{id} versión desactualizada: devuelve 409 CONCURRENT_MODIFICATION")
    void patch_staleVersion_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.StaleVersion()));

        mockMvc.perform(patch("/api/v1/employees/" + empId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // =========================================================================
    // 6. POST /api/v1/employees/{employeeId}/activate
    // =========================================================================

    @Test
    @DisplayName("POST /employees/{id}/activate exitoso con Proceed: devuelve 200 OK")
    void activate_exito_devuelve200() throws Exception {
        EmployeeResponse response = new EmployeeResponse(empId, "EMP-01", "Juan Pérez", posId, siteId, true, 1L);
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.Success(response)));

        mockMvc.perform(post("/api/v1/employees/" + empId + "/activate")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /employees/{id}/activate límite licencias: devuelve 409 EMPLOYEE_LICENSE_LIMIT_REACHED")
    void activate_limiteLicencias_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.LicenseLimitExceeded()));

        mockMvc.perform(post("/api/v1/employees/" + empId + "/activate")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LICENSE_LIMIT_REACHED"));
    }

    // =========================================================================
    // 7. POST /api/v1/employees/{employeeId}/deactivate
    // =========================================================================

    @Test
    @DisplayName("POST /employees/{id}/deactivate exitoso con Proceed: devuelve 200 OK")
    void deactivate_exito_devuelve200() throws Exception {
        EmployeeResponse response = new EmployeeResponse(empId, "EMP-01", "Juan Pérez", posId, siteId, false, 1L);
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.Success(response)));

        mockMvc.perform(post("/api/v1/employees/" + empId + "/deactivate")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /employees/{id}/deactivate versión desactualizada: devuelve 409 CONCURRENT_MODIFICATION")
    void deactivate_staleVersion_devuelve409() throws Exception {
        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(new EmployeeExecutionResult.StaleVersion()));

        mockMvc.perform(post("/api/v1/employees/" + empId + "/deactivate")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // =========================================================================
    // 8. POST /api/v1/employees/{employeeId}/deactivation-preview
    // =========================================================================

    @Test
    @DisplayName("POST /employees/{id}/deactivation-preview con UUID inválido: responde 400 VALIDATION_FAILED")
    void deactivationPreview_idInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/employees/no-un-uuid/deactivation-preview"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /employees/{id}/deactivation-preview colaborador no encontrado: devuelve 404 EMPLOYEE_NOT_FOUND")
    void deactivationPreview_noEncontrado() throws Exception {
        when(deactivateEmployeeUseCase.preview(orgId, empId))
                .thenThrow(new EmployeeNotFoundException(empId));

        mockMvc.perform(post("/api/v1/employees/" + empId + "/deactivation-preview"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /employees/{id}/deactivation-preview exitoso: devuelve 200 OK con canDeactivate y blockers")
    void deactivationPreview_exito() throws Exception {
        DeactivationPreview preview = new DeactivationPreview(empId, "Juan Pérez", true, List.of());
        when(deactivateEmployeeUseCase.preview(orgId, empId)).thenReturn(preview);

        mockMvc.perform(post("/api/v1/employees/" + empId + "/deactivation-preview"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.employeeId").value(empId.toString()))
                .andExpect(jsonPath("$.displayName").value("Juan Pérez"))
                .andExpect(jsonPath("$.canDeactivate").value(true))
                .andExpect(jsonPath("$.blockers").isArray());
    }

    // =========================================================================
    // 9. Error de infraestructura / persistencia: 503 SERVICE_UNAVAILABLE
    // =========================================================================

    @Test
    @DisplayName("Fallo de infraestructura en EmployeeDataAccessException: responde 503 SERVICE_UNAVAILABLE")
    void falloEmployeeDataAccessException_responde503() throws Exception {
        when(getEmployeesUseCase.execute(orgId, null))
                .thenThrow(new EmployeeDataAccessException(new RuntimeException("Error JDBC")));

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Fallo de infraestructura en BusinessIdempotencyDataAccessException: responde 503 SERVICE_UNAVAILABLE")
    void falloBusinessIdempotencyDataAccessException_responde503() throws Exception {
        when(executor.execute(any(), any()))
                .thenThrow(new BusinessIdempotencyDataAccessException(new RuntimeException("Error de base de datos")));

        mockMvc.perform(post("/api/v1/employees")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EMP-01\",\"displayName\":\"Juan Pérez\",\"positionId\":\"" + posId + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }
}
