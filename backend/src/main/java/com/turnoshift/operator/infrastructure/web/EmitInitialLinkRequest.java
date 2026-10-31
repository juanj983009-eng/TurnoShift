package com.turnoshift.operator.infrastructure.web;

import java.util.UUID;

/**
 * Solicitud para emitir el enlace de configuración inicial del primer administrador.
 */
public record EmitInitialLinkRequest(
        UUID adminUserId,
        Long expectedVersion
) {
}
