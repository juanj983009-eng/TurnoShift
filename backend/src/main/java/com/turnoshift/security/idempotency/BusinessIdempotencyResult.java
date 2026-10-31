package com.turnoshift.security.idempotency;

/**
 * Resultado terminal del orquestador de idempotencia empresarial.
 * Representa los 4 estados terminales de coordinación:
 * - Proceed: clave nueva, el efecto de negocio se ejecutó exitosamente.
 * - Replay: clave previa con misma huella vigente, se devuelve el resultado persistido.
 * - KeyReused: clave previa con huella diferente (conflicto de reutilización).
 * - Expired: clave previa cuyo tiempo de vida (24h) ha expirado.
 */
public sealed interface BusinessIdempotencyResult<T> {

    record Proceed<T>(T response) implements BusinessIdempotencyResult<T> {}

    record Replay<T>(T response) implements BusinessIdempotencyResult<T> {}

    record KeyReused<T>() implements BusinessIdempotencyResult<T> {}

    record Expired<T>() implements BusinessIdempotencyResult<T> {}
}
