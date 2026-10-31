package com.turnoshift.unavailability.domain;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Puerto de dominio para resolver la zona horaria oficial configurada de una organización.
 */
public interface OrganizationTimezonePort {

    /**
     * Resuelve el {@link ZoneId} de la organización especificada.
     *
     * @param organizationId Identificador único de la organización.
     * @return El {@link ZoneId} resuelto.
     * @throws IllegalStateException Si la organización no existe o la zona no es válida.
     */
    ZoneId resolveZoneId(UUID organizationId);
}
