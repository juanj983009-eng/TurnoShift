package com.turnoshift.operator.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validador puro para la solicitud de emisión de enlace inicial de contraseña.
 *
 * <p>Centraliza las validaciones estructurales de la cabecera {@code Idempotency-Key},
 * el parámetro de ruta {@code organizationId}, y el cuerpo con {@code adminUserId} y {@code expectedVersion}.
 *
 * <p>Dominio puro sin dependencias de Spring, JDBC ni HTTP.
 */
public final class EmitInitialAccessLinkInputValidator {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private EmitInitialAccessLinkInputValidator() {
        // Utilidad estática pura
    }

    /**
     * Valida estructuralmente todos los datos requeridos para emitir un enlace inicial.
     *
     * @param idempotencyKey      clave de idempotencia de la cabecera HTTP
     * @param rawOrganizationId   identificador de la organización (path param)
     * @param rawAdminUserId      identificador del usuario administrador (body)
     * @param expectedVersion     versión esperada del registro del usuario (body)
     * @return lista de errores de validación; vacía si todos los campos son conformes
     */
    public static List<OperatorValidationError> validate(
            String idempotencyKey,
            String rawOrganizationId,
            String rawAdminUserId,
            Long expectedVersion
    ) {
        List<OperatorValidationError> errors = new ArrayList<>();

        // 1. Idempotency-Key
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            errors.add(new OperatorValidationError("idempotencyKey", "REQUIRED", "La clave de idempotencia es obligatoria."));
        } else if (!UUID_PATTERN.matcher(idempotencyKey.trim()).matches()) {
            errors.add(new OperatorValidationError("idempotencyKey", "INVALID_FORMAT", "La clave de idempotencia debe ser un UUID válido."));
        }

        // 2. organizationId
        if (rawOrganizationId == null || rawOrganizationId.isBlank()) {
            errors.add(new OperatorValidationError("organizationId", "REQUIRED", "El identificador de organización es obligatorio."));
        } else if (!UUID_PATTERN.matcher(rawOrganizationId.trim()).matches()) {
            errors.add(new OperatorValidationError("organizationId", "INVALID_FORMAT", "El identificador de organización debe ser un UUID válido."));
        }

        // 3. adminUserId
        if (rawAdminUserId == null || rawAdminUserId.isBlank()) {
            errors.add(new OperatorValidationError("adminUserId", "REQUIRED", "El identificador del administrador es obligatorio."));
        } else if (!UUID_PATTERN.matcher(rawAdminUserId.trim()).matches()) {
            errors.add(new OperatorValidationError("adminUserId", "INVALID_FORMAT", "El identificador del administrador debe ser un UUID válido."));
        }

        // 4. expectedVersion
        if (expectedVersion == null) {
            errors.add(new OperatorValidationError("expectedVersion", "REQUIRED", "La versión esperada del administrador es obligatoria."));
        } else if (expectedVersion < 0) {
            errors.add(new OperatorValidationError("expectedVersion", "INVALID_VALUE", "La versión esperada debe ser mayor o igual a 0."));
        }

        return errors;
    }
}
