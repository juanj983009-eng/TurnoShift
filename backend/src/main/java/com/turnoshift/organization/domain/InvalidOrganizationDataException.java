package com.turnoshift.organization.domain;

/**
 * Excepción lanzada cuando los datos de configuración de la organización o sede violan
 * las reglas estructurales de dominio (longitud, nulidad o versión negativa).
 */
public final class InvalidOrganizationDataException extends RuntimeException {

    public InvalidOrganizationDataException(String message) {
        super(message);
    }
}
