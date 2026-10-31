package com.turnoshift.organization.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Vista de lectura de la configuración de una organización.
 * <p>
 * <b>Limitación de Zona Horaria (timezoneId):</b>
 * La zona horaria es visible en lectura para contextualizar las operaciones de la organización,
 * pero deliberadamente no existe soporte ni caso de uso para modificar {@code timezoneId} en I2.1.
 * La modificación de la zona horaria requiere el modelo integral de planificación y cálculo
 * de turnos de I3 para validar y previsualizar impactos en coberturas y turnos existentes.
 */
public record OrganizationView(
        UUID id,
        String displayName,
        String timezoneId,
        long lockVersion
) {
    public OrganizationView {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(displayName, "displayName no puede ser nulo");
        Objects.requireNonNull(timezoneId, "timezoneId no puede ser nulo");
    }
}
