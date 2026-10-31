package com.turnoshift.schedule.domain.ports;

import java.util.UUID;

/**
 * Puerto para la adquisición del candado operativo transaccional a nivel de organización
 * para operaciones de planificación semanal (sp_getapplock en SQL Server).
 */
public interface PlanningOperationalLockPort {

    int DEFAULT_TIMEOUT_MS = 10_000;

    /**
     * Adquiere el candado operativo exclusivo de planificación para una organización
     * utilizando el tiempo de espera por defecto (10.000 ms).
     *
     * @param organizationId identificador de la organización
     */
    void acquirePlanningLock(UUID organizationId);

    /**
     * Adquiere el candado operativo exclusivo de planificación para una organización
     * con un tiempo de espera explícito en milisegundos.
     *
     * @param organizationId identificador de la organización
     * @param timeoutMs      tiempo de espera máximo en milisegundos
     */
    void acquirePlanningLock(UUID organizationId, int timeoutMs);
}
