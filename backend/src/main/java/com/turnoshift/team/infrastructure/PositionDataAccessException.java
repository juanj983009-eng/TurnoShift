package com.turnoshift.team.infrastructure;

/**
 * Excepción segura lanzada ante fallos de persistencia en la gestión de puestos.
 * No expone detalles internos de base de datos, consultas SQL ni parámetros.
 */
public class PositionDataAccessException extends RuntimeException {

    public PositionDataAccessException() {
        super("Error de persistencia en la operación de puestos");
    }

    public PositionDataAccessException(Throwable cause) {
        super("Error de persistencia en la operación de puestos", cause);
    }

    public PositionDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
