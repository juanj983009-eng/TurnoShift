package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.security.idempotency.BusinessIdempotencyDataAccessException;
import com.turnoshift.security.idempotency.BusinessIdempotencyResult;
import com.turnoshift.security.idempotency.BusinessIdempotentOperationExecutor;
import com.turnoshift.unavailability.application.CancelUnavailabilityUseCase;
import com.turnoshift.unavailability.application.CreateUnavailabilityUseCase;
import com.turnoshift.unavailability.application.GetUnavailabilityUseCase;
import com.turnoshift.unavailability.application.ListEmployeeUnavailabilitiesUseCase;
import com.turnoshift.unavailability.application.ListOrganizationUnavailabilitiesUseCase;
import com.turnoshift.unavailability.application.UpdateUnavailabilityUseCase;
import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.OrganizationTimezonePort;
import com.turnoshift.unavailability.domain.RestrictedNote;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import com.turnoshift.unavailability.domain.exceptions.EmployeeNotFoundException;
import com.turnoshift.unavailability.domain.exceptions.PastIntervalModificationProhibitedException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityAlreadyCancelledException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityConcurrentModificationException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityNotFoundException;
import com.turnoshift.unavailability.infrastructure.persistence.UnavailabilityDataAccessException;
import com.turnoshift.unavailability.infrastructure.web.dto.EmployeeUnavailabilityResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: UnavailabilityController — API HTTP de Indisponibilidades")
class UnavailabilityControllerTest {

    @Mock
    private GetUnavailabilityUseCase getUnavailabilityUseCase;

    @Mock
    private ListEmployeeUnavailabilitiesUseCase listEmployeeUnavailabilitiesUseCase;

    @Mock
    private ListOrganizationUnavailabilitiesUseCase listOrganizationUnavailabilitiesUseCase;

    @Mock
    private CreateUnavailabilityUseCase createUnavailabilityUseCase;

    @Mock
    private UpdateUnavailabilityUseCase updateUnavailabilityUseCase;

    @Mock
    private CancelUnavailabilityUseCase cancelUnavailabilityUseCase;

    @Mock
    private OrganizationTimezonePort organizationTimezonePort;

    @Mock
    private BusinessIdempotentOperationExecutor executor;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private MockMvc mockMvc;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID orgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID empId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID unavailId = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final Instant now = Instant.parse("2026-06-01T12:00:00Z");
    private final Instant startsAt = Instant.parse("2026-06-10T08:00:00Z");
    private final Instant endsAt = Instant.parse("2026-06-10T17:00:00Z");

