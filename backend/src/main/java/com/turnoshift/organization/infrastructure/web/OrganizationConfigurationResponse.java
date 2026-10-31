package com.turnoshift.organization.infrastructure.web;

import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.OrganizationConfiguration;
import com.turnoshift.organization.domain.OrganizationView;

import java.util.Objects;

/**
 * DTO de respuesta para la consulta de configuración de organización y sede única.
 */
public record OrganizationConfigurationResponse(
        OrganizationView organization,
        ActiveSiteView activeSite
) {
    public OrganizationConfigurationResponse {
        Objects.requireNonNull(organization, "organization no puede ser nulo");
        Objects.requireNonNull(activeSite, "activeSite no puede ser nulo");
    }

    public static OrganizationConfigurationResponse from(OrganizationConfiguration config) {
        Objects.requireNonNull(config, "config no puede ser nulo");
        return new OrganizationConfigurationResponse(config.organization(), config.activeSite());
    }
}
