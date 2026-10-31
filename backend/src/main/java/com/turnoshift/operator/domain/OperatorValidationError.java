package com.turnoshift.operator.domain;

/**
 * Representa un error de validación estructural en campos de operador.
 */
public record OperatorValidationError(
        String field,
        String code,
        String message
) {}
