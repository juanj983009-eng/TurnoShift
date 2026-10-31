package com.turnoshift.identity.domain;

/**
 * Propósito del token de configuración o restablecimiento de contraseña.
 * Alineado con la restricción CHECK de la tabla password_reset_tokens (V6).
 */
public enum ResetTokenPurpose {
    INITIAL_SETUP,
    PASSWORD_RESET
}
