package com.turnoshift.team.domain;

import java.util.UUID;

public class PositionInactiveException extends RuntimeException {
    private final UUID positionId;

    public PositionInactiveException(UUID positionId) {
        super("El puesto con id " + positionId + " se encuentra inactivo");
        this.positionId = positionId;
    }

    public UUID positionId() {
        return positionId;
    }
}
