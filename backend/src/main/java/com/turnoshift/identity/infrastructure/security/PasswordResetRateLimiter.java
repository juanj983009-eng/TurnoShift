package com.turnoshift.identity.infrastructure.security;

/**
 * Puerto para limitación de tasa (rate limiting) de inspección y completado de configuración de contraseñas.
 *
 * <p>Protege la aplicación contra ataques de fuerza bruta sobre enlaces de configuración
 * y previene el agotamiento de recursos de cómputo (Argon2id) y red (Have I Been Pwned).
 */
public interface PasswordResetRateLimiter {

    /**
     * Intenta registrar un intento de inspección de enlace para la IP dada.
     *
     * @param ipAddress dirección IP del cliente (no nula ni en blanco)
     * @return {@link RateLimitResult.Allowed} si el intento está dentro de la cuota;
     *         {@link RateLimitResult.Rejected} con el tiempo en segundos a esperar si se superó.
     */
    RateLimitResult tryAcquireInspect(String ipAddress);

    /**
     * Intenta registrar un intento de completado de contraseña para la IP dada.
     *
     * @param ipAddress dirección IP del cliente (no nula ni en blanco)
     * @return {@link RateLimitResult.Allowed} si el intento está dentro de la cuota;
     *         {@link RateLimitResult.Rejected} con el tiempo en segundos a esperar si se superó.
     */
    RateLimitResult tryAcquireComplete(String ipAddress);

    /**
     * Restablece el contador de completado para la IP dada tras una operación exitosa.
     *
     * @param ipAddress dirección IP del cliente
     */
    void resetComplete(String ipAddress);

    /**
     * Restablece el contador de inspección para la IP dada.
     *
     * @param ipAddress dirección IP del cliente
     */
    void resetInspect(String ipAddress);

    /**
     * Resultado sellado de la evaluación de tasa.
     */
    sealed interface RateLimitResult {
        record Allowed(int remainingAttempts) implements RateLimitResult {}
        record Rejected(long retryAfterSeconds) implements RateLimitResult {}
    }
}
