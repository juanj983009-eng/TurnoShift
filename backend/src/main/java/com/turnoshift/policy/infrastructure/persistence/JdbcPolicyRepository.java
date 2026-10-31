package com.turnoshift.policy.infrastructure.persistence;

import com.turnoshift.policy.domain.NewPolicyVersion;
import com.turnoshift.policy.domain.PolicyConcurrentModificationException;
import com.turnoshift.policy.domain.PolicyDataAccessException;
import com.turnoshift.policy.domain.PolicyOperationBusyException;
import com.turnoshift.policy.domain.PolicyReason;
import com.turnoshift.policy.domain.PolicyRepository;
import com.turnoshift.policy.domain.PolicyThresholds;
import com.turnoshift.policy.domain.PolicyVersion;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para el repositorio de versiones de políticas operativas en SQL Server 2022.
 * Implementa lecturas filtradas por organization_id, orden descendente por version_number,
 * exclusión mutua de activación vía sp_getapplock con SELECT @res, inserción con timestamps
 * autoritativos de base de datos vía OUTPUT inserted.*, y actualización condicional atómica del puntero.
 */
@Repository
public class JdbcPolicyRepository implements PolicyRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<PolicyVersion> POLICY_VERSION_ROW_MAPPER = (rs, rowNum) -> {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID orgId = UUID.fromString(rs.getString("organization_id"));
        int versionNumber = rs.getInt("version_number");
        int minRest = rs.getInt("minimum_rest_minutes");
        int weeklyWarning = rs.getInt("weekly_warning_minutes");
        Instant effectiveAt = rs.getObject("effective_at", OffsetDateTime.class).toInstant();
        UUID createdByUserId = UUID.fromString(rs.getString("created_by_user_id"));
        String changeReasonStr = rs.getString("change_reason");
        Instant createdAt = rs.getObject("created_at", OffsetDateTime.class).toInstant();

