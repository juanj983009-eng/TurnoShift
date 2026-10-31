package com.turnoshift.schedule.infrastructure.persistence;

import com.turnoshift.schedule.domain.ScheduleDataAccessException;
import com.turnoshift.schedule.domain.ports.PlanningOperationBusyException;
import com.turnoshift.schedule.domain.ports.PlanningOperationalLockPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador JDBC para el candado operativo de planificación mediante sp_getapplock en SQL Server 2022.
 *
 * <p>Recurso exacto: {@code TurnoShift_OrgPlanning_<organizationId>}.
 * Requiere una transacción Spring activa y asegura la apertura física de la transacción de SQL Server
 * mediante {@code IF @@TRANCOUNT = 0 BEGIN TRANSACTION;}.
 */
@Component
public class JdbcPlanningOperationalLockAdapter implements PlanningOperationalLockPort {

    public static final String RESOURCE_PREFIX = "TurnoShift_OrgPlanning_";
    public static final int DEFAULT_TIMEOUT_MS = 10_000;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPlanningOperationalLockAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public void acquirePlanningLock(UUID organizationId) {
        acquirePlanningLock(organizationId, DEFAULT_TIMEOUT_MS);
    }

    @Override
    public void acquirePlanningLock(UUID organizationId, int timeoutMs) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "No se puede adquirir el candado operativo de planificación: se requiere una transacción activa."
            );
        }

        String resourceName = RESOURCE_PREFIX + organizationId;
        String sql = """
                SET NOCOUNT ON;
                IF @@TRANCOUNT = 0 BEGIN TRANSACTION;
                DECLARE @res INT;
                EXEC @res = sp_getapplock
                    @Resource = ?,
                    @LockMode = 'Exclusive',
                    @LockOwner = 'Transaction',
                    @LockTimeout = ?;
                SELECT @res;
                """;
        try {
            Integer lockResult = jdbcTemplate.queryForObject(sql, Integer.class, resourceName, timeoutMs);
            if (lockResult == null || lockResult < 0) {
                throw new PlanningOperationBusyException(
                        "No se pudo adquirir el candado operativo de planificación para la organización "
                                + organizationId + " (código sp_getapplock: " + lockResult + ")"
                );
            }
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException(
                    "Error de acceso a datos al intentar adquirir el candado operativo de planificación", e
            );
        }
    }
}
