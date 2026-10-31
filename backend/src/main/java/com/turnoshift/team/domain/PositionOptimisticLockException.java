package com.turnoshift.team.domain;

import java.util.UUID;

public final class PositionOptimisticLockException extends RuntimeException {
    private final UUID positionId;
    private final long expectedLockVersion;

    public PositionOptimisticLockException(UUID positionId, long expectedLockVersion) {
        super("Conflicto de concurrencia optimista en puesto: " + positionId + ", versión esperada: " + expectedLockVersion);
        this.positionId = positionId;
        this.expectedLockVersion = expectedLockVersion;
    }

    public UUID getPositionId() { return positionId; }
    public long getExpectedLockVersion() { return expectedLockVersion; }
}
