package com.turnoshift.identity.domain;

/**
 * Contrato de dominio para rechazar contraseñas comunes o comprometidas.
 *
 * <p>La implementación de producción debe integrarse con un proveedor externo
 * de contraseñas comprometidas (por ejemplo, HIBP k-anonymity API o una
 * lista local curada). No se incluye una implementación de producción vacía
 * que acepte todo.
 *
 * <p><b>Bloqueo explícito:</b> pasar {@code null} como implementación a
 * {@link PasswordPolicy#validar} lanza {@link IllegalStateException},
 * impidiendo establecer o cambiar contraseñas sin un proveedor real.
 *
 * <p>La contraseña recibida ya está normalizada (NFC) y nunca debe
 * registrarse ni transmitirse externamente.
 *
 * <p>Sin dependencias de Spring, JDBC ni HTTP. Dominio puro.
 */
public interface PasswordBlocklist {

    /**
     * Determina si una contraseña está bloqueada.
     *
     * @param contrasenaEnTextoPlano contraseña ya normalizada (NFC); nunca nula
     * @return {@code true} si la contraseña debe rechazarse
     */
    boolean estaBloquada(String contrasenaEnTextoPlano);
}
