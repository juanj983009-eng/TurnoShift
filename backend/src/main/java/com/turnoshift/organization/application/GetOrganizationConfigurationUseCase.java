package com.turnoshift.organization.application;

import com.turnoshift.organization.domain.ActiveSiteNotFoundException;
import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.OrganizationConfiguration;
import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.organization.domain.OrganizationNotFoundException;
import com.turnoshift.organization.domain.OrganizationView;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para consultar la configuración de una organización y su sede única activa.
 * <p>
 * <b>Aislamiento Multitenant:</b>
 * La consulta utiliza estrictamente el {@code organizationId} provisto por la capa superior
 * (derivado de la sesión autenticada) y nunca mezcla datos entre diferentes organizaciones.
 * <p>
 * <b>Limitación de Zona Horaria:</b>
 * La zona horaria ({@code timezoneId}) se expone en la respuesta de lectura para conocimiento
 * del cliente, pero en esta versión (I2.1) deliberadamente no se permite su modificación,
 * dado que las reglas de cambio de huso horario requieren el modelo de turnos de I3.
 */
@Service
public class GetOrganizationConfigurationUseCase {

    private final OrganizationConfigurationRepository repository;

    public GetOrganizationConfigurationUseCase(OrganizationConfigurationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
    }

    public OrganizationConfiguration execute(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");

        OrganizationView organization = repository.findOrganizationById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));

        ActiveSiteView activeSite = repository.findActiveSiteByOrganizationId(organizationId)
                .orElseThrow(() -> new ActiveSiteNotFoundException(organizationId));

        return new OrganizationConfiguration(organization, activeSite);
    }
}
