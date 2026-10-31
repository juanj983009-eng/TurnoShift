package com.turnoshift.identity.application;

/**
 * Puerto para verificación de contraseñas.
 *
 * <p>Permite sustituir Argon2 real por un doble rápido en pruebas unitarias,
 * evitando el coste de ~500 ms por verificación.
 *
 * <p>La implementación de producción delega en
 * {@link com.turnoshift.identity.infrastructure.security.Argon2PasswordService}.
 */
public interface PasswordVerifier {

    /**
     * Verifica si una contraseña en texto plano (normalizada NFC)
     * coincide con un hash almacenado.
     *
     * @param preparedPassword contraseña normalizada NFC, sin recortar espacios
     * @param storedHash       hash tal como se almacenó en base de datos
     * @return {@code true} si coincide
     */
    boolean matches(String preparedPassword, String storedHash);
}
