package com.turnoshift.identity.domain;

import java.util.Objects;

/**
 * Representa un error de validación estructural de entrada en credenciales de acceso.
 *
 * @param field   nombre del campo con error (notación camelCase, e.g. "organizationCode", "email", "password")
 * @param code    código estable de error (e.g. "REQUIRED", "INVALID_LENGTH", "INVALID_FORMAT", "MAX_LENGTH_EXCEEDED")
 * @param message descripción legible y segura en español
 */
public record LoginValidationError(
        String field,
        String code,
        String message
) {
    public LoginValidationError {
        Objects.requireNonNull(code, "code no puede ser nulo");
        Objects.requireNonNull(message, "message no puede ser nulo");
    }
}