    @BeforeEach
    void setUp() {
        UnavailabilityController controller = new UnavailabilityController(
                getUnavailabilityUseCase,
                listEmployeeUnavailabilitiesUseCase,
                listOrganizationUnavailabilitiesUseCase,
                createUnavailabilityUseCase,
                updateUnavailabilityUseCase,
                cancelUnavailabilityUseCase,
                organizationTimezonePort,
                executor,
                objectMapper
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(String role) {
        String cleanRole = role.startsWith("ROLE_") ? role.substring(5) : role;
        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                userId,
                orgId,
                "admin@empresa.example",
                "Admin Prueba",
                cleanRole,
                null,
                1L,
                "Empresa Prueba",
                "America/Bogota"
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                identity,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + cleanRole))
        ));
        SecurityContextHolder.setContext(context);
    }

    private EmployeeUnavailability sampleUnavailability(UUID id, UUID employeeId) {
        return EmployeeUnavailability.reconstruct(
                id,
                orgId,
                employeeId,
                new TimeInterval(startsAt, endsAt),
                UnavailabilityReason.VACACIONES,
                new RestrictedNote("Nota confidencial médica"),
                userId,
                now,
                now,
                0L,
                null
        );
    }

    // =========================================================================
    // 1. Sesión Requerida (401)
    // =========================================================================

    @Test
    @DisplayName("GET sin sesión activa devuelve 401 SESSION_REQUIRED en los tres endpoints")
    void sinSesion_devuelve401SessionRequired() throws Exception {
        // 1. Por empleado
        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("SESSION_REQUIRED")));

        // 2. Ventana organizacional
        mockMvc.perform(get("/api/v1/unavailabilities?from=2026-06-01T00:00:00Z&to=2026-06-30T00:00:00Z"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("SESSION_REQUIRED")));

        // 3. Detalle por ID
        mockMvc.perform(get("/api/v1/unavailabilities/" + unavailId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("SESSION_REQUIRED")));
    }

    // =========================================================================
    // 2. GET /api/v1/employees/{employeeId}/unavailabilities
    // =========================================================================

    @Test
    @DisplayName("GET por empleado exitoso devuelve 200 OK con lista de indisponibilidades y campos restringidos")
    void getEmployeeUnavailabilities_exitoso_devuelve200() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        EmployeeUnavailability item = sampleUnavailability(unavailId, empId);
        when(listEmployeeUnavailabilitiesUseCase.execute(orgId, empId, false))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(unavailId.toString())))
                .andExpect(jsonPath("$.items[0].employeeId", is(empId.toString())))
                .andExpect(jsonPath("$.items[0].reasonCode", is("VACACIONES")))
                .andExpect(jsonPath("$.items[0].restrictedNote", is("Nota confidencial médica")))
                .andExpect(jsonPath("$.items[0].cancelled", is(false)))
                .andExpect(jsonPath("$.items[0].lockVersion", is(0)));

        verify(listEmployeeUnavailabilitiesUseCase).execute(orgId, empId, false);
    }

    @Test
    @DisplayName("GET por empleado con filtro de rango ISO-8601 devuelve 200 OK")
    void getEmployeeUnavailabilities_conRangoFechas_devuelve200() throws Exception {
        setAuthenticatedUser("ROLE_SUPERVISOR");
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");
        EmployeeUnavailability item = sampleUnavailability(unavailId, empId);

        when(listEmployeeUnavailabilitiesUseCase.execute(orgId, empId, from, to, false))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-30T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items", hasSize(1)));

        verify(listEmployeeUnavailabilitiesUseCase).execute(orgId, empId, from, to, false);
    }

    @Test
    @DisplayName("GET por empleado valida estrictamente includeCancelled ('true', 'false', valores inválidos dan 400)")
    void getEmployeeUnavailabilities_validacionIncludeCancelled() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");

        // 1. includeCancelled=true
        when(listEmployeeUnavailabilitiesUseCase.execute(orgId, empId, true))
                .thenReturn(List.of());
        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities")
                        .param("includeCancelled", "true"))
                .andExpect(status().isOk());
        verify(listEmployeeUnavailabilitiesUseCase).execute(orgId, empId, true);

        // 2. includeCancelled=false
        when(listEmployeeUnavailabilitiesUseCase.execute(orgId, empId, false))
                .thenReturn(List.of());
        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities")
                        .param("includeCancelled", "false"))
                .andExpect(status().isOk());
        verify(listEmployeeUnavailabilitiesUseCase).execute(orgId, empId, false);

        // 3. includeCancelled con mayúsculas válidas
        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities")
                        .param("includeCancelled", "TRUE"))
                .andExpect(status().isOk());

        // 4. includeCancelled inválido (ej. 'si', '1', 'foo') devuelve 400 VALIDATION_FAILED
        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities")
                        .param("includeCancelled", "si"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities")
                        .param("includeCancelled", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("GET por empleado con fechas parciales o invertidas devuelve 400 VALIDATION_FAILED")
    void getEmployeeUnavailabilities_fechasInvalidas_devuelve400() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");

        // Solo 'from' sin 'to'
        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities")
                        .param("from", "2026-06-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        // Solo 'to' sin 'from'
        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities")
                        .param("to", "2026-06-30T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        // Formato no ISO-8601
        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities")
                        .param("from", "fecha-invalida")
                        .param("to", "2026-06-30T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        // from >= to
        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities")
                        .param("from", "2026-06-30T00:00:00Z")
                        .param("to", "2026-06-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("GET por empleado con employeeId inválido devuelve 400 VALIDATION_FAILED")
    void getEmployeeUnavailabilities_uuidInvalido_devuelve400() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");

        mockMvc.perform(get("/api/v1/employees/no-es-uuid/unavailabilities"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        verifyNoInteractions(listEmployeeUnavailabilitiesUseCase);
    }

    @Test
    @DisplayName("GET por empleado cuando no existe o está inactivo devuelve 404 EMPLOYEE_NOT_FOUND")
    void getEmployeeUnavailabilities_empleadoNoExiste_devuelve404() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        when(listEmployeeUnavailabilitiesUseCase.execute(orgId, empId, false))
                .thenThrow(new EmployeeNotFoundException("Colaborador no encontrado o inactivo"));

        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("EMPLOYEE_NOT_FOUND")));
    }

    // =========================================================================
    // 3. GET /api/v1/unavailabilities (Ventana Organizacional)
    // =========================================================================

    @Test
    @DisplayName("GET organizacional exitoso devuelve 200 OK con lista de indisponibilidades")
    void getOrganizationUnavailabilities_exitoso_devuelve200() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T00:00:00Z");
        EmployeeUnavailability item = sampleUnavailability(unavailId, empId);

        when(listOrganizationUnavailabilitiesUseCase.execute(orgId, from, to, false))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/unavailabilities")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-30T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(unavailId.toString())));

        verify(listOrganizationUnavailabilitiesUseCase).execute(orgId, from, to, false);
    }

    @Test
    @DisplayName("GET organizacional requiere obligatoriamente from y to; faltantes dan 400 VALIDATION_FAILED")
    void getOrganizationUnavailabilities_fromToObligatorios() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");

        // Sin parámetros
        mockMvc.perform(get("/api/v1/unavailabilities"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        // Solo 'from'
        mockMvc.perform(get("/api/v1/unavailabilities")
                        .param("from", "2026-06-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        // Solo 'to'
        mockMvc.perform(get("/api/v1/unavailabilities")
                        .param("to", "2026-06-30T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        verifyNoInteractions(listOrganizationUnavailabilitiesUseCase);
    }

    @Test
    @DisplayName("GET organizacional con rango superior a 92 días devuelve 400 VALIDATION_FAILED")
    void getOrganizationUnavailabilities_rangoMayorA92Dias_devuelve400() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");

        // 93 días de diferencia
        mockMvc.perform(get("/api/v1/unavailabilities")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-04-05T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        verifyNoInteractions(listOrganizationUnavailabilitiesUseCase);
    }

    @Test
    @DisplayName("GET organizacional valida includeCancelled estrictamente")
    void getOrganizationUnavailabilities_includeCancelledInvalido_devuelve400() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");

        mockMvc.perform(get("/api/v1/unavailabilities")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-15T00:00:00Z")
                        .param("includeCancelled", "no"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        verifyNoInteractions(listOrganizationUnavailabilitiesUseCase);
    }

    // =========================================================================
    // 4. GET /api/v1/unavailabilities/{unavailabilityId}
    // =========================================================================

    @Test
    @DisplayName("GET por ID exitoso devuelve 200 OK con detalle completo")
    void getUnavailability_exitoso_devuelve200() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        EmployeeUnavailability item = sampleUnavailability(unavailId, empId);
        when(getUnavailabilityUseCase.execute(orgId, unavailId)).thenReturn(item);

        mockMvc.perform(get("/api/v1/unavailabilities/" + unavailId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(unavailId.toString())))
                .andExpect(jsonPath("$.employeeId", is(empId.toString())))
                .andExpect(jsonPath("$.reasonCode", is("VACACIONES")))
                .andExpect(jsonPath("$.restrictedNote", is("Nota confidencial médica")))
                .andExpect(jsonPath("$.cancelled", is(false)));

        verify(getUnavailabilityUseCase).execute(orgId, unavailId);
    }

    @Test
    @DisplayName("GET por ID con UUID inválido devuelve 400 VALIDATION_FAILED")
    void getUnavailability_uuidInvalido_devuelve400() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");

        mockMvc.perform(get("/api/v1/unavailabilities/no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        verifyNoInteractions(getUnavailabilityUseCase);
    }

    @Test
    @DisplayName("GET por ID no encontrado devuelve 404 UNAVAILABILITY_NOT_FOUND")
    void getUnavailability_noExiste_devuelve404() throws Exception {
        setAuthenticatedUser("ROLE_SUPERVISOR");
        when(getUnavailabilityUseCase.execute(orgId, unavailId))
                .thenThrow(new UnavailabilityNotFoundException("Indisponibilidad no encontrada"));

        mockMvc.perform(get("/api/v1/unavailabilities/" + unavailId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("UNAVAILABILITY_NOT_FOUND")));
    }

    // =========================================================================
    // 5. Manejo de Fallas de Base de Datos (503)
    // =========================================================================

    @Test
    @DisplayName("Falla de acceso a datos devuelve 503 SERVICE_UNAVAILABLE sin filtrar detalles internos")
    void dataAccessException_devuelve503() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        when(getUnavailabilityUseCase.execute(orgId, unavailId))
                .thenThrow(new UnavailabilityDataAccessException("Error de conexión a SQL Server", null));

        mockMvc.perform(get("/api/v1/unavailabilities/" + unavailId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("SERVICE_UNAVAILABLE")))
                .andExpect(jsonPath("$.detail", is("El servicio no está disponible temporalmente. Intente más tarde.")));
    }

    // =========================================================================
    // 6. Mutaciones: POST create, PUT update, POST cancel y RN-013
    // =========================================================================

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: creación exitosa devuelve 201 y Location")
    void create_exito() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavailId, empId, Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"),
                "MEDICO", "Cita médica", false, null, null, userId, 0L, now, now
        );

        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(resp));

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z",
                  "reasonCode": "MEDICO",
                  "restrictedNote": "Cita médica"
                }
                """;

        mockMvc.perform(post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/unavailabilities/" + unavailId))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id", is(unavailId.toString())))
                .andExpect(jsonPath("$.reasonCode", is("MEDICO")));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: formato allDay exitoso resuelve zona horaria y devuelve 201")
    void create_allDay_exito() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavailId, empId, Instant.parse("2026-10-15T05:00:00Z"), Instant.parse("2026-10-16T05:00:00Z"),
                "VACACIONES", null, false, null, null, userId, 0L, now, now
        );

        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(resp));

        String payload = """
                {
                  "allDay": true,
                  "startDate": "2026-10-15",
                  "endDate": "2026-10-16",
                  "reasonCode": "VACACIONES"
                }
                """;

        mockMvc.perform(post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/unavailabilities/" + unavailId))
                .andExpect(jsonPath("$.id", is(unavailId.toString())));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: sin Idempotency-Key devuelve 400 VALIDATION_FAILED")
    void create_sinIdempotencyKey() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/employees/" + empId + "/unavailabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: Idempotency-Key inválido devuelve 400 VALIDATION_FAILED")
    void create_idempotencyKeyInvalido() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", "clave-invalida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: mezcla allDay con startsAt devuelve 400 VALIDATION_FAILED")
    void create_formatoInvalido_mezclaAllDayConStartsAt() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        String payload = """
                {
                  "allDay": true,
                  "startDate": "2026-10-15",
                  "endDate": "2026-10-16",
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: motivo no perteneciente al catálogo devuelve 400 VALIDATION_FAILED")
    void create_motivoInvalido() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z",
                  "reasonCode": "MOTIVO_INEXISTENTE"
                }
                """;

        mockMvc.perform(post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: replay idempotente devuelve 201 e Idempotency-Replayed: true")
    void create_replayIdempotente() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavailId, empId, Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"),
                null, null, false, null, null, userId, 0L, now, now
        );

        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Replay<>(resp));

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id", is(unavailId.toString())));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: clave reutilizada con distinto payload devuelve 409 IDEMPOTENCY_KEY_REUSED")
    void create_keyReusedDevuelve409() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.KeyReused<>());

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_KEY_REUSED")));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: clave expirada devuelve 409 IDEMPOTENCY_KEY_EXPIRED")
    void create_keyExpiredDevuelve409() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Expired<>());

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_KEY_EXPIRED")));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: colaborador inexistente devuelve 404 EMPLOYEE_NOT_FOUND")
    void create_colaboradorNoExiste_devuelve404() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        when(executor.execute(any(), any())).thenAnswer(inv -> {
            Supplier<?> effect = inv.getArgument(1);
            return effect.get();
        });

        when(createUnavailabilityUseCase.execute(eq(orgId), eq(empId), any(), any(), any(), any(), eq(userId)))
                .thenThrow(new EmployeeNotFoundException("Colaborador no encontrado"));

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("EMPLOYEE_NOT_FOUND")));
    }

    @Test
    @DisplayName("PUT /unavailabilities/{id}: actualización exitosa devuelve 200 OK")
    void update_exito() throws Exception {
        setAuthenticatedUser("ROLE_SUPERVISOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavailId, empId, Instant.parse("2026-10-16T14:00:00Z"), Instant.parse("2026-10-16T18:00:00Z"),
                "VACACIONES", null, false, null, null, userId, 1L, now, now
        );

        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(resp));

        String payload = """
                {
                  "startsAt": "2026-10-16T14:00:00Z",
                  "endsAt": "2026-10-16T18:00:00Z",
                  "reasonCode": "VACACIONES",
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(put("/api/v1/unavailabilities/" + unavailId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id", is(unavailId.toString())))
                .andExpect(jsonPath("$.lockVersion", is(1)));
    }

    @Test
    @DisplayName("PUT /unavailabilities/{id}: falta expectedLockVersion devuelve 400 VALIDATION_FAILED")
    void update_faltaLockVersion() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(put("/api/v1/unavailabilities/" + unavailId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("PUT /unavailabilities/{id}: expectedLockVersion negativo devuelve 400 VALIDATION_FAILED")
    void update_lockVersionNegativo() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z",
                  "expectedLockVersion": -1
                }
                """;

        mockMvc.perform(put("/api/v1/unavailabilities/" + unavailId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("PUT /unavailabilities/{id}: replay idempotente devuelve 200 e Idempotency-Replayed: true")
    void update_replayIdempotente() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavailId, empId, Instant.parse("2026-10-16T14:00:00Z"), Instant.parse("2026-10-16T18:00:00Z"),
                null, null, false, null, null, userId, 1L, now, now
        );

        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Replay<>(resp));

        String payload = """
                {
                  "startsAt": "2026-10-16T14:00:00Z",
                  "endsAt": "2026-10-16T18:00:00Z",
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(put("/api/v1/unavailabilities/" + unavailId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id", is(unavailId.toString())));
    }

    @Test
    @DisplayName("RN-013 Update: rechaza actualizar indisponibilidad original ya iniciada (startsAt <= now)")
    void update_indisponibilidadOriginalIniciada_responde400() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        when(executor.execute(any(), any()))
                .thenAnswer(inv -> {
                    Supplier<?> effect = inv.getArgument(1);
                    return effect.get();
                });

        when(updateUnavailabilityUseCase.execute(
                eq(orgId),
                eq(unavailId),
                any(),
                any(),
                any(),
                any(),
                eq(0L)
        )).thenThrow(new PastIntervalModificationProhibitedException("No se puede modificar una indisponibilidad que ya inició o finalizó"));

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z",
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(put("/api/v1/unavailabilities/" + unavailId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("PAST_INTERVAL_MODIFICATION_PROHIBITED")));
    }

    @Test
    @DisplayName("RN-013 Update: rechaza actualizar indisponibilidad futura si el nuevo startsAt ya inició o es pasado (newStartsAt <= now)")
    void update_indisponibilidadFuturaConNuevoStartsAtIniciadoOPasado_responde400() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        when(executor.execute(any(), any()))
                .thenAnswer(inv -> {
                    Supplier<?> effect = inv.getArgument(1);
                    return effect.get();
                });

        when(updateUnavailabilityUseCase.execute(
                eq(orgId),
                eq(unavailId),
                any(),
                any(),
                any(),
                any(),
                eq(0L)
        )).thenThrow(new PastIntervalModificationProhibitedException("El nuevo intervalo no puede haber iniciado ya ni estar en el pasado"));

        String payload = """
                {
                  "startsAt": "2026-09-20T10:00:00Z",
                  "endsAt": "2026-09-20T14:00:00Z",
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(put("/api/v1/unavailabilities/" + unavailId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("PAST_INTERVAL_MODIFICATION_PROHIBITED")));
    }

    @Test
    @DisplayName("PUT /unavailabilities/{id}: conflicto de concurrencia optimista devuelve 409 CONCURRENT_MODIFICATION")
    void update_concurrenciaOptimista_responde409() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        when(executor.execute(any(), any())).thenAnswer(inv -> {
            Supplier<?> effect = inv.getArgument(1);
            return effect.get();
        });

        when(updateUnavailabilityUseCase.execute(eq(orgId), eq(unavailId), any(), any(), any(), any(), eq(0L)))
                .thenThrow(new UnavailabilityConcurrentModificationException("Conflicto de concurrencia"));

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z",
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(put("/api/v1/unavailabilities/" + unavailId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("CONCURRENT_MODIFICATION")));
    }

    @Test
    @DisplayName("PUT /unavailabilities/{id}: indisponibilidad ya cancelada devuelve 409 UNAVAILABILITY_ALREADY_CANCELLED")
    void update_yaCancelada_responde409() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        when(executor.execute(any(), any())).thenAnswer(inv -> {
            Supplier<?> effect = inv.getArgument(1);
            return effect.get();
        });

        when(updateUnavailabilityUseCase.execute(eq(orgId), eq(unavailId), any(), any(), any(), any(), eq(0L)))
                .thenThrow(new UnavailabilityAlreadyCancelledException("Indisponibilidad cancelada"));

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z",
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(put("/api/v1/unavailabilities/" + unavailId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("UNAVAILABILITY_ALREADY_CANCELLED")));
    }

    @Test
    @DisplayName("PUT /unavailabilities/{id}: indisponibilidad inexistente devuelve 404 UNAVAILABILITY_NOT_FOUND")
    void update_noExiste_devuelve404() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        when(executor.execute(any(), any())).thenAnswer(inv -> {
            Supplier<?> effect = inv.getArgument(1);
            return effect.get();
        });

        when(updateUnavailabilityUseCase.execute(eq(orgId), eq(unavailId), any(), any(), any(), any(), eq(0L)))
                .thenThrow(new UnavailabilityNotFoundException("Indisponibilidad no encontrada"));

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z",
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(put("/api/v1/unavailabilities/" + unavailId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("UNAVAILABILITY_NOT_FOUND")));
    }

    @Test
    @DisplayName("POST /unavailabilities/{id}/cancel: cancelación exitosa responde 200 OK")
    void cancel_exito() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();

        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavailId, empId, Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"),
                null, null, true, now, userId, userId, 1L, now, now
        );

        when(executor.execute(any(), any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(resp));

        String payload = """
                {
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(post("/api/v1/unavailabilities/" + unavailId + "/cancel")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.cancelled", is(true)))
                .andExpect(jsonPath("$.lockVersion", is(1)));
    }

    @Test
    @DisplayName("POST /unavailabilities/{id}/cancel: falta expectedLockVersion devuelve 400 VALIDATION_FAILED")
    void cancel_faltaLockVersion() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();
        String payload = "{}";

        mockMvc.perform(post("/api/v1/unavailabilities/" + unavailId + "/cancel")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("RN-013 Cancel: rechaza cancelar indisponibilidad ya iniciada (startsAt <= now)")
    void cancel_indisponibilidadIniciada_responde400() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();

        when(executor.execute(any(), any()))
                .thenAnswer(inv -> {
                    Supplier<?> effect = inv.getArgument(1);
                    return effect.get();
                });

        when(cancelUnavailabilityUseCase.execute(orgId, unavailId, userId, 0L))
                .thenThrow(new PastIntervalModificationProhibitedException("No se puede cancelar una indisponibilidad que ya inició o finalizó"));

        String payload = """
                {
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(post("/api/v1/unavailabilities/" + unavailId + "/cancel")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("PAST_INTERVAL_MODIFICATION_PROHIBITED")));
    }

    @Test
    @DisplayName("POST /unavailabilities/{id}/cancel: conflicto de concurrencia responde 409 CONCURRENT_MODIFICATION")
    void cancel_concurrencyConflict() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();

        when(executor.execute(any(), any()))
                .thenAnswer(inv -> {
                    Supplier<?> effect = inv.getArgument(1);
                    return effect.get();
                });

        when(cancelUnavailabilityUseCase.execute(orgId, unavailId, userId, 0L))
                .thenThrow(new UnavailabilityConcurrentModificationException("Conflicto de versión"));

        String payload = """
                {
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(post("/api/v1/unavailabilities/" + unavailId + "/cancel")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("CONCURRENT_MODIFICATION")));
    }

    @Test
    @DisplayName("POST /unavailabilities/{id}/cancel: indisponibilidad ya cancelada responde 409 UNAVAILABILITY_ALREADY_CANCELLED")
    void cancel_alreadyCancelled() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();

        when(executor.execute(any(), any())).thenAnswer(inv -> {
            Supplier<?> effect = inv.getArgument(1);
            return effect.get();
        });

        when(cancelUnavailabilityUseCase.execute(orgId, unavailId, userId, 0L))
                .thenThrow(new UnavailabilityAlreadyCancelledException("Ya cancelada"));

        String payload = """
                {
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(post("/api/v1/unavailabilities/" + unavailId + "/cancel")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("UNAVAILABILITY_ALREADY_CANCELLED")));
    }

    @Test
    @DisplayName("Idempotency failsafe: error de base de datos en executor responde 503 SERVICE_UNAVAILABLE")
    void cancel_idempotencyFailsafe_devuelve503() throws Exception {
        setAuthenticatedUser("ROLE_ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();

        when(executor.execute(any(), any()))
                .thenThrow(new BusinessIdempotencyDataAccessException());

        String payload = """
                {
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(post("/api/v1/unavailabilities/" + unavailId + "/cancel")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code", is("SERVICE_UNAVAILABLE")));
    }
}
