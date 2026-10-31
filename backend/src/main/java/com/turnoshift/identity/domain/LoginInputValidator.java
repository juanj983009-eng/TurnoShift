package com.turnoshift.identity.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validador puro compartido para entradas estructurales de autenticación (login).
 *
 * <p>Centraliza las reglas estructurales de validación requeridas por el contrato OpenAPI
 * para evitar duplicación entre la capa web ({@code LoginController}) y el caso de uso
 * ({@code AuthenticateUser}).
 *
 * <p>No contiene dependencias de Spring, JDBC ni HTTP. Dominio puro.
 */
public final class LoginInputValidator {

    public static final int MAX_PASSWORD_CODE_POINTS = 128;
    public static final int MIN_ORG_CODE_LENGTH = 2;
    public static final int MAX_ORG_CODE_LENGTH = 32;

    private static final Pattern ORG_CODE_PATTERN = Pattern.compile("^[A-Z0-9\\-]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private LoginInputValidator() {
        // Utilidad estática pura
    }

    /**
     * Valida estructuralmente los tres campos de la petición de login.
     *
     * @param rawOrgCode  código de empresa sin normalizar
     * @param rawEmail    correo electrónico sin normalizar
     * @param rawPassword contraseña en texto plano
     * @return lista de errores de validación encontrados; vacía si todos los campos son válidos
     */
    public static List<LoginValidationError> validate(String rawOrgCode, String rawEmail, String rawPassword) {
        List<LoginValidationError> errors = new ArrayList<>();

        // 1. Código de organización
        if (rawOrgCode == null || rawOrgCode.isBlank()) {
            errors.add(new LoginValidationError(
                    "organizationCode",
                    "REQUIRED",
                    "El código de organización es obligatorio."
            ));
        } else {
            String normalizedOrg = normalizeOrgCode(rawOrgCode);
            if (normalizedOrg.length() < MIN_ORG_CODE_LENGTH || normalizedOrg.length() > MAX_ORG_CODE_LENGTH) {
                errors.add(new LoginValidationError(
                        "organizationCode",
                        "INVALID_LENGTH",
                        "El código de organización debe tener entre " + MIN_ORG_CODE_LENGTH + " y " + MAX_ORG_CODE_LENGTH + " caracteres."
                ));
            } else if (!ORG_CODE_PATTERN.matcher(normalizedOrg).matches()) {
                errors.add(new LoginValidationError(
                        "organizationCode",
                        "INVALID_FORMAT",
                        "El código de organización solo admite letras, números y guiones."
                ));
            }
        }

        // 2. Correo electrónico
        if (rawEmail == null || rawEmail.isBlank()) {
            errors.add(new LoginValidationError(
                    "email",
                    "REQUIRED",
                    "El correo electrónico es obligatorio."
            ));
        } else {
            String normalizedEmail = null;
            try {
                normalizedEmail = EmailNormalizer.normalizar(rawEmail);
            } catch (Exception e) {
                errors.add(new LoginValidationError(
                        "email",
                        "INVALID_FORMAT",
                        "El correo electrónico no tiene un formato válido."
                ));
            }

            if (normalizedEmail != null) {
                if (normalizedEmail.isEmpty() || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                    errors.add(new LoginValidationError(
                            "email",
                            "INVALID_FORMAT",
                            "El correo electrónico no tiene un formato válido."
                    ));
                } else {
                    try {
                        EmailNormalizer.validarCapacidadUtf16(normalizedEmail, "email");
                    } catch (CapacidadEmailExcedidaException e) {
                        errors.add(new LoginValidationError(
                                "email",
                                "MAX_LENGTH_EXCEEDED",
                                "El correo electrónico excede la capacidad permitida."
                        ));
                    }
                }
            }
        }

        // 3. Contraseña
        if (rawPassword == null || rawPassword.isEmpty()) {
            errors.add(new LoginValidationError(
                    "password",
                    "REQUIRED",
                    "La contraseña es obligatoria."
            ));
        } else {
            String nfcPassword = Normalizer.normalize(rawPassword, Normalizer.Form.NFC);
            int codePoints = nfcPassword.codePointCount(0, nfcPassword.length());
            if (codePoints > MAX_PASSWORD_CODE_POINTS) {
                errors.add(new LoginValidationError(
                        "password",
                        "MAX_LENGTH_EXCEEDED",
                        "La contraseña no debe exceder los " + MAX_PASSWORD_CODE_POINTS + " caracteres."
                ));
            }
        }

        return errors;
    }

    /**
     * Normaliza el código de empresa (trim exterior Unicode y mayúsculas con Locale.ROOT).
     */
    public static String normalizeOrgCode(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.strip().toUpperCase(Locale.ROOT);
    }
}
