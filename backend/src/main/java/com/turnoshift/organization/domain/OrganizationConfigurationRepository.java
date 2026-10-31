package com.turnoshift.organization.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de repositorio para la consulta y actualización de la configuración de organización y sede única.
 */
public interface OrganizationConfigurationRepository {

    /**
     * Consulta la configuración de una organización por su identificador único.
     */
    Optional<OrganizationView> findOrganizationById(UUID organizationId);

    /**
     * Consulta la sede única activa perteneciente a una organización.
     */
    Optional<ActiveSiteView> findActiveSiteByOrganizationId(UUID organizationId);

    /**
     * Actualiza atómicamente el nombre visible (displayName) de la organización verificando optimistic locking.
     * Incrementa {@code lock_version} y actualiza {@code updated_at}.
     *
     * @return Vista actualizada si la fila coincidió con {@code expectedLockVersion}, o {@link Optional#empty()} si no.
     */
    Optional<OrganizationView> updateOrganizationDisplayName(UUID organizationId, String newDisplayName, long expectedLockVersion);

    /**
     * Actualiza atómicamente el nombre (name) de la sede activa verificando optimistic locking.
     * Incrementa {@code lock_version} y actualiza {@code updated_at}.
     *
     * @return Vista actualizada si la fila coincidió con {@code expectedLockVersion}, o {@link Optional#empty()} si no.
     */
    Optional<ActiveSiteView> updateActiveSiteName(UUID organizationId, String newSiteName, long expectedLockVersion);
}
