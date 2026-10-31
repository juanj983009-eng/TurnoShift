package com.turnoshift.team.infrastructure.web;

/**
 * Resultado interno sellado para la ejecución determinista de mutaciones de puestos.
 * Permite coordinar con el ejecutor de idempotencia los resultados terminales exitosos y de rechazo.
 */
public sealed interface PositionExecutionResult {

    record Success(PositionResponse position) implements PositionExecutionResult {}

    record CodeAlreadyExists() implements PositionExecutionResult {}

    record NotFound() implements PositionExecutionResult {}

    record StaleVersion() implements PositionExecutionResult {}
}
