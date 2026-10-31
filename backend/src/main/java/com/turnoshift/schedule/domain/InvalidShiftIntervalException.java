package com.turnoshift.schedule.domain;

/**
 * Excepción lanzada cuando un intervalo de turno no cumple las restricciones
 * de inicio/fin o los límites de duración operativa (15 a 960 minutos).
 */
public class InvalidShiftIntervalException extends ScheduleDomainException {

    public InvalidShiftIntervalException(String message) {
        super(message);
    }
}
