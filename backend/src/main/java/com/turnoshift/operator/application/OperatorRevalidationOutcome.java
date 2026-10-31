package com.turnoshift.operator.application;

/**
 * Resultado de la revalidación de sesión de un operador de plataforma.
 */
public enum OperatorRevalidationOutcome {
    /** La sesión es válida y el operador continúa activo con la misma versión. */
    VALID,
    /** El atributo authenticatedAt expiró (excede 8 horas o reloj futuro). */
    EXPIRED,
    /** El operador está inactivo, no existe o su auth_version cambió. */
    INVALID,
    /** Error técnico de persistencia o indisponibilidad de base de datos. */
    DATA_ACCESS_ERROR
}
