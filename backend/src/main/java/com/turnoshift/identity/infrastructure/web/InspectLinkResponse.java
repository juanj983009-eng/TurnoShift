package com.turnoshift.identity.infrastructure.web;

/**
 * Respuesta a la inspección de un enlace de configuración de contraseña.
 *
 * @param valid   Si el enlace es válido y utilizable en este momento.
 * @param purpose Propósito del enlace (INITIAL_SETUP, etc.).
 */
public record InspectLinkResponse(
        boolean valid,
        String purpose
) {}
