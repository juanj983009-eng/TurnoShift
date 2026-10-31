package com.turnoshift.organization.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Vista de lectura de la configuración de la sede activa de una organización.
 */
public record ActiveSiteView(
        UUID id,
        String code,
        String name,
        long lockVersion
) {
    public ActiveSiteView {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(code, "code no puede ser nulo");
        Objects.requireNonNull(name, "name no puede ser nulo");
    }
}
