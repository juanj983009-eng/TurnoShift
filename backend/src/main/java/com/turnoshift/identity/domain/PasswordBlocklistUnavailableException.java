package com.turnoshift.identity.domain;

/**
 * Excepción técnica de dominio lanzada cuando el servicio o proveedor
 * de contraseñas bloqueadas (p. ej. HIBP) no se encuentra disponible,
 * agota su tiempo de espera o produce una respuesta no confiable.
 *
 * <p>Esta excepción representa una condición de falla cerrada (fail-closed)
 * para flujos de establecimiento o cambio de contraseñas y se mapea
 * a HTTP 503 SERVICE_UNAVAILABLE en la capa web.
 *
 * <p>Por seguridad estricta, jamás debe incluir contraseñas en texto plano,
 * hashes completos, prefijos de búsqueda ni detalles de infraestructura en sus mensajes.
 */
public final class PasswordBlocklistUnavailableException extends RuntimeException {

    private static final String MENSAJE_PREDETERMINADO =
            "[IDENTITY_BLOCKLIST_UNAVAILABLE] El servicio de verificación de contraseñas no está disponible temporalmente.";

    public PasswordBlocklistUnavailableException() {
        super(MENSAJE_PREDETERMINADO);
    }

    public PasswordBlocklistUnavailableException(Throwable cause) {
        super(MENSAJE_PREDETERMINADO, cause);
    }
}
