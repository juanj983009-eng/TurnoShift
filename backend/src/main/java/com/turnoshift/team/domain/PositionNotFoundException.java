package com.turnoshift.team.domain;

import java.util.UUID;

public final class PositionNotFoundException extends RuntimeException {
    private final UUID positionId;

    public PositionNotFoundException(UUID positionId) {
        super("Puesto no encontrado: " + positionId);
        this.positionId = positionId;
    }

    public UUID getPositionId() { return positionId; }
}
