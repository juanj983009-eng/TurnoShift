package com.turnoshift.operator.domain;

import com.turnoshift.identity.domain.CapacidadEmailExcedidaException;
import com.turnoshift.identity.domain.EmailNormalizer;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validador puro para la petición de incorporación de organizaciones (onboarding).
 *
 * <p>Centraliza las validaciones estructurales de los campos de la organización,
 * la sede inicial, el primer administrador y la cabecera {@code Idempotency-Key}.
 *
 * <p>Dominio puro sin dependencias de Spring, JDBC ni HTTP.
 */
public final class IncorporateOrganizationInputValidator {

    public static final int MIN_ORG_CODE_LENGTH = 2;
    public static final int MAX_ORG_CODE_LENGTH = 32;
    public static final int MAX_DISPLAY_NAME_LENGTH = 150;
    public static final int MAX_TIMEZONE_LENGTH = 64;

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern ORG_CODE_PATTERN = Pattern.compile("^[A-Z0-9\\-]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private IncorporateOrganizationInputValidator() {
        // Utilidad estática pura
    }

    /**
     * Valida estructuralmente todos los datos requeridos para la incorporación de una organización.
     *
     * @param idempotencyKey      clave de idempotencia recibida en la cabecera HTTP
     * @param rawOrgCode          código de organización
     * @param rawOrgDisplayName   nombre de la organización
     * @param rawTimeZone         identificador de zona horaria
     * @param rawSiteDisplayName  nombre de la sede
     * @param rawAdminEmail       correo del administrador
     * @param rawAdminDisplayName nombre del administrador
     * @return lista de errores encontrados; vacía si todos los campos son válidos
     */
    public static List<OperatorValidationError> validate(
            String idempotencyKey,
            String rawOrgCode,
            String rawOrgDisplayName,
            String rawTimeZone,
            String rawSiteDisplayName,
            String rawAdminEmail,
            String rawAdminDisplayName
    ) {
        List<OperatorValidationError> errors = new ArrayList<>();

        // 1. Idempotency-Key
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            errors.add(new OperatorValidationError("idempotencyKey", "REQUIRED", "La clave de idempotencia es obligatoria."));
        } else if (!UUID_PATTERN.matcher(idempotencyKey.trim()).matches()) {
            errors.add(new OperatorValidationError("idempotencyKey", "INVALID_FORMAT", "La clave de idempotencia debe ser un UUID válido."));
        }

        // 2. organization.code
        if (rawOrgCode == null || rawOrgCode.isBlank()) {
            errors.add(new OperatorValidationError("organization.code", "REQUIRED", "El código de organización es obligatorio."));
        } else {
            String strippedCode = rawOrgCode.strip().toUpperCase(Locale.ROOT);
            if (strippedCode.length() < MIN_ORG_CODE_LENGTH || strippedCode.length() > MAX_ORG_CODE_LENGTH) {
                errors.add(new OperatorValidationError("organization.code", "INVALID_LENGTH",
                        "El código de organización debe tener entre " + MIN_ORG_CODE_LENGTH + " y " + MAX_ORG_CODE_LENGTH + " caracteres."));
            } else if (!ORG_CODE_PATTERN.matcher(strippedCode).matches()
                    || strippedCode.startsWith("-") || strippedCode.endsWith("-")) {
                errors.add(new OperatorValidationError("organization.code", "INVALID_FORMAT",
                        "El código de organización solo admite letras mayúsculas, números y guiones intermedios."));
            }
        }

        // 3. organization.displayName
        if (rawOrgDisplayName == null || rawOrgDisplayName.isBlank()) {
            errors.add(new OperatorValidationError("organization.displayName", "REQUIRED", "El nombre de la organización es obligatorio."));
        } else if (rawOrgDisplayName.strip().length() > MAX_DISPLAY_NAME_LENGTH) {
            errors.add(new OperatorValidationError("organization.displayName", "INVALID_LENGTH",
                    "El nombre de la organización no debe exceder los " + MAX_DISPLAY_NAME_LENGTH + " caracteres."));
        }

        // 4. organization.timeZone
        if (rawTimeZone == null || rawTimeZone.isBlank()) {
            errors.add(new OperatorValidationError("organization.timeZone", "REQUIRED", "La zona horaria es obligatoria."));
        } else if (rawTimeZone.strip().length() > MAX_TIMEZONE_LENGTH) {
            errors.add(new OperatorValidationError("organization.timeZone", "INVALID_LENGTH",
                    "La zona horaria no debe exceder los " + MAX_TIMEZONE_LENGTH + " caracteres."));
        } else {
            try {
                ZoneId.of(rawTimeZone.strip());
            } catch (DateTimeException e) {
                errors.add(new OperatorValidationError("organization.timeZone", "INVALID_FORMAT", "La zona horaria especificada no es válida."));
            }
        }

        // 5. site.displayName
        if (rawSiteDisplayName == null || rawSiteDisplayName.isBlank()) {
            errors.add(new OperatorValidationError("site.displayName", "REQUIRED", "El nombre de la sede es obligatorio."));
        } else if (rawSiteDisplayName.strip().length() > MAX_DISPLAY_NAME_LENGTH) {
            errors.add(new OperatorValidationError("site.displayName", "INVALID_LENGTH",
                    "El nombre de la sede no debe exceder los " + MAX_DISPLAY_NAME_LENGTH + " caracteres."));
        }

        // 6. firstAdmin.email
        if (rawAdminEmail == null || rawAdminEmail.isBlank()) {
            errors.add(new OperatorValidationError("firstAdmin.email", "REQUIRED", "El correo electrónico del administrador es obligatorio."));
        } else {
            String normalizedEmail = null;
            try {
                normalizedEmail = EmailNormalizer.normalizar(rawAdminEmail);
            } catch (Exception e) {
                errors.add(new OperatorValidationError("firstAdmin.email", "INVALID_FORMAT", "El correo electrónico no tiene un formato válido."));
            }

            if (normalizedEmail != null) {
                if (normalizedEmail.isEmpty() || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                    errors.add(new OperatorValidationError("firstAdmin.email", "INVALID_FORMAT", "El correo electrónico no tiene un formato válido."));
                } else {
                    try {
                        EmailNormalizer.validarCapacidadUtf16(normalizedEmail, "email");
                    } catch (CapacidadEmailExcedidaException e) {
                        errors.add(new OperatorValidationError("firstAdmin.email", "MAX_LENGTH_EXCEEDED", "El correo electrónico excede la capacidad permitida."));
                    }
                }
            }
        }

        // 7. firstAdmin.displayName
        if (rawAdminDisplayName == null || rawAdminDisplayName.isBlank()) {
            errors.add(new OperatorValidationError("firstAdmin.displayName", "REQUIRED", "El nombre del administrador es obligatorio."));
        } else if (rawAdminDisplayName.strip().length() > MAX_DISPLAY_NAME_LENGTH) {
            errors.add(new OperatorValidationError("firstAdmin.displayName", "INVALID_LENGTH",
                    "El nombre del administrador no debe exceder los " + MAX_DISPLAY_NAME_LENGTH + " caracteres."));
        }

        return errors;
    }
}
