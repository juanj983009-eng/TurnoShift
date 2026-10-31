package com.turnoshift.operator.infrastructure.web;

/**
 * Petición de autenticación del operador de plataforma (POST /api/v1/operator/auth/login).
 *
 * <p>No contiene ni requiere código de organización.
 * <p>{@link #toString()} redacta email y password para evitar fugas en diagnósticos.
 */
public record OperatorLoginRequest(
        String email,
        String password
) {
    @Override
    public String toString() {
        return "OperatorLoginRequest[email=[PROTECTED], password=[PROTECTED]]";
    }
}
