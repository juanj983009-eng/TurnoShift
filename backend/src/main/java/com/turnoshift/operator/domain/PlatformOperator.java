package com.turnoshift.operator.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Operador de plataforma independiente del dominio multi-tenant de clientes.
 *
 * <p>Un operador de plataforma administra la provisión de organizaciones y enlaces
 * de configuración inicial. No pertenece a ninguna organización ni posee un rol empresarial.
 *
 * <p>Por principios estrictos de seguridad, {@link #toString()} redacta completamente
 * {@code passwordHash}, {@code email} y {@code normalizedEmail} para impedir cualquier fuga
 * en registros de auditoría, logs o diagnósticos de depuración.
 */
public record PlatformOperator(
        UUID id,
        String email,
        String normalizedEmail,
        String displayName,
        String passwordHash,
        boolean isActive,
        long authVersion,
        long lockVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public PlatformOperator {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(email, "email no puede ser nulo");
        Objects.requireNonNull(normalizedEmail, "normalizedEmail no puede ser nulo");
        Objects.requireNonNull(displayName, "displayName no puede ser nulo");
        Objects.requireNonNull(passwordHash, "passwordHash no puede ser nulo");
        Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");

        if (email.trim().isEmpty() || email.length() > 254) {
            throw new IllegalArgumentException("email no puede estar vacío ni superar 254 caracteres");
        }
        if (normalizedEmail.trim().isEmpty() || normalizedEmail.length() > 254) {
            throw new IllegalArgumentException("normalizedEmail no puede estar vacío ni superar 254 caracteres");
        }
        if (displayName.trim().isEmpty() || displayName.length() > 150) {
            throw new IllegalArgumentException("displayName no puede estar vacío ni superar 150 caracteres");
        }
        if (passwordHash.trim().isEmpty() || passwordHash.length() > 512) {
            throw new IllegalArgumentException("passwordHash no puede estar vacío ni superar 512 caracteres");
        }
        if (authVersion < 0) {
            throw new IllegalArgumentException("authVersion no puede ser negativa");
        }
        if (lockVersion < 0) {
            throw new IllegalArgumentException("lockVersion no puede ser negativa");
        }
    }

    @Override
    public String toString() {
        return "PlatformOperator["
                + "id=" + id
                + ", email=[PROTECTED]"
                + ", normalizedEmail=[PROTECTED]"
                + ", displayName=" + displayName
                + ", passwordHash=[PROTECTED]"
                + ", isActive=" + isActive
                + ", authVersion=" + authVersion
                + ", lockVersion=" + lockVersion
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + "]";
    }
}
