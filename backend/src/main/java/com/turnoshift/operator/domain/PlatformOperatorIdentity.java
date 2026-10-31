package com.turnoshift.operator.domain;

import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/**
 * Identidad autenticada de un operador de plataforma.
 *
 * <p>Implementa {@link Principal} retornando {@code id.toString()} como nombre,
 * garantizando compatibilidad con Spring Security y Spring Session JDBC
 * (PRINCIPAL_NAME VARCHAR(100) — UUID de 36 chars).
 *
 * <p>Implementa {@link Serializable} para serialización binaria segura del
 * {@code SecurityContext} en {@code SPRING_SESSION_ATTRIBUTES}.
 *
 * <p>{@link #toString()} redacta completamente el id, el correo y el hash
 * para prevenir fugas en logs o diagnósticos.
 *
 * <p>Sin dependencias de Spring, JDBC ni HTTP. Dominio puro.
 */
public record PlatformOperatorIdentity(
        UUID id,
        String displayName,
        long authVersion
) implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    public PlatformOperatorIdentity {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(displayName, "displayName no puede ser nulo");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName no puede estar vacío");
        }
        if (authVersion < 0) {
            throw new IllegalArgumentException("authVersion no puede ser negativa");
        }
    }

    /** Retorna el UUID como string de 36 chars para Spring Security / Spring Session. */
    @Override
    public String getName() {
        return id.toString();
    }

    /** Nunca expone id, email, hash ni otros identificadores sensibles. */
    @Override
    public String toString() {
        return "PlatformOperatorIdentity["
                + "id=[PROTECTED]"
                + ", displayName=" + displayName
                + ", authVersion=" + authVersion
                + "]";
    }
}
