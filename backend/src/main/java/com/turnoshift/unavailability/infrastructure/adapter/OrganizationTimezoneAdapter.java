package com.turnoshift.unavailability.infrastructure.adapter;

import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.organization.domain.OrganizationView;
import com.turnoshift.unavailability.domain.OrganizationTimezonePort;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador que resuelve el huso horario oficial de una organización delegando
 * en el repositorio de configuración de organizaciones.
 */
public class OrganizationTimezoneAdapter implements OrganizationTimezonePort {

    private final OrganizationConfigurationRepository organizationConfigurationRepository;

    public OrganizationTimezoneAdapter(OrganizationConfigurationRepository organizationConfigurationRepository) {
        this.organizationConfigurationRepository = Objects.requireNonNull(
                organizationConfigurationRepository, "organizationConfigurationRepository no puede ser nulo");
    }

    @Override
    public ZoneId resolveZoneId(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");

        OrganizationView org = organizationConfigurationRepository.findOrganizationById(organizationId)
                .orElseThrow(() -> new IllegalStateException("Organización no encontrada: " + organizationId));

        try {
            return ZoneId.of(org.timezoneId());
        } catch (DateTimeException e) {
            throw new IllegalStateException("Zona horaria no válida en la organización: " + org.timezoneId(), e);
        }
    }
}
