package com.turnoshift.identity.infrastructure.web;

/**
 * Petición para inspeccionar un enlace de configuración de contraseña sin consumirlo.
 *
 * @param token Secreto del enlace de configuración (43 caracteres Base64 URL-safe).
 */
public record InspectLinkRequest(
        String token
) {}
