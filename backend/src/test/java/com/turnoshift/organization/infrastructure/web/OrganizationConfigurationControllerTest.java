package com.turnoshift.organization.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.organization.application.GetOrganizationConfigurationUseCase;
import com.turnoshift.organization.application.UpdateActiveSiteNameUseCase;
import com.turnoshift.organization.application.UpdateOrganizationDisplayNameUseCase;
import com.turnoshift.organization.domain.ActiveSiteNotFoundException;
import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.OrganizationConfiguration;
import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.organization.domain.OrganizationNotFoundException;
import com.turnoshift.organization.domain.OrganizationOptimisticLockException;
import com.turnoshift.organization.domain.OrganizationView;
import com.turnoshift.organization.domain.SiteOptimisticLockException;
import com.turnoshift.organization.infrastructure.OrganizationConfigurationDataAccessException;
import com.turnoshift.security.idempotency.BusinessIdempotencyDataAccessException;
import com.turnoshift.security.idempotency.BusinessIdempotencyResult;
import com.turnoshift.security.idempotency.BusinessIdempotentOperationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Unit: OrganizationConfigurationController — API HTTP Segura e Idempotente")
class OrganizationConfigurationControllerTest {

    private MockMvc mockMvc;

    private GetOrganizationConfigurationUseCase getConfigurationUseCase;
    private UpdateOrganizationDisplayNameUseCase updateDisplayNameUseCase;
    private UpdateActiveSiteNameUseCase updateSiteNameUseCase;
    private BusinessIdempotentOperationExecutor executor;
    private ObjectMapper objectMapper;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        getConfigurationUseCase = mock(GetOrganizationConfigurationUseCase.class);
        updateDisplayNameUseCase = mock(UpdateOrganizationDisplayNameUseCase.class);
        updateSiteNameUseCase = mock(UpdateActiveSiteNameUseCase.class);
        executor = mock(BusinessIdempotentOperationExecutor.class);
        objectMapper = new ObjectMapper();

        OrganizationConfigurationController controller = new OrganizationConfigurationController(
                getConfigurationUseCase,
                updateDisplayNameUseCase,
                updateSiteNameUseCase,
                executor,
                objectMapper
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        setAuthenticatedUser("ADMINISTRADOR");
    }