        return new PolicyVersion(
                id,
                orgId,
                versionNumber,
                new PolicyThresholds(minRest, weeklyWarning),
                effectiveAt,
                createdByUserId,
                new PolicyReason(changeReasonStr),
                createdAt
        );
    };

    public JdbcPolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public Optional<PolicyVersion> findCurrentPolicy(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        String sql = """
                SELECT p.id, p.organization_id, p.version_number, p.minimum_rest_minutes,
                       p.weekly_warning_minutes, p.effective_at, p.created_by_user_id,
                       p.change_reason, p.created_at
                FROM planning_policy_versions p
                INNER JOIN organizations o
                  ON o.current_policy_version_id = p.id AND o.id = p.organization_id
                WHERE o.id = ?;
                """;
        try {
            List<PolicyVersion> results = jdbcTemplate.query(sql, POLICY_VERSION_ROW_MAPPER, organizationId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new PolicyDataAccessException("Error al consultar la política actual de la organización", e);
        }
    }

    @Override
    public Optional<UUID> findCurrentPolicyVersionId(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        String sql = "SELECT current_policy_version_id FROM organizations WHERE id = ?";
        try {
            List<UUID> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
                String val = rs.getString("current_policy_version_id");
                return val != null ? UUID.fromString(val) : null;
            }, organizationId);
            return results.isEmpty() || results.get(0) == null ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new PolicyDataAccessException("Error al consultar el ID de la política actual de la organización", e);
        }
    }

    @Override
    public List<PolicyVersion> findHistory(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        String sql = """
                SELECT id, organization_id, version_number, minimum_rest_minutes,
                       weekly_warning_minutes, effective_at, created_by_user_id,
                       change_reason, created_at
                FROM planning_policy_versions
                WHERE organization_id = ?
                ORDER BY version_number DESC;
                """;
        try {
            return jdbcTemplate.query(sql, POLICY_VERSION_ROW_MAPPER, organizationId);
        } catch (DataAccessException e) {
            throw new PolicyDataAccessException("Error al consultar el historial de versiones de política", e);
        }
    }

    @Override
    public int findNextVersionNumber(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        String sql = "SELECT COALESCE(MAX(version_number), 0) + 1 FROM planning_policy_versions WHERE organization_id = ?";
        try {
            Integer next = jdbcTemplate.queryForObject(sql, Integer.class, organizationId);
            return next != null ? next : 1;
        } catch (DataAccessException e) {
            throw new PolicyDataAccessException("Error al calcular el siguiente número de versión de política", e);
        }
    }

    @Override
    public void acquirePolicyLock(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new PolicyDataAccessException(
                    "No se puede adquirir el bloqueo de política: se requiere una transacción activa."
            );
        }

        String resourceName = "TurnoShift:Policy:Org:" + organizationId;
        // SQL Server exige @@TRANCOUNT > 0 para sp_getapplock con @LockOwner = 'Transaction'.
        // El driver JDBC de SQL Server opera con transacciones implícitas (autoCommit=false),
        // pero difiere la apertura física de la transacción hasta la primera sentencia DML.
        // Dado que EXEC sp_getapplock no es DML, @@TRANCOUNT permanece en 0 si es la primera
        // instrucción del bloque transaccional de Spring.
        // 'IF @@TRANCOUNT = 0 BEGIN TRANSACTION;' asegura que la transacción física exista
        // en SQL Server sin introducir COMMIT/ROLLBACK manuales: Spring gestiona el ciclo de vida
        // completo y su commit/rollback libera automáticamente el applock transaccional.
        String sql = """
                SET NOCOUNT ON;
                IF @@TRANCOUNT = 0 BEGIN TRANSACTION;
                DECLARE @res INT;
                EXEC @res = sp_getapplock
                    @Resource = ?,
                    @LockMode = 'Exclusive',
                    @LockOwner = 'Transaction',
                    @LockTimeout = 5000;
                SELECT @res;
                """;
        try {
            Integer lockResult = jdbcTemplate.queryForObject(sql, Integer.class, resourceName);
            if (lockResult == null || lockResult < 0) {
                throw new PolicyOperationBusyException();
            }
        } catch (DataAccessException e) {
            throw new PolicyDataAccessException("Error de acceso a datos al intentar adquirir el bloqueo de aplicación", e);
        }
    }

    @Override
    public PolicyVersion insertPolicyVersion(NewPolicyVersion newVersion) {
        Objects.requireNonNull(newVersion, "newVersion no puede ser nulo");
        String sql = """
                INSERT INTO planning_policy_versions (
                    organization_id, version_number, minimum_rest_minutes, weekly_warning_minutes,
                    created_by_user_id, change_reason
                )
                OUTPUT inserted.id,
                       inserted.organization_id,
                       inserted.version_number,
                       inserted.minimum_rest_minutes,
                       inserted.weekly_warning_minutes,
                       inserted.effective_at,
                       inserted.created_by_user_id,
                       inserted.change_reason,
                       inserted.created_at
                VALUES (?, ?, ?, ?, ?, ?);
                """;
        try {
            List<PolicyVersion> results = jdbcTemplate.query(
                    sql,
                    POLICY_VERSION_ROW_MAPPER,
                    newVersion.organizationId(),
                    newVersion.versionNumber(),
                    newVersion.thresholds().minimumRestMinutes(),
                    newVersion.thresholds().weeklyWarningMinutes(),
                    newVersion.createdByUserId(),
                    newVersion.changeReason().value()
            );
            if (results.isEmpty()) {
                throw new PolicyDataAccessException("No se obtuvo resultado de la inserción de la versión de política");
            }
            return results.get(0);
        } catch (DataAccessException e) {
            throw new PolicyDataAccessException("Error al insertar la versión de política", e);
        }
    }

    @Override
    public void updateCurrentPolicyVersion(UUID organizationId, UUID newPolicyVersionId, UUID expectedCurrentPolicyVersionId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(newPolicyVersionId, "newPolicyVersionId no puede ser nulo");

        int updatedRows;
        try {
            if (expectedCurrentPolicyVersionId == null) {
                String sql = """
                        UPDATE organizations
                        SET current_policy_version_id = ?,
                            lock_version = lock_version + 1,
                            updated_at = SYSUTCDATETIME()
                        WHERE id = ? AND current_policy_version_id IS NULL;
                        """;
                updatedRows = jdbcTemplate.update(sql, newPolicyVersionId, organizationId);
            } else {
                String sql = """
                        UPDATE organizations
                        SET current_policy_version_id = ?,
                            lock_version = lock_version + 1,
                            updated_at = SYSUTCDATETIME()
                        WHERE id = ? AND current_policy_version_id = ?;
                        """;
                updatedRows = jdbcTemplate.update(sql, newPolicyVersionId, organizationId, expectedCurrentPolicyVersionId);
            }
        } catch (DataAccessException e) {
            throw new PolicyDataAccessException("Error al actualizar el puntero de la política actual de la organización", e);
        }

        if (updatedRows == 0) {
            throw new PolicyConcurrentModificationException(
                    "Conflicto de modificación concurrente al actualizar el puntero de política actual de la organización."
            );
        }
    }
}
