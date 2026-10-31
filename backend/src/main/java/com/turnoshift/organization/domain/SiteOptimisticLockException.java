package com.turnoshift.organization.domain;

import java.util.UUID;

/**
 * Excepción lanzada cuando ocurre un conflicto de concurrencia optimista al actualizar
 * la configuración de una sede.
 */
public final class SiteOptimisticLockException extends RuntimeException {

    private final UUID siteId;
    private final long expectedLockVersion;

    public SiteOptimisticLockException(UUID siteId, long expectedLockVersion) {
        super("Conflicto de concurrencia optimista en sede: " + siteId + ", versión esperada: " + expectedLockVersion);
        this.siteId = siteId;
        this.expectedLockVersion = expectedLockVersion;
    }

    public UUID getSiteId() {
        return siteId;
    }

    public long getExpectedLockVersion() {
        return expectedLockVersion;
    }
}
