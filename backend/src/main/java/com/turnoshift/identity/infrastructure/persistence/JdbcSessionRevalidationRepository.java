package com.turnoshift.identity.infrastructure.persistence;

import com.turnoshift.identity.domain.AuthenticationDataAccessException;
import com.turnoshift.identity.domain.SessionRevalidationCandidate;
import com.turnoshift.identity.domain.SessionRevalidationRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para la revalidación de sesión por solicitud.
 *
 * <p>Ejecuta una consulta SQL parametrizada que combina {@code app_users},
 * {@code organizations} y el catálogo global {@code roles}.
 *
 * <p><b>Aislamiento multi-tenant:</b> se filtra estrictamente por {@code u.id = ? AND u.organization_id = ?},
 * garantizando que la consulta verifique la correspondencia de la organización derivada del SecurityContext.
 */
@Repository
public class JdbcSessionRevalidationRepository implements SessionRevalidationRepository {

    private static final String SELECT_REVALIDATION_SQL = """
            SELECT
                u.id AS user_id,
                u.organization_id,
                u.is_active,
                u.auth_version,
                u.role_code,
                u.display_name AS user_display_name,
                u.employee_id,
                o.service_status AS org_service_status,
                o.display_name AS org_display_name,
                o.timezone_id AS org_timezone_id
            FROM app_users u
            JOIN organizations o ON u.organization_id = o.id
            JOIN roles r ON u.role_code = r.code
            WHERE u.id = ?
              AND u.organization_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RevalidationRowMapper rowMapper = new RevalidationRowMapper();

    public JdbcSessionRevalidationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public Optional<SessionRevalidationCandidate> findCandidate(UUID userId, UUID organizationId) {
        if (userId == null || organizationId == null) {
            return Optional.empty();
        }

        List<SessionRevalidationCandidate> results;
        try {
            results = jdbcTemplate.query(
                    SELECT_REVALIDATION_SQL,
                    rowMapper,
                    userId,
                    organizationId
            );
        } catch (DataAccessException e) {
            throw new AuthenticationDataAccessException(
                    "El servicio de base de datos no está disponible temporalmente", e);
        }

        if (results.size() == 1) {
            return Optional.ofNullable(results.get(0));
        }

        return Optional.empty();
    }

    private static class RevalidationRowMapper implements RowMapper<SessionRevalidationCandidate> {

        @Override
        public SessionRevalidationCandidate mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                UUID userId = rs.getObject("user_id", UUID.class);
                UUID organizationId = rs.getObject("organization_id", UUID.class);
                boolean isActive = rs.getBoolean("is_active");
                long authVersion = rs.getLong("auth_version");
                String roleCode = rs.getString("role_code");
                String displayName = rs.getString("user_display_name");
                UUID employeeId = rs.getObject("employee_id", UUID.class);
                String orgStatus = rs.getString("org_service_status");
                String orgDisplayName = rs.getString("org_display_name");
                String orgTimeZone = rs.getString("org_timezone_id");

                if (userId == null || organizationId == null || roleCode == null
                        || displayName == null || orgStatus == null
                        || orgDisplayName == null || orgTimeZone == null) {
                    return null;
                }

                return new SessionRevalidationCandidate(
                        userId,
                        organizationId,
                        isActive,
                        authVersion,
                        roleCode,
                        displayName,
                        employeeId,
                        orgStatus,
                        orgDisplayName,
                        orgTimeZone
                );
            } catch (Exception e) {
                // Fail-closed ante cualquier error de conversión o UUID corrupto
                return null;
            }
        }
    }
}
