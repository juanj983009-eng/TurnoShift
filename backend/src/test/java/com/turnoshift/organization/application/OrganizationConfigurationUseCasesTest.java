package com.turnoshift.organization.application;

import com.turnoshift.organization.domain.ActiveSiteNotFoundException;
import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.InvalidOrganizationDataException;
import com.turnoshift.organization.domain.OrganizationConfiguration;
import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.organization.domain.OrganizationNotFoundException;
import com.turnoshift.organization.domain.OrganizationOptimisticLockException;
import com.turnoshift.organization.domain.OrganizationView;
import com.turnoshift.organization.domain.SiteOptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: Casos de Uso de Configuración de Organización y Sede Activa")
class OrganizationConfigurationUseCasesTest {

    @Mock
    private OrganizationConfigurationRepository repository;

    private GetOrganizationConfigurationUseCase getConfigurationUseCase;
    private UpdateOrganizationDisplayNameUseCase updateDisplayNameUseCase;
    private UpdateActiveSiteNameUseCase updateSiteNameUseCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        getConfigurationUseCase = new GetOrganizationConfigurationUseCase(repository);
        updateDisplayNameUseCase = new UpdateOrganizationDisplayNameUseCase(repository);
        updateSiteNameUseCase = new UpdateActiveSiteNameUseCase(repository);
    }

    // =========================================================================
    // 1. GetOrganizationConfigurationUseCase
    // =========================================================================

    @Nested
    @DisplayName("GetOrganizationConfigurationUseCase")
    class GetConfigurationTests {

        @Test
        @DisplayName("organizationId nulo lanza NullPointerException")
        void organizationIdNulo_lanzaExcepcion() {
            assertThatThrownBy(() -> getConfigurationUseCase.execute(null))
                    .isInstanceOf(NullPointerException.class);
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("Organización no existe lanza OrganizationNotFoundException")
        void organizacionNoExiste_lanzaOrganizationNotFoundException() {
            when(repository.findOrganizationById(orgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getConfigurationUseCase.execute(orgId))
                    .isInstanceOf(OrganizationNotFoundException.class)
                    .hasMessageContaining(orgId.toString());

            verify(repository).findOrganizationById(orgId);
        }

        @Test
        @DisplayName("Organización existe pero no tiene sede activa lanza ActiveSiteNotFoundException")
        void sedeActivaNoExiste_lanzaActiveSiteNotFoundException() {
            OrganizationView orgView = new OrganizationView(orgId, "TurnoShift Corp", "America/Bogota", 0L);
            when(repository.findOrganizationById(orgId)).thenReturn(Optional.of(orgView));
            when(repository.findActiveSiteByOrganizationId(orgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getConfigurationUseCase.execute(orgId))
                    .isInstanceOf(ActiveSiteNotFoundException.class)
                    .hasMessageContaining(orgId.toString());

            verify(repository).findOrganizationById(orgId);
            verify(repository).findActiveSiteByOrganizationId(orgId);
        }

        @Test
        @DisplayName("Organización y sede activa existen devuelve OrganizationConfiguration compuesto")
        void configuracionCompleta_devuelveOrganizationConfiguration() {
            OrganizationView orgView = new OrganizationView(orgId, "TurnoShift Corp", "America/Bogota", 3L);
            ActiveSiteView siteView = new ActiveSiteView(siteId, "SEDE-PRINCIPAL", "Sede Central", 1L);

            when(repository.findOrganizationById(orgId)).thenReturn(Optional.of(orgView));
            when(repository.findActiveSiteByOrganizationId(orgId)).thenReturn(Optional.of(siteView));

            OrganizationConfiguration config = getConfigurationUseCase.execute(orgId);

            assertThat(config).isNotNull();
            assertThat(config.organization()).isEqualTo(orgView);
            assertThat(config.activeSite()).isEqualTo(siteView);
            assertThat(config.organization().timezoneId()).isEqualTo("America/Bogota");

            verify(repository).findOrganizationById(orgId);
            verify(repository).findActiveSiteByOrganizationId(orgId);
        }
    }

    // =========================================================================
    // 2. UpdateOrganizationDisplayNameUseCase
    // =========================================================================

    @Nested
    @DisplayName("UpdateOrganizationDisplayNameUseCase")
    class UpdateDisplayNameTests {

        @Test
        @DisplayName("organizationId nulo lanza NullPointerException")
        void organizationIdNulo_lanzaExcepcion() {
            assertThatThrownBy(() -> updateDisplayNameUseCase.execute(null, "Nuevo Nombre", 0L))
                    .isInstanceOf(NullPointerException.class);
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("displayName nulo, vacío o solo espacios lanza InvalidOrganizationDataException")
        void displayNameInvalido_lanzaInvalidOrganizationDataException() {
            assertThatThrownBy(() -> updateDisplayNameUseCase.execute(orgId, null, 0L))
                    .isInstanceOf(InvalidOrganizationDataException.class);

            assertThatThrownBy(() -> updateDisplayNameUseCase.execute(orgId, "", 0L))
                    .isInstanceOf(InvalidOrganizationDataException.class);

            assertThatThrownBy(() -> updateDisplayNameUseCase.execute(orgId, "   ", 0L))
                    .isInstanceOf(InvalidOrganizationDataException.class);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("displayName mayor a 150 caracteres lanza InvalidOrganizationDataException")
        void displayNameMayorA150_lanzaInvalidOrganizationDataException() {
            String largo = "A".repeat(151);
            assertThatThrownBy(() -> updateDisplayNameUseCase.execute(orgId, largo, 0L))
                    .isInstanceOf(InvalidOrganizationDataException.class);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("expectedLockVersion negativo lanza InvalidOrganizationDataException")
        void lockVersionNegativo_lanzaInvalidOrganizationDataException() {
            assertThatThrownBy(() -> updateDisplayNameUseCase.execute(orgId, "Nombre Válido", -1L))
                    .isInstanceOf(InvalidOrganizationDataException.class);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("Organización no existe lanza OrganizationNotFoundException")
        void organizacionNoExiste_lanzaOrganizationNotFoundException() {
            when(repository.findOrganizationById(orgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> updateDisplayNameUseCase.execute(orgId, "Nuevo Nombre", 0L))
                    .isInstanceOf(OrganizationNotFoundException.class);

            verify(repository).findOrganizationById(orgId);
        }

        @Test
        @DisplayName("Versión esperada no coincide lanza OrganizationOptimisticLockException")
        void versionNoCoincide_lanzaOrganizationOptimisticLockException() {
            OrganizationView actual = new OrganizationView(orgId, "Nombre Actual", "America/Bogota", 2L);
            when(repository.findOrganizationById(orgId)).thenReturn(Optional.of(actual));

            assertThatThrownBy(() -> updateDisplayNameUseCase.execute(orgId, "Nuevo Nombre", 1L))
                    .isInstanceOf(OrganizationOptimisticLockException.class)
                    .satisfies(e -> {
                        OrganizationOptimisticLockException ole = (OrganizationOptimisticLockException) e;
                        assertThat(ole.getOrganizationId()).isEqualTo(orgId);
                        assertThat(ole.getExpectedLockVersion()).isEqualTo(1L);
                    });

            verify(repository).findOrganizationById(orgId);
        }

        @Test
        @DisplayName("Actualización concurrente falla atómicamente lanza OrganizationOptimisticLockException")
        void actualizacionConcurrente_lanzaOrganizationOptimisticLockException() {
            OrganizationView actual = new OrganizationView(orgId, "Nombre Actual", "America/Bogota", 1L);
            when(repository.findOrganizationById(orgId)).thenReturn(Optional.of(actual));
            when(repository.updateOrganizationDisplayName(orgId, "Nombre Limpio", 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> updateDisplayNameUseCase.execute(orgId, "  Nombre Limpio  ", 1L))
                    .isInstanceOf(OrganizationOptimisticLockException.class);

            verify(repository).findOrganizationById(orgId);
            verify(repository).updateOrganizationDisplayName(orgId, "Nombre Limpio", 1L);
        }

        @Test
        @DisplayName("Actualización exitosa aplica trim, incrementa versión y retorna vista actualizada")
        void actualizacionExitosa_aplicaTrimYRetornaActualizada() {
            OrganizationView actual = new OrganizationView(orgId, "Nombre Antiguo", "America/Bogota", 1L);
            OrganizationView actualizada = new OrganizationView(orgId, "Nombre Nuevo", "America/Bogota", 2L);

            when(repository.findOrganizationById(orgId)).thenReturn(Optional.of(actual));
            when(repository.updateOrganizationDisplayName(orgId, "Nombre Nuevo", 1L))
                    .thenReturn(Optional.of(actualizada));

            OrganizationView resultado = updateDisplayNameUseCase.execute(orgId, "   Nombre Nuevo   ", 1L);

            assertThat(resultado).isEqualTo(actualizada);
            assertThat(resultado.displayName()).isEqualTo("Nombre Nuevo");
            assertThat(resultado.lockVersion()).isEqualTo(2L);
            assertThat(resultado.timezoneId()).isEqualTo("America/Bogota");

            verify(repository).findOrganizationById(orgId);
            verify(repository).updateOrganizationDisplayName(orgId, "Nombre Nuevo", 1L);
        }
    }

    // =========================================================================
    // 3. UpdateActiveSiteNameUseCase
    // =========================================================================

    @Nested
    @DisplayName("UpdateActiveSiteNameUseCase")
    class UpdateActiveSiteNameTests {

        @Test
        @DisplayName("organizationId nulo lanza NullPointerException")
        void organizationIdNulo_lanzaExcepcion() {
            assertThatThrownBy(() -> updateSiteNameUseCase.execute(null, "Nueva Sede", 0L))
                    .isInstanceOf(NullPointerException.class);
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("name nulo, vacío o solo espacios lanza InvalidOrganizationDataException")
        void nameInvalido_lanzaInvalidOrganizationDataException() {
            assertThatThrownBy(() -> updateSiteNameUseCase.execute(orgId, null, 0L))
                    .isInstanceOf(InvalidOrganizationDataException.class);

            assertThatThrownBy(() -> updateSiteNameUseCase.execute(orgId, "", 0L))
                    .isInstanceOf(InvalidOrganizationDataException.class);

            assertThatThrownBy(() -> updateSiteNameUseCase.execute(orgId, "   ", 0L))
                    .isInstanceOf(InvalidOrganizationDataException.class);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("name mayor a 150 caracteres lanza InvalidOrganizationDataException")
        void nameMayorA150_lanzaInvalidOrganizationDataException() {
            String largo = "B".repeat(151);
            assertThatThrownBy(() -> updateSiteNameUseCase.execute(orgId, largo, 0L))
                    .isInstanceOf(InvalidOrganizationDataException.class);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("expectedLockVersion negativo lanza InvalidOrganizationDataException")
        void lockVersionNegativo_lanzaInvalidOrganizationDataException() {
            assertThatThrownBy(() -> updateSiteNameUseCase.execute(orgId, "Sede Válida", -1L))
                    .isInstanceOf(InvalidOrganizationDataException.class);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("Sede activa no existe lanza ActiveSiteNotFoundException")
        void sedeActivaNoExiste_lanzaActiveSiteNotFoundException() {
            when(repository.findActiveSiteByOrganizationId(orgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> updateSiteNameUseCase.execute(orgId, "Nueva Sede", 0L))
                    .isInstanceOf(ActiveSiteNotFoundException.class);

            verify(repository).findActiveSiteByOrganizationId(orgId);
        }

        @Test
        @DisplayName("Versión esperada de sede no coincide lanza SiteOptimisticLockException")
        void versionNoCoincide_lanzaSiteOptimisticLockException() {
            ActiveSiteView actual = new ActiveSiteView(siteId, "SEDE-01", "Nombre Actual", 4L);
            when(repository.findActiveSiteByOrganizationId(orgId)).thenReturn(Optional.of(actual));

            assertThatThrownBy(() -> updateSiteNameUseCase.execute(orgId, "Nueva Sede", 3L))
                    .isInstanceOf(SiteOptimisticLockException.class)
                    .satisfies(e -> {
                        SiteOptimisticLockException sole = (SiteOptimisticLockException) e;
                        assertThat(sole.getSiteId()).isEqualTo(siteId);
                        assertThat(sole.getExpectedLockVersion()).isEqualTo(3L);
                    });

            verify(repository).findActiveSiteByOrganizationId(orgId);
        }

        @Test
        @DisplayName("Actualización concurrente de sede falla atómicamente lanza SiteOptimisticLockException")
        void actualizacionConcurrente_lanzaSiteOptimisticLockException() {
            ActiveSiteView actual = new ActiveSiteView(siteId, "SEDE-01", "Nombre Actual", 2L);
            when(repository.findActiveSiteByOrganizationId(orgId)).thenReturn(Optional.of(actual));
            when(repository.updateActiveSiteName(orgId, "Sede Limpia", 2L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> updateSiteNameUseCase.execute(orgId, "  Sede Limpia  ", 2L))
                    .isInstanceOf(SiteOptimisticLockException.class);

            verify(repository).findActiveSiteByOrganizationId(orgId);
            verify(repository).updateActiveSiteName(orgId, "Sede Limpia", 2L);
        }

        @Test
        @DisplayName("Actualización exitosa de sede aplica trim, incrementa versión y retorna vista actualizada")
        void actualizacionExitosa_aplicaTrimYRetornaActualizada() {
            ActiveSiteView actual = new ActiveSiteView(siteId, "SEDE-01", "Nombre Viejo", 0L);
            ActiveSiteView actualizada = new ActiveSiteView(siteId, "SEDE-01", "Nombre Renovado", 1L);

            when(repository.findActiveSiteByOrganizationId(orgId)).thenReturn(Optional.of(actual));
            when(repository.updateActiveSiteName(orgId, "Nombre Renovado", 0L))
                    .thenReturn(Optional.of(actualizada));

            ActiveSiteView resultado = updateSiteNameUseCase.execute(orgId, "   Nombre Renovado   ", 0L);

            assertThat(resultado).isEqualTo(actualizada);
            assertThat(resultado.name()).isEqualTo("Nombre Renovado");
            assertThat(resultado.code()).isEqualTo("SEDE-01");
            assertThat(resultado.lockVersion()).isEqualTo(1L);

            verify(repository).findActiveSiteByOrganizationId(orgId);
            verify(repository).updateActiveSiteName(orgId, "Nombre Renovado", 0L);
        }
    }
}
