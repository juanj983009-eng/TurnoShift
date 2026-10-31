package com.turnoshift.operator.domain;

import java.util.Objects;

/**
 * Resultado tipado de un intento de autenticación de operador de plataforma.
 *
 * <p>Nunca contiene contraseña, hash ni información sensible.
 * Creado exclusivamente a través de factory methods estáticos.
 */
public final class OperatorAuthenticationOutcome {

    private final OperatorAuthenticationResult result;
    private final PlatformOperatorIdentity identity;

    private OperatorAuthenticationOutcome(OperatorAuthenticationResult result,
                                          PlatformOperatorIdentity identity) {
        this.result = Objects.requireNonNull(result, "result no puede ser nulo");
        this.identity = identity;
    }

    /**
     * Autenticación exitosa.
     *
     * @param identity identidad del operador autenticado (no nula)
     */
    public static OperatorAuthenticationOutcome success(PlatformOperatorIdentity identity) {
        return new OperatorAuthenticationOutcome(
                OperatorAuthenticationResult.SUCCESS,
                Objects.requireNonNull(identity, "identity no puede ser nula en resultado exitoso"));
    }

    /** Credenciales inválidas, cuenta inactiva o no encontrada. */
    public static OperatorAuthenticationOutcome invalidCredentials() {
        return new OperatorAuthenticationOutcome(OperatorAuthenticationResult.INVALID_CREDENTIALS, null);
    }

    /** Fallo técnico de acceso a datos. */
    public static OperatorAuthenticationOutcome dataAccessError() {
        return new OperatorAuthenticationOutcome(OperatorAuthenticationResult.DATA_ACCESS_ERROR, null);
    }

    /** @return resultado de la autenticación */
    public OperatorAuthenticationResult result() {
        return result;
    }

    /**
     * @return identidad autenticada del operador; {@code null} si el resultado no es SUCCESS
     */
    public PlatformOperatorIdentity identity() {
        return identity;
    }

    @Override
    public String toString() {
        return "OperatorAuthenticationOutcome["
                + "result=" + result
                + ", identity=" + (identity != null ? identity : "null")
                + "]";
    }
}
