package com.turnoshift.organization.domain;

import java.util.UUID;

/**
 * Excepción lanzada cuando ocurre un conflicto de concurrencia optimista al actualizar
 * la configuración de una organización.
 */
public final class OrganizationOptimisticLockException extends RuntimeException {

    private final UUID organizationId;
    private final long expectedLockVersion;

    public OrganizationOptimisticLockException(UUID organizationId, long expectedLockVersion) {
        super("Conflicto de concurrencia optimista en organización: " + organizationId + ", versión esperada: " + expectedLockVersion);
        this.organizationId = organizationId;
        this.expectedLockVersion = expectedLockVersion;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public long getExpectedLockVersion() {
        return expectedLockVersion;
    }
}
