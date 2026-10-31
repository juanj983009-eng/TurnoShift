package com.turnoshift.operator.infrastructure.web;

/**
 * Respuesta contractual con el enlace de acceso inicial y su fecha de expiración.
 */
public record EmitInitialLinkResponse(
        String setupLink,
        String expiresAt
) {
}
