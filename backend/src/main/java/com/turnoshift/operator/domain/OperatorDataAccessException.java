package com.turnoshift.operator.domain;

/**
 * Excepción técnica lanzada ante fallos de persistencia al acceder a datos de operadores de plataforma.
 *
 * <p>Por principios estrictos de seguridad, no expone sentencias SQL, credenciales,
 * hashes, correos ni identificadores en su mensaje ni permite mensajes arbitrarios externos.
 */
public final class OperatorDataAccessException extends RuntimeException {

    private static final String MENSAJE_SEGURO =
            "[OPERATOR_DATA_ACCESS_ERROR] Error al acceder a los datos de persistencia del operador de plataforma.";

    public OperatorDataAccessException() {
        super(MENSAJE_SEGURO);
    }

    public OperatorDataAccessException(Throwable cause) {
        super(MENSAJE_SEGURO, cause);
    }
}
