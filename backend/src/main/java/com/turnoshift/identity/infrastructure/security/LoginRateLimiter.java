package com.turnoshift.identity.infrastructure.security;

/**
 * Puerto para limitación de tasa (rate limiting) de intentos de autenticación por IP.
 *
 * <p>Protege la aplicación contra ataques de fuerza bruta y agotamiento de CPU por Argon2id.
 */
public interface LoginRateLimiter {

    /**
     * Intenta registrar un intento de autenticación para la dirección IP dada.
     *
     * @param ipAddress dirección IP del cliente (no nula ni en blanco)
     * @return {@link RateLimitResult.Allowed} si el intento está dentro del límite;
     *         {@link RateLimitResult.Rejected} con el tiempo en segundos a esperar si se superó.
     */
    RateLimitResult tryAcquire(String ipAddress);

    /**
     * Restablece el contador de intentos para la dirección IP (por ejemplo, tras autenticación exitosa).
     *
     * @param ipAddress dirección IP del cliente
     */
    void reset(String ipAddress);

    /**
     * Resultado tipado de la evaluación de tasa.
     */
    sealed interface RateLimitResult {
        record Allowed(int remainingAttempts) implements RateLimitResult {}
        record Rejected(long retryAfterSeconds) implements RateLimitResult {}
    }
}
