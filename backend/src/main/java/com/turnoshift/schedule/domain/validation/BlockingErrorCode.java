package com.turnoshift.schedule.domain.validation;

/**
 * Códigos de error bloqueante que impiden la publicación de un horario semanal.
 */
public enum BlockingErrorCode {
    POLICY_NOT_CONFIGURED,
    PAST_SHIFT_MODIFICATION,
    EMPLOYEE_INACTIVE,
    POSITION_INACTIVE,
    POSITION_MISMATCH,
    EMPLOYEE_UNAVAILABLE,
    SHIFT_OVERLAP,
    MINIMUM_REST_VIOLATION
}
