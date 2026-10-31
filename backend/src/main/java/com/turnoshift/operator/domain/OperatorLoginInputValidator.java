package com.turnoshift.operator.domain;

import com.turnoshift.identity.domain.CapacidadEmailExcedidaException;
import com.turnoshift.identity.domain.EmailNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validador estructural puro para credenciales de inicio de sesión de operador de plataforma.
 *
 * <p>No requiere ni valida código de organización.
 * <p>Dominio puro sin dependencias de Spring ni HTTP.
 */
public final class OperatorLoginInputValidator {

    public static final int MAX_PASSWORD_CODE_POINTS = 128;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private OperatorLoginInputValidator() {
        // Utilidad estática pura
    }

    /**
     * Valida estructuralmente el email y la contraseña de la petición de login del operador.
     *
     * @param rawEmail    correo electrónico sin normalizar
     * @param rawPassword contraseña en texto plano
     * @return lista de errores de validación encontrados; vacía si los campos son válidos
     */
    public static List<OperatorValidationError> validate(String rawEmail, String rawPassword) {
        List<OperatorValidationError> errors = new ArrayList<>();

        // 1. Correo electrónico
        if (rawEmail == null || rawEmail.isBlank()) {
            errors.add(new OperatorValidationError(
                    "email",
                    "REQUIRED",
                    "El correo electrónico es obligatorio."
            ));
        } else {
            String normalizedEmail = null;
            try {
                normalizedEmail = EmailNormalizer.normalizar(rawEmail);
            } catch (Exception e) {
                errors.add(new OperatorValidationError(
                        "email",
                        "INVALID_FORMAT",
                        "El correo electrónico no tiene un formato válido."
                ));
            }

            if (normalizedEmail != null) {
                if (normalizedEmail.isEmpty() || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                    errors.add(new OperatorValidationError(
                            "email",
                            "INVALID_FORMAT",
                            "El correo electrónico no tiene un formato válido."
                    ));
                } else {
                    try {
                        EmailNormalizer.validarCapacidadUtf16(normalizedEmail, "email");
                    } catch (CapacidadEmailExcedidaException e) {
                        errors.add(new OperatorValidationError(
                                "email",
                                "MAX_LENGTH_EXCEEDED",
                                "El correo electrónico excede la capacidad permitida."
                        ));
                    }
                }
            }
        }

        // 2. Contraseña
        if (rawPassword == null || rawPassword.isEmpty()) {
            errors.add(new OperatorValidationError(
                    "password",
                    "REQUIRED",
                    "La contraseña es obligatoria."
            ));
        } else {
            int codePointCount = rawPassword.codePointCount(0, rawPassword.length());
            if (codePointCount > MAX_PASSWORD_CODE_POINTS) {
                errors.add(new OperatorValidationError(
                        "password",
                        "MAX_LENGTH_EXCEEDED",
                        "La contraseña excede la longitud máxima permitida."
                ));
            }
        }

        return errors;
    }
}
