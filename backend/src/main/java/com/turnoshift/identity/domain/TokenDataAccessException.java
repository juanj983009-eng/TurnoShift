package com.turnoshift.identity.domain;

/**
 * Excepción técnica de dominio lanzada ante errores de persistencia o indisponibilidad
 * de base de datos durante el ciclo de vida de tokens de configuración de contraseñas.
 *
 * <p>Por seguridad estricta, la excepción expone exclusivamente un mensaje fijo inmutable
 * y no acepta cadenas externas arbitrarias para evitar filtraciones de tokens, hashes,
 * identificadores, SQL o detalles de infraestructura.
 */
public final class TokenDataAccessException extends RuntimeException {

    private static final String MENSAJE_PREDETERMINADO =
            "[IDENTITY_TOKEN_DATA_ACCESS_ERROR] Error en el acceso a datos del ciclo de vida de tokens.";

    public TokenDataAccessException() {
        super(MENSAJE_PREDETERMINADO);
    }

    public TokenDataAccessException(Throwable cause) {
        super(MENSAJE_PREDETERMINADO, cause);
    }
}
