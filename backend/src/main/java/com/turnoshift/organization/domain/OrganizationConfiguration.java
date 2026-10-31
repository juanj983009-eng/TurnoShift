package com.turnoshift.organization.domain;

import java.util.Objects;

/**
 * Agregado de lectura que compone la configuración de la organización y su sede única activa.
 */
public record OrganizationConfiguration(
        OrganizationView organization,
        ActiveSiteView activeSite
) {
    public OrganizationConfiguration {
        Objects.requireNonNull(organization, "organization no puede ser nulo");
        Objects.requireNonNull(activeSite, "activeSite no puede ser nulo");
    }
}
