package com.turnoshift.schedule.domain.ports;

/**
 * Excepción lanzada cuando no es posible adquirir el candado operativo de planificación
 * debido a concurrencia o expiración del tiempo de espera de sp_getapplock.
 */
public class PlanningOperationBusyException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
            "El recurso de planificación de la organización está ocupado por otra operación concurrente.";

    public PlanningOperationBusyException() {
        super(DEFAULT_MESSAGE);
    }

    public PlanningOperationBusyException(String message) {
        super(message);
    }

    public PlanningOperationBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
