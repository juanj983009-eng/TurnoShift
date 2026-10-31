package com.turnoshift.schedule.domain;

/**
 * Excepción técnica lanzada ante fallos inesperados de acceso a datos en la capa JDBC de planificación.
 */
public class ScheduleDataAccessException extends RuntimeException {

    public ScheduleDataAccessException(String message) {
        super(message);
    }

    public ScheduleDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
