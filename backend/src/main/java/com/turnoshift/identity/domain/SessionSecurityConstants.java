package com.turnoshift.identity.domain;

import java.time.Duration;

/**
 * Constantes de seguridad para sesiones HTTP de TurnoShift.
 */
public final class SessionSecurityConstants {

    private SessionSecurityConstants() {}

    /**
     * Nombre del atributo de sesión que almacena el instante de autenticación inicial (epoch millis Long).
     * Se establece una sola vez en el login exitoso y nunca se renueva con peticiones subsiguientes.
     */
    public static final String AUTHENTICATED_AT_ATTRIBUTE = "com.turnoshift.session.authenticated_at";

    /**
     * Duración absoluta máxima de una sesión autenticada: 8 horas.
     */
    public static final Duration DEFAULT_MAX_LIFETIME = Duration.ofHours(8);

    /**
     * Tolerancia de reloj hacia el futuro para absorber leves diferencias de sincronización temporal.
     */
    public static final Duration CLOCK_SKEW_TOLERANCE = Duration.ofSeconds(60);
}
