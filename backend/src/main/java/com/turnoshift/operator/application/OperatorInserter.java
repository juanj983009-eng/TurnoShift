package com.turnoshift.operator.application;

/**
 * Puerto para inserción transaccional y controlada del primer operador de plataforma.
 *
 * <p>Aisla el caso de uso/runner de los detalles de persistencia y concurrencia
 * (bloqueo exclusivo {@code sp_getapplock} y transacción SQL Server).
 */
@FunctionalInterface
public interface OperatorInserter {

    /**
     * Inserta el primer operador si y solo si no existe ninguno.
     *
     * @param email           correo original del operador
     * @param normalizedEmail correo normalizado para búsqueda BIN2
     * @param displayName     nombre del operador
     * @param encodedHash     hash Argon2id ya codificado
     */
    void insertFirstOperatorIfNone(String email,
                                   String normalizedEmail,
                                   String displayName,
                                   String encodedHash);
}
