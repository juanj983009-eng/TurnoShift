package com.turnoshift.operator.application;

/**
 * Excepción técnica de configuración lanzada cuando el bootstrap del operador
 * no puede completarse debido a configuración inválida, contraseña inválida
 * o blocklist no disponible.
 *
 * <p>Por principios estrictos de seguridad, el mensaje es fijo y no puede
 * incluir correo, contraseña, hash ni detalles de excepción.
 */
public final class BootstrapConfigurationException extends RuntimeException {

    private static final String MENSAJE_SEGURO =
            "[OPERATOR_BOOTSTRAP_CONFIG_ERROR] El bootstrap del operador de plataforma "
            + "falló. Revisa la configuración de las variables de entorno requeridas.";

    public BootstrapConfigurationException() {
        super(MENSAJE_SEGURO);
    }
}
