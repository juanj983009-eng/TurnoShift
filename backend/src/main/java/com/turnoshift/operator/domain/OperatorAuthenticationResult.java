package com.turnoshift.operator.domain;

/**
 * Resultado de un intento de autenticación de operador de plataforma.
 */
public enum OperatorAuthenticationResult {
    /** Autenticación exitosa. */
    SUCCESS,
    /** Credenciales inválidas o cuenta inactiva — sin distinción deliberada. */
    INVALID_CREDENTIALS,
    /** Fallo técnico de acceso a datos (no expone causa al cliente). */
    DATA_ACCESS_ERROR
}
