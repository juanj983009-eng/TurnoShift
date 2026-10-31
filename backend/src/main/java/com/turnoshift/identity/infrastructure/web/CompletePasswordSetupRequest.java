package com.turnoshift.identity.infrastructure.web;

/**
 * Petición para completar el establecimiento de contraseña consumiendo el token.
 *
 * @param token       Secreto del enlace de configuración (43 caracteres Base64 URL-safe).
 * @param newPassword Nueva contraseña en texto plano propuesta por el usuario.
 */
public record CompletePasswordSetupRequest(
        String token,
        String newPassword
) {}
