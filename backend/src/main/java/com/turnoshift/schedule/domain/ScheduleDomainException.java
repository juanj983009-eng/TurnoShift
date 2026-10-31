package com.turnoshift.schedule.domain;

/**
 * Excepción base para violaciones de invariantes y reglas de negocio del dominio de planificación.
 */
public class ScheduleDomainException extends RuntimeException {

    public ScheduleDomainException(String message) {
        super(message);
    }

    public ScheduleDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