    private void setAuthenticatedUser(String role) {
        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                userId,
                orgId,
                "admin@empresa.com",
                "Admin General",
                role,
                null,
                1L,
                "Empresa Original",
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
    // 1. GET /api/v1/organization/configuration
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/organization/configuration")
    class GetConfigurationTests {

        @Test
        @DisplayName("Sin sesión: devuelve 401 SESSION_REQUIRED")
        void sinSesion_devuelve401() throws Exception {
            SecurityContextHolder.clearContext();

            mockMvc.perform(get("/api/v1/organization/configuration"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));

            verifyNoInteractions(getConfigurationUseCase);
        }

        @Test
        @DisplayName("Consulta exitosa: deriva tenant exclusivamente de la sesión y responde 200 con configuración completa")
        void consultaExitosa_derivaTenantDeSesion() throws Exception {
            OrganizationView orgView = new OrganizationView(orgId, "Empresa Alfa", "America/Bogota", 2L);
            ActiveSiteView siteView = new ActiveSiteView(siteId, "SEDE-ALFA", "Sede Alfa Central", 1L);
            OrganizationConfiguration config = new OrganizationConfiguration(orgView, siteView);

            when(getConfigurationUseCase.execute(orgId)).thenReturn(config);

            mockMvc.perform(get("/api/v1/organization/configuration"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.organization.id").value(orgId.toString()))
                    .andExpect(jsonPath("$.organization.displayName").value("Empresa Alfa"))
                    .andExpect(jsonPath("$.organization.timezoneId").value("America/Bogota"))
                    .andExpect(jsonPath("$.organization.lockVersion").value(2))
                    .andExpect(jsonPath("$.activeSite.id").value(siteId.toString()))
                    .andExpect(jsonPath("$.activeSite.code").value("SEDE-ALFA"))
                    .andExpect(jsonPath("$.activeSite.name").value("Sede Alfa Central"))
                    .andExpect(jsonPath("$.activeSite.lockVersion").value(1))
                    // Comprobar que NO expone campos restringidos
                    .andExpect(jsonPath("$.service_status").doesNotExist())
                    .andExpect(jsonPath("$.max_active_employees").doesNotExist())
                    .andExpect(jsonPath("$.current_policy_version_id").doesNotExist());

            verify(getConfigurationUseCase).execute(orgId);
        }

        @Test
        @DisplayName("Organización no encontrada: devuelve 404 RESOURCE_NOT_FOUND y detalle seguro sin UUID")
        void organizacionNoEncontrada_devuelve404() throws Exception {
            when(getConfigurationUseCase.execute(orgId)).thenThrow(new OrganizationNotFoundException(orgId));

            var result = mockMvc.perform(get("/api/v1/organization/configuration"))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("El recurso solicitado no existe o no pertenece a la organización."))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).doesNotContain(orgId.toString());
        }

        @Test
        @DisplayName("Sede activa no encontrada en GET: devuelve 404 RESOURCE_NOT_FOUND y detalle seguro sin UUID")
        void sedeActivaNoEncontradaGet_devuelve404() throws Exception {
            when(getConfigurationUseCase.execute(orgId)).thenThrow(new ActiveSiteNotFoundException(orgId));

            var result = mockMvc.perform(get("/api/v1/organization/configuration"))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("El recurso solicitado no existe o no pertenece a la organización."))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).doesNotContain(orgId.toString());
        }
    }

    // =========================================================================
    // 2. PATCH /api/v1/organization/configuration/display-name
    // =========================================================================

    @Nested
    @DisplayName("PATCH /api/v1/organization/configuration/display-name")
    class PatchDisplayNameTests {

        @Test
        @DisplayName("Sin sesión: devuelve 401 SESSION_REQUIRED")
        void sinSesion_devuelve401() throws Exception {
            SecurityContextHolder.clearContext();

            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));

            verifyNoInteractions(executor);
        }

        @Test
        @DisplayName("Sin Idempotency-Key: responde 400 VALIDATION_FAILED")
        void sinIdempotencyKey_responde400() throws Exception {
            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(executor);
        }

        @Test
        @DisplayName("Idempotency-Key no UUID: responde 400 VALIDATION_FAILED")
        void idempotencyKeyInvalida_responde400() throws Exception {
            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", "not-a-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(executor);
        }

        @Test
        @DisplayName("Cuerpo vacío o nulo: responde 400 VALIDATION_FAILED")
        void cuerpoVacio_responde400() throws Exception {
            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(executor);
        }

        @Test
        @DisplayName("JSON malformado: responde 400 VALIDATION_FAILED")
        void jsonMalformado_responde400() throws Exception {
            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{displayName: broken"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(executor);
        }

        @Test
        @DisplayName("expectedLockVersion nulo o negativo: responde 400 VALIDATION_FAILED")
        void lockVersionNegativoONulo_responde400() throws Exception {
            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":-1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Nuevo Nombre\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(executor);
        }

        @Test
        @DisplayName("displayName vacío o > 150 caracteres: responde 400 VALIDATION_FAILED")
        void displayNameInvalido_responde400() throws Exception {
            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"   \",\"expectedLockVersion\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            String largo = "X".repeat(151);
            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"" + largo + "\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            verifyNoInteractions(executor);
        }

        @Test
        @DisplayName("Actualización exitosa: ejecuta vía ejecutor y devuelve 200 con vista actualizada")
        void actualizacionExitosa_devuelve200() throws Exception {
            UUID key = UUID.randomUUID();
            OrganizationView updated = new OrganizationView(orgId, "Empresa Renovada", "America/Bogota", 1L);

            when(executor.execute(any(), any())).thenAnswer(inv -> {
                var effect = inv.getArgument(1, java.util.function.Supplier.class);
                var execRes = effect.get();
                return new BusinessIdempotencyResult.Proceed<>(execRes);
            });
            when(updateDisplayNameUseCase.execute(eq(orgId), eq("Empresa Renovada"), eq(0L)))
                    .thenReturn(updated);

            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"  Empresa Renovada  \",\"expectedLockVersion\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().doesNotExist("Idempotency-Replayed"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(orgId.toString()))
                    .andExpect(jsonPath("$.displayName").value("Empresa Renovada"))
                    .andExpect(jsonPath("$.lockVersion").value(1));

            verify(updateDisplayNameUseCase).execute(orgId, "Empresa Renovada", 0L);
        }

        @Test
        @DisplayName("Replay de actualización exitosa: devuelve 200 con header Idempotency-Replayed: true")
        void replayExitoso_devuelve200ConHeader() throws Exception {
            UUID key = UUID.randomUUID();
            OrganizationView updated = new OrganizationView(orgId, "Empresa Renovada", "America/Bogota", 1L);

            when(executor.execute(any(), any()))
                    .thenReturn(new BusinessIdempotencyResult.Replay<>(new OrganizationExecutionResult.OrganizationSuccess(updated)));

            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Empresa Renovada\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Idempotency-Replayed", "true"))
                    .andExpect(jsonPath("$.displayName").value("Empresa Renovada"))
                    .andExpect(jsonPath("$.lockVersion").value(1));
        }

        @Test
        @DisplayName("Conflicto de versión (stale version): devuelve 409 CONCURRENT_MODIFICATION")
        void conflictoDeVersion_devuelve409() throws Exception {
            UUID key = UUID.randomUUID();

            when(executor.execute(any(), any())).thenAnswer(inv -> {
                var effect = inv.getArgument(1, java.util.function.Supplier.class);
                var execRes = effect.get();
                return new BusinessIdempotencyResult.Proceed<>(execRes);
            });
            when(updateDisplayNameUseCase.execute(any(), any(), eq(0L)))
                    .thenThrow(new OrganizationOptimisticLockException(orgId, 0L));

            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Empresa Renovada\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty())
                    .andExpect(jsonPath("$.instance").value("/api/v1/organization/configuration/display-name"));
        }

        @Test
        @DisplayName("Replay de rechazo determinista: devuelve 409 con nuevo requestId y cabecera Idempotency-Replayed")
        void replayRechazo_devuelve409ConNuevoRequestId() throws Exception {
            UUID key = UUID.randomUUID();

            when(executor.execute(any(), any()))
                    .thenReturn(new BusinessIdempotencyResult.Replay<>(new OrganizationExecutionResult.ConcurrentModification()));

            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Empresa Renovada\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isConflict())
                    .andExpect(header().string("Idempotency-Replayed", "true"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }

        @Test
        @DisplayName("Clave reutilizada con payload diferente: devuelve 409 IDEMPOTENCY_KEY_REUSED")
        void claveReutilizada_devuelve409() throws Exception {
            UUID key = UUID.randomUUID();

            when(executor.execute(any(), any()))
                    .thenReturn(new BusinessIdempotencyResult.KeyReused<>());

            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        }

        @Test
        @DisplayName("Fallo de persistencia: responde 503 SERVICE_UNAVAILABLE")
        void falloPersistencia_responde503() throws Exception {
            UUID key = UUID.randomUUID();

            when(executor.execute(any(), any()))
                    .thenThrow(new OrganizationConfigurationDataAccessException());

            mockMvc.perform(patch("/api/v1/organization/configuration/display-name")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Nuevo Nombre\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }
    }

    // =========================================================================
    // 3. PATCH /api/v1/organization/configuration/active-site/name
    // =========================================================================

    @Nested
    @DisplayName("PATCH /api/v1/organization/configuration/active-site/name")
    class PatchActiveSiteNameTests {

        @Test
        @DisplayName("Actualización exitosa de sede: devuelve 200 con vista actualizada")
        void actualizacionExitosaSede_devuelve200() throws Exception {
            UUID key = UUID.randomUUID();
            ActiveSiteView updated = new ActiveSiteView(siteId, "SEDE-01", "Sede Renovada", 1L);

            when(executor.execute(any(), any())).thenAnswer(inv -> {
                var effect = inv.getArgument(1, java.util.function.Supplier.class);
                var execRes = effect.get();
                return new BusinessIdempotencyResult.Proceed<>(execRes);
            });
            when(updateSiteNameUseCase.execute(eq(orgId), eq("Sede Renovada"), eq(0L)))
                    .thenReturn(updated);

            mockMvc.perform(patch("/api/v1/organization/configuration/active-site/name")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"  Sede Renovada  \",\"expectedLockVersion\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().doesNotExist("Idempotency-Replayed"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(siteId.toString()))
                    .andExpect(jsonPath("$.code").value("SEDE-01"))
                    .andExpect(jsonPath("$.name").value("Sede Renovada"))
                    .andExpect(jsonPath("$.lockVersion").value(1));

            verify(updateSiteNameUseCase).execute(orgId, "Sede Renovada", 0L);
        }

        @Test
        @DisplayName("Replay de actualización exitosa de sede: devuelve 200 con Idempotency-Replayed: true")
        void replayExitosoSede_devuelve200ConHeader() throws Exception {
            UUID key = UUID.randomUUID();
            ActiveSiteView updated = new ActiveSiteView(siteId, "SEDE-01", "Sede Renovada", 1L);

            when(executor.execute(any(), any()))
                    .thenReturn(new BusinessIdempotencyResult.Replay<>(new OrganizationExecutionResult.SiteSuccess(updated)));

            mockMvc.perform(patch("/api/v1/organization/configuration/active-site/name")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Sede Renovada\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Idempotency-Replayed", "true"))
                    .andExpect(jsonPath("$.name").value("Sede Renovada"))
                    .andExpect(jsonPath("$.lockVersion").value(1));
        }

        @Test
        @DisplayName("Sede activa no encontrada: devuelve 404 RESOURCE_NOT_FOUND")
        void sedeActivaNoEncontrada_devuelve404() throws Exception {
            UUID key = UUID.randomUUID();

            when(executor.execute(any(), any())).thenAnswer(inv -> {
                var effect = inv.getArgument(1, java.util.function.Supplier.class);
                var execRes = effect.get();
                return new BusinessIdempotencyResult.Proceed<>(execRes);
            });
            when(updateSiteNameUseCase.execute(any(), any(), eq(0L)))
                    .thenThrow(new ActiveSiteNotFoundException(orgId));

            var result = mockMvc.perform(patch("/api/v1/organization/configuration/active-site/name")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Sede Renovada\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("El recurso solicitado no existe o no pertenece a la organización."))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).doesNotContain(orgId.toString());
        }

        @Test
        @DisplayName("Conflicto de versión en sede (stale version): devuelve 409 CONCURRENT_MODIFICATION")
        void conflictoDeVersionSede_devuelve409() throws Exception {
            UUID key = UUID.randomUUID();

            when(executor.execute(any(), any())).thenAnswer(inv -> {
                var effect = inv.getArgument(1, java.util.function.Supplier.class);
                var execRes = effect.get();
                return new BusinessIdempotencyResult.Proceed<>(execRes);
            });
            when(updateSiteNameUseCase.execute(any(), any(), eq(0L)))
                    .thenThrow(new SiteOptimisticLockException(siteId, 0L));

            mockMvc.perform(patch("/api/v1/organization/configuration/active-site/name")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Sede Renovada\",\"expectedLockVersion\":0}"))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        }
    }
}
