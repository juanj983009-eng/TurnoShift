package com.turnoshift.operator.domain;

import java.time.ZoneId;
import java.util.Objects;

/**
 * Comando inmutable y fuertemente tipado para la incorporación asistida de una organización.
 *
 * <p>Representa la intención de negocio validada y normalizada antes de su ejecución
 * transaccional y cálculo de huella de idempotencia.
 */
public record IncorporateOrganizationCommand(
        String orgCode,
        String orgDisplayName,
        ZoneId timeZone,
        String siteDisplayName,
        String adminEmail,
        String adminDisplayName
) {
    public IncorporateOrganizationCommand {
        Objects.requireNonNull(orgCode, "orgCode no puede ser nulo");
        Objects.requireNonNull(orgDisplayName, "orgDisplayName no puede ser nulo");
        Objects.requireNonNull(timeZone, "timeZone no puede ser nulo");
        Objects.requireNonNull(siteDisplayName, "siteDisplayName no puede ser nulo");
        Objects.requireNonNull(adminEmail, "adminEmail no puede ser nulo");
        Objects.requireNonNull(adminDisplayName, "adminDisplayName no puede ser nulo");
    }
}
