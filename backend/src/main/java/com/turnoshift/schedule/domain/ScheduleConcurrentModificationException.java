package com.turnoshift.schedule.domain;

/**
 * Excepción lanzada cuando una operación sobre un borrador o cuadrante falla
 * debido a que el recurso fue modificado concurrentemente (conflicto de lock_version).
 */
public class ScheduleConcurrentModificationException extends ScheduleDomainException {

    public ScheduleConcurrentModificationException(String message) {
        super(message);
    }
}
