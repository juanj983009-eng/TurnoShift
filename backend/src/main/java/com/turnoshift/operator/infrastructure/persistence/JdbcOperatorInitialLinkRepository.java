package com.turnoshift.operator.infrastructure.persistence;

import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.OperatorInitialLinkRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para la consulta y actualización de bloqueo de administrador en SQL Server.
 */
@Repository
public class JdbcOperatorInitialLinkRepository implements OperatorInitialLinkRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOperatorInitialLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public Optional<AdminLinkTarget> findAdminLinkTarget(UUID organizationId, UUID adminUserId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(adminUserId, "adminUserId no puede ser nulo");

        String sql = """
                SELECT
                    o.id AS org_id,
                    o.service_status AS org_service_status,
                    u.id AS user_id,
                    u.role_code,
                    u.is_active AS user_is_active,
                    CASE WHEN u.password_hash IS NOT NULL THEN 1 ELSE 0 END AS has_password,
                    u.lock_version
                FROM organizations o WITH (HOLDLOCK)
                LEFT JOIN app_users u WITH (UPDLOCK, HOLDLOCK)
                    ON u.organization_id = o.id AND u.id = ?
                WHERE o.id = ?
                """;

        try {
            List<AdminLinkTarget> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
                UUID orgId = UUID.fromString(rs.getString("org_id"));
                String orgStatus = rs.getString("org_service_status");
                String userIdStr = rs.getString("user_id");
                UUID userId = userIdStr != null ? UUID.fromString(userIdStr) : null;
                String roleCode = rs.getString("role_code");
                boolean userIsActive = rs.getBoolean("user_is_active");
                boolean hasPassword = rs.getInt("has_password") == 1;
                long lockVersion = rs.getLong("lock_version");

                return new AdminLinkTarget(
                        orgId,
                        orgStatus,
                        userId,
                        roleCode,
                        userIsActive,
                        hasPassword,
                        lockVersion
                );
            }, adminUserId, organizationId);

            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (DataAccessException e) {
            throw new OperatorDataAccessException();
        }
    }

    @Override
    public boolean incrementUserLockVersion(UUID organizationId, UUID adminUserId, long expectedVersion, Instant now) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(adminUserId, "adminUserId no puede ser nulo");
        Objects.requireNonNull(now, "now no puede ser nulo");

        String sql = """
                UPDATE app_users
                SET lock_version = lock_version + 1,
                    updated_at = SYSUTCDATETIME()
                WHERE organization_id = ? AND id = ? AND lock_version = ?
                """;

        try {
            int updated = jdbcTemplate.update(sql, organizationId, adminUserId, expectedVersion);
            return updated > 0;
        } catch (DataAccessException e) {
            throw new OperatorDataAccessException();
        }
    }
}
