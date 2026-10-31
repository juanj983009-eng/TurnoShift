package com.turnoshift.team.infrastructure.web;

/**
 * Resultado interno sellado para la ejecución determinista de mutaciones de empleados.
 * Permite coordinar con el ejecutor de idempotencia los resultados terminales exitosos y de rechazo.
 */
public sealed interface EmployeeExecutionResult {

    record Success(EmployeeResponse employee) implements EmployeeExecutionResult {}

    record NotFound() implements EmployeeExecutionResult {}

    record CodeAlreadyExists() implements EmployeeExecutionResult {}

    record PositionNotFound() implements EmployeeExecutionResult {}

    record PositionInactive() implements EmployeeExecutionResult {}

    record NoActiveSite() implements EmployeeExecutionResult {}

    record LicenseLimitExceeded() implements EmployeeExecutionResult {}

    record StaleVersion() implements EmployeeExecutionResult {}
}
