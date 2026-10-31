package com.turnoshift.organization.domain;

import java.util.UUID;

/**
 * Excepción lanzada cuando una organización no posee ninguna sede activa configurada.
 */
public final class ActiveSiteNotFoundException extends RuntimeException {

    private final UUID organizationId;

    public ActiveSiteNotFoundException(UUID organizationId) {
        super("No se encontró sede activa para la organización: " + organizationId);
        this.organizationId = organizationId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }
}
