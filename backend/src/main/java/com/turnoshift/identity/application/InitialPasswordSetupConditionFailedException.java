package com.turnoshift.identity.application;

/**
 * Excepción interna utilizada para forzar el rollback transaccional
 * cuando las condiciones de actualización en {@code app_users} no se cumplen
 * tras haber consumido un token.
 */
final class InitialPasswordSetupConditionFailedException extends RuntimeException {

    InitialPasswordSetupConditionFailedException() {
        super("[IDENTITY_INITIAL_PASSWORD_CONDITION_FAILED] Las condiciones de usuario u organización no se cumplieron al persistir la contraseña inicial.");
    }
}
