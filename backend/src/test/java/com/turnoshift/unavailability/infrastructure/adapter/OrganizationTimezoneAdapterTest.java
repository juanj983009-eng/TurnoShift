package com.turnoshift.unavailability.infrastructure.adapter;

import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.organization.domain.OrganizationView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: OrganizationTimezoneAdapter — Resolución de Zona Horaria")
class OrganizationTimezoneAdapterTest {

    @Mock
    private OrganizationConfigurationRepository repository;

    @Test
    @DisplayName("Resuelve ZoneId correctamente a partir de la configuración de la organización")
    void resuelveZoneIdValido() {
        UUID orgId = UUID.randomUUID();
        when(repository.findOrganizationById(orgId)).thenReturn(Optional.of(
                new OrganizationView(orgId, "Empresa Test", "America/Bogota", 0L)
        ));

        OrganizationTimezoneAdapter adapter = new OrganizationTimezoneAdapter(repository);
        ZoneId zoneId = adapter.resolveZoneId(orgId);

        assertThat(zoneId).isEqualTo(ZoneId.of("America/Bogota"));
    }

    @Test
    @DisplayName("Lanza excepción si la organización no existe")
    void lanzaExcepcionSiOrganizacionNoExiste() {
        UUID orgId = UUID.randomUUID();
        when(repository.findOrganizationById(orgId)).thenReturn(Optional.empty());

        OrganizationTimezoneAdapter adapter = new OrganizationTimezoneAdapter(repository);

        assertThatThrownBy(() -> adapter.resolveZoneId(orgId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Organización no encontrada");
    }

    @Test
    @DisplayName("Lanza excepción si el timezone_id configurado es inválido")
    void lanzaExcepcionSiTimezoneInvalido() {
        UUID orgId = UUID.randomUUID();
        when(repository.findOrganizationById(orgId)).thenReturn(Optional.of(
                new OrganizationView(orgId, "Empresa Test", "Zona/Invalida_123", 0L)
        ));

        OrganizationTimezoneAdapter adapter = new OrganizationTimezoneAdapter(repository);

        assertThatThrownBy(() -> adapter.resolveZoneId(orgId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Zona horaria no válida");
    }

    @Test
    @DisplayName("Lanza NullPointerException si el organizationId es nulo")
    void lanzaExcepcionSiOrganizationIdEsNulo() {
        OrganizationTimezoneAdapter adapter = new OrganizationTimezoneAdapter(repository);

        assertThatThrownBy(() -> adapter.resolveZoneId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("organizationId no puede ser nulo");
    }
}
