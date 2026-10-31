package com.turnoshift.identity.domain;

/**
 * Resultado interno de un intento de autenticación.
 *
 * <p>Los códigos son estables y no revelan detalles internos al cliente.
 * La futura capa HTTP mapeará estos resultados a respuestas Problem Details.
 *
 * <p>No se usan excepciones distintas por causa de fallo para evitar
 * que una capa superior revele la existencia del usuario, el estado
 * de la contraseña o la cuenta.
 */
public enum AuthenticationResult {

    /** Credenciales correctas, organización activa, usuario activo. */
    SUCCESS,

    /**
     * Fallo genérico: usuario inexistente, contraseña incorrecta,
     * hash NULL, cuenta inactiva u organización inexistente.
     * No se diferencia la causa al cliente.
     */
    INVALID_CREDENTIALS,

    /**
     * Credenciales correctas pero la organización está SUSPENDIDA o CERRADA.
     * El cliente puede saber que sus credenciales son válidas pero
     * el acceso empresarial está denegado.
     */
    ORGANIZATION_ACCESS_DENIED,

    /**
     * Error puramente estructural en la entrada (campos vacíos,
     * longitud excedida, formato de código de empresa inválido).
     * Distinguible internamente como validación.
     */
    VALIDATION_FAILED
}
