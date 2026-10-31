package com.turnoshift.operator.infrastructure.persistence;

import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.PlatformOperator;
import com.turnoshift.operator.domain.PlatformOperatorRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para la persistencia de operadores de plataforma en SQL Server.
 */
@Repository
public class JdbcPlatformOperatorRepository implements PlatformOperatorRepository {

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, email, normalized_email, display_name, password_hash,
                   is_active, auth_version, lock_version, created_at, updated_at
            FROM platform_operators
            WHERE id = ?
            """;

    private static final String SELECT_BY_NORMALIZED_EMAIL_SQL = """
            SELECT id, email, normalized_email, display_name, password_hash,
                   is_active, auth_version, lock_version, created_at, updated_at
            FROM platform_operators
            WHERE normalized_email = ?
            """;

    private static final String INSERT_SQL = """
            INSERT INTO platform_operators (
                id, email, normalized_email, display_name, password_hash,
                is_active, auth_version, lock_version, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_AUTH_VERSION_SQL = """
            UPDATE platform_operators
            SET auth_version = ?, updated_at = ?
            WHERE id = ? AND auth_version = ?
            """;

    private static final String UPDATE_ACTIVE_STATUS_SQL = """
            UPDATE platform_operators
            SET is_active = ?, updated_at = ?
            WHERE id = ?
            """;

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM platform_operators";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<PlatformOperator> rowMapper = new PlatformOperatorRowMapper();

    public JdbcPlatformOperatorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    RowMapper<PlatformOperator> getRowMapper() {
        return rowMapper;
    }

    @Override
    public Optional<PlatformOperator> findById(UUID id) {
        Objects.requireNonNull(id, "id no puede ser nulo");
        try {
            List<PlatformOperator> results = jdbcTemplate.query(SELECT_BY_ID_SQL, rowMapper, id);
            if (results.size() == 1) {
                return Optional.of(results.get(0));
            }
            return Optional.empty();
        } catch (DataAccessException e) {
            throw new OperatorDataAccessException(e);
        }
    }

    @Override
    public Optional<PlatformOperator> findByNormalizedEmail(String normalizedEmail) {
        Objects.requireNonNull(normalizedEmail, "normalizedEmail no puede ser nulo");
        try {
            List<PlatformOperator> results = jdbcTemplate.query(SELECT_BY_NORMALIZED_EMAIL_SQL, rowMapper, normalizedEmail);
            if (results.size() == 1) {
                return Optional.of(results.get(0));
            }
            return Optional.empty();
        } catch (DataAccessException e) {
            throw new OperatorDataAccessException(e);
        }
    }

    @Override
    public PlatformOperator save(PlatformOperator operator) {
        Objects.requireNonNull(operator, "operator no puede ser nulo");
        try {
            jdbcTemplate.update(
                    INSERT_SQL,
                    operator.id(),
                    operator.email(),
                    operator.normalizedEmail(),
                    operator.displayName(),
                    operator.passwordHash(),
                    operator.isActive(),
                    operator.authVersion(),
                    operator.lockVersion(),
                    operator.createdAt(),
                    operator.updatedAt()
            );
            return operator;
        } catch (DataAccessException e) {
            throw new OperatorDataAccessException(e);
        }
    }

    @Override
    public boolean updateAuthVersion(UUID id, long expectedAuthVersion, long newAuthVersion) {
        Objects.requireNonNull(id, "id no puede ser nulo");
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            int rows = jdbcTemplate.update(UPDATE_AUTH_VERSION_SQL, newAuthVersion, now, id, expectedAuthVersion);
            return rows == 1;
        } catch (DataAccessException e) {
            throw new OperatorDataAccessException(e);
        }
    }

    @Override
    public boolean updateActiveStatus(UUID id, boolean isActive) {
        Objects.requireNonNull(id, "id no puede ser nulo");
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            int rows = jdbcTemplate.update(UPDATE_ACTIVE_STATUS_SQL, isActive, now, id);
            return rows == 1;
        } catch (DataAccessException e) {
            throw new OperatorDataAccessException(e);
        }
    }

    @Override
    public long count() {
        try {
            Long c = jdbcTemplate.queryForObject(COUNT_SQL, Long.class);
            return c != null ? c : 0L;
        } catch (DataAccessException e) {
            throw new OperatorDataAccessException(e);
        }
    }

    private static class PlatformOperatorRowMapper implements RowMapper<PlatformOperator> {
        @Override
        public PlatformOperator mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID id = rs.getObject("id", UUID.class);
            String email = rs.getString("email");
            String normalizedEmail = rs.getString("normalized_email");
            String displayName = rs.getString("display_name");
            String passwordHash = rs.getString("password_hash");
            boolean isActive = rs.getBoolean("is_active");
            long authVersion = rs.getLong("auth_version");
            long lockVersion = rs.getLong("lock_version");
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);

            if (id == null || email == null || normalizedEmail == null || displayName == null
                    || passwordHash == null || createdAt == null || updatedAt == null) {
                throw new SQLException("Fila de operador de plataforma incompleta en base de datos");
            }

            return new PlatformOperator(
                    id,
                    email,
                    normalizedEmail,
                    displayName,
                    passwordHash,
                    isActive,
                    authVersion,
                    lockVersion,
                    createdAt,
                    updatedAt
            );
        }
    }
}
