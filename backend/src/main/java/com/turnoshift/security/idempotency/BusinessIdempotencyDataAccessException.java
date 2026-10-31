package com.turnoshift.security.idempotency;

/**
 * Excepción segura lanzada ante fallos de persistencia o coordinación de idempotencia empresarial.
 * No expone detalles internos de base de datos, consultas SQL ni parámetros.
 */
public class BusinessIdempotencyDataAccessException extends RuntimeException {

    public BusinessIdempotencyDataAccessException() {
        super("Error de persistencia en la operación de idempotencia empresarial");
    }

    public BusinessIdempotencyDataAccessException(Throwable cause) {
        super("Error de persistencia en la operación de idempotencia empresarial", cause);
    }
}
