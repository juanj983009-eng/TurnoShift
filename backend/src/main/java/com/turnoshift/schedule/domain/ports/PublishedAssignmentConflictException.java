package com.turnoshift.schedule.domain.ports;

/**
 * Excepción de dominio lanzada cuando una operación en un colaborador o indisponibilidad
 * entra en conflicto con turnos publicados vigentes en el futuro.
 */
public class PublishedAssignmentConflictException extends RuntimeException {

    public PublishedAssignmentConflictException(String message) {
        super(message);
    }

    public PublishedAssignmentConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
