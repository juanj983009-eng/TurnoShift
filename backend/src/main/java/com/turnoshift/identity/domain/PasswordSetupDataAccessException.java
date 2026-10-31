package com.turnoshift.identity.domain;

/**
 * Excepción técnica lanzada ante fallos de persistencia al actualizar contraseñas.
 *
 * <p>Por principios estrictos de seguridad, no expone sentencias SQL, credenciales,
 * hashes, tokens, correos ni identificadores en su mensaje.
 */
public final class PasswordSetupDataAccessException extends RuntimeException {

    private static final String MENSAJE_SEGURO =
            "[IDENTITY_PASSWORD_SETUP_DATA_ACCESS_ERROR] Error al acceder a los datos de usuario para el establecimiento de contraseña.";

    public PasswordSetupDataAccessException() {
        super(MENSAJE_SEGURO);
    }

    public PasswordSetupDataAccessException(Throwable cause) {
        super(MENSAJE_SEGURO, cause);
    }
}
