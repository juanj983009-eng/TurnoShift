package com.turnoshift.organization.domain;

import java.util.UUID;

/**
 * Excepción lanzada cuando una organización objetivo no existe en el sistema.
 */
public final class OrganizationNotFoundException extends RuntimeException {

    private final UUID organizationId;

    public OrganizationNotFoundException(UUID organizationId) {
        super("Organización no encontrada: " + organizationId);
        this.organizationId = organizationId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }
}
