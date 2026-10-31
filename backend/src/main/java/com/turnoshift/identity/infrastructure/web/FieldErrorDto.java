package com.turnoshift.identity.infrastructure.web;

/**
 * Detalle de error de campo para Problem Details RFC 9457 (400 VALIDATION_FAILED).
 */
public record FieldErrorDto(
        String field,
        String code,
        String message
) {}
