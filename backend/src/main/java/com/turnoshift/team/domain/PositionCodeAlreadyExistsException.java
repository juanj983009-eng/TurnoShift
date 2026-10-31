package com.turnoshift.team.domain;

public final class PositionCodeAlreadyExistsException extends RuntimeException {
    private final String code;

    public PositionCodeAlreadyExistsException(String code) {
        super("El código de puesto '" + code + "' ya existe en la organización");
        this.code = code;
    }

    public String getCode() { return code; }
}
