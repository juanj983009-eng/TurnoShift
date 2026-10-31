package com.turnoshift.organization.infrastructure;

/**
 * Excepción de persistencia segura lanzada ante fallos en operaciones JDBC de configuración.
 * Oculta detalles internos de SQL Server y conexión.
 */
public class OrganizationConfigurationDataAccessException extends RuntimeException {

    public OrganizationConfigurationDataAccessException() {
        super("Error de persistencia en la configuración de la organización");
    }

    public OrganizationConfigurationDataAccessException(Throwable cause) {
        super("Error de persistencia en la configuración de la organización", cause);
    }

    public OrganizationConfigurationDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
