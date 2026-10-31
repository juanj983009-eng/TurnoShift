package com.turnoshift.identity.domain;

import java.util.List;

/**
 * Respuesta tipada de un intento de autenticación.
 *
 * <p>Contiene el resultado ({@link AuthenticationResult}), la identidad
 * autenticada (solo si SUCCESS), y lista estructurada de errores de validación
 * (solo si VALIDATION_FAILED).
 *
 * <p>No expone contraseñas, hashes ni detalles de excepción interna.
 */
public final class AuthenticationOutcome {

    private final AuthenticationResult result;
    private final AuthenticatedIdentity identity;
    private final List<LoginValidationError> validationErrors;

    private AuthenticationOutcome(AuthenticationResult result,
                                  AuthenticatedIdentity identity,
                                  List<LoginValidationError> validationErrors) {
        this.result = result;
        this.identity = identity;
        this.validationErrors = validationErrors;
    }

    public static AuthenticationOutcome success(AuthenticatedIdentity identity) {
        return new AuthenticationOutcome(AuthenticationResult.SUCCESS, identity, null);
    }

    public static AuthenticationOutcome invalidCredentials() {
        return new AuthenticationOutcome(AuthenticationResult.INVALID_CREDENTIALS, null, null);
    }

    public static AuthenticationOutcome organizationAccessDenied() {
        return new AuthenticationOutcome(AuthenticationResult.ORGANIZATION_ACCESS_DENIED, null, null);
    }

    public static AuthenticationOutcome validationFailed(List<LoginValidationError> errors) {
        return new AuthenticationOutcome(
                AuthenticationResult.VALIDATION_FAILED,
                null,
                errors != null ? List.copyOf(errors) : List.of()
        );
    }

    public static AuthenticationOutcome validationFailed(String message) {
        return new AuthenticationOutcome(
                AuthenticationResult.VALIDATION_FAILED,
                null,
                List.of(new LoginValidationError(null, "VALIDATION_FAILED", message))
        );
    }

    public AuthenticationResult result() {
        return result;
    }

    /** Solo disponible cuando {@link #result()} es {@link AuthenticationResult#SUCCESS}. */
    public AuthenticatedIdentity identity() {
        return identity;
    }

    /** Lista estructurada de errores cuando {@link #result()} es {@link AuthenticationResult#VALIDATION_FAILED}. */
    public List<LoginValidationError> validationErrors() {
        return validationErrors != null ? validationErrors : List.of();
    }

    /**
     * Mensaje del primer error de validación, o null si no hubo error de validación.
     * Mantenido para retrocompatibilidad.
     */
    public String validationMessage() {
        if (validationErrors == null || validationErrors.isEmpty()) {
            return null;
        }
        return validationErrors.get(0).message();
    }

    @Override
    public String toString() {
        // No expone identidad ni detalles — solo el resultado
        return "AuthenticationOutcome{result=" + result + "}";
    }
}
