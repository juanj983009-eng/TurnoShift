package com.turnoshift.identity.domain;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * Identidad autenticada mínima devuelta tras una autenticación exitosa.
 *
 * <p>Contiene únicamente los datos necesarios para establecer la sesión
 * y derivar permisos. No incluye contraseña, hash, token CSRF, cookie
 * ni datos de otra organización.
 *
 * <p>Implementa {@link Principal} retornando {@code userId.toString()} como nombre,
 * garantizando que Spring Security y Spring Session JDBC almacenen el identificador
 * opaco de usuario en {@code SPRING_SESSION.PRINCIPAL_NAME} (columna VARCHAR(100))
 * sin desbordamiento ni truncamiento.
 *
 * <p>Implementa {@link Serializable} para permitir la serialización binaria segura
 * del {@code SecurityContext} en la tabla {@code SPRING_SESSION_ATTRIBUTES}.
 *
 * <p>Sin dependencias de Spring, JDBC ni HTTP. Dominio puro.
 */
public record AuthenticatedIdentity(
        UUID userId,
        UUID organizationId,
        String normalizedEmail,
        String displayName,
        String role,
        UUID employeeId, // nullable
        long authVersion,
        String organizationDisplayName,
        String organizationTimeZone
) implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return userId.toString();
    }
}
