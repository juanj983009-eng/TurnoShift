package com.turnoshift.schedule.domain.validation;

import java.util.Objects;
import java.util.UUID;

/**
 * Resumen de minutos asignados versus umbral semanal de advertencia para un colaborador.
 */
public record EmployeeWeeklySummary(
        UUID employeeId,
        String employeeName,
        int plannedMinutes,
        int thresholdMinutes,
        boolean exceeded
) {

    public EmployeeWeeklySummary {
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(employeeName, "employeeName no puede ser nulo");
    }
}
