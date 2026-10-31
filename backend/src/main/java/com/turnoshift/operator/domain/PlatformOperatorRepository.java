package com.turnoshift.operator.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para la gestión de operadores de plataforma.
 */
public interface PlatformOperatorRepository {

    /**
     * Busca un operador por su identificador único.
     *
     * @param id identificador del operador
     * @return operador si existe
     * @throws OperatorDataAccessException si ocurre un error de persistencia
     */
    Optional<PlatformOperator> findById(UUID id);

    /**
     * Busca un operador por su correo electrónico normalizado con comparación binaria exacta.
     *
     * @param normalizedEmail correo canónico normalizado
     * @return operador si existe
     * @throws OperatorDataAccessException si ocurre un error de persistencia
     */
    Optional<PlatformOperator> findByNormalizedEmail(String normalizedEmail);

    /**
     * Guarda o inserta un nuevo operador de plataforma.
     *
     * @param operator operador a persistir
     * @return operador persistido
     * @throws OperatorDataAccessException si ocurre un error de persistencia o violación de integridad
     */
    PlatformOperator save(PlatformOperator operator);

    /**
     * Actualiza la versión de autenticación (`auth_version`) de un operador de forma atómica y condicional.
     *
     * @param id identificador del operador
     * @param expectedAuthVersion versión esperada actual
     * @param newAuthVersion nueva versión de autenticación
     * @return {@code true} si se actualizó exactamente 1 fila; {@code false} si no coincidió la versión esperada
     * @throws OperatorDataAccessException si ocurre un error de persistencia
     */
    boolean updateAuthVersion(UUID id, long expectedAuthVersion, long newAuthVersion);

    /**
     * Actualiza el estado activo de un operador de plataforma.
     *
     * @param id identificador del operador
     * @param isActive nuevo estado de activación
     * @return {@code true} si se actualizó el registro; {@code false} si el operador no existe
     * @throws OperatorDataAccessException si ocurre un error de persistencia
     */
    boolean updateActiveStatus(UUID id, boolean isActive);

    /**
     * Cuenta el número total de operadores de plataforma registrados.
     *
     * @return total de operadores
     * @throws OperatorDataAccessException si ocurre un error de persistencia
     */
    long count();
}
