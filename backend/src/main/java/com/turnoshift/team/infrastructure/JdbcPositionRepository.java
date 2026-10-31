package com.turnoshift.team.infrastructure;

import com.turnoshift.team.domain.Position;
import com.turnoshift.team.domain.PositionCodeAlreadyExistsException;
import com.turnoshift.team.domain.PositionRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para la persistencia de puestos de trabajo en SQL Server.
 * Garantiza multi-tenancy estricto mediante filtrado obligatorio por organization_id,
 * control de concurrencia optimista vía lock_version y timestamps autoritativos con SYSUTCDATETIME().
 */
@Repository
public class JdbcPositionRepository implements PositionRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Position> POSITION_ROW_MAPPER = (rs, rowNum) -> {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID orgId = UUID.fromString(rs.getString("organization_id"));
        String code = rs.getString("code");
        String name = rs.getString("name");
        boolean isActive = rs.getBoolean("is_active");
        long lockVersion = rs.getLong("lock_version");
        Instant createdAt = rs.getObject("created_at", OffsetDateTime.class).toInstant();
        Instant updatedAt = rs.getObject("updated_at", OffsetDateTime.class).toInstant();

        return Position.reconstruct(id, orgId, code, name, isActive, lockVersion, createdAt, updatedAt);
    };

    public JdbcPositionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public Optional<Position> findById(UUID organizationId, UUID positionId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(positionId, "positionId no puede ser nulo");

        String sql = """
                SELECT id, organization_id, code, name, is_active, lock_version, created_at, updated_at
                FROM positions
                WHERE organization_id = ? AND id = ?
                """;
        try {
            List<Position> results = jdbcTemplate.query(sql, POSITION_ROW_MAPPER, organizationId, positionId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new PositionDataAccessException(e);
        }
    }

    @Override
    public Optional<Position> findByCode(UUID organizationId, String normalizedCode) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(normalizedCode, "normalizedCode no puede ser nulo");

        String sql = """
                SELECT id, organization_id, code, name, is_active, lock_version, created_at, updated_at
                FROM positions
                WHERE organization_id = ? AND code = ?
                """;
        try {
            List<Position> results = jdbcTemplate.query(sql, POSITION_ROW_MAPPER, organizationId, normalizedCode);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new PositionDataAccessException(e);
        }
    }

    @Override
    public List<Position> findAll(UUID organizationId, Boolean activeFilter) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");

        try {
            if (activeFilter == null) {
                String sql = """
                        SELECT id, organization_id, code, name, is_active, lock_version, created_at, updated_at
                        FROM positions
                        WHERE organization_id = ?
                        ORDER BY code ASC
                        """;
                return jdbcTemplate.query(sql, POSITION_ROW_MAPPER, organizationId);
            } else {
                String sql = """
                        SELECT id, organization_id, code, name, is_active, lock_version, created_at, updated_at
                        FROM positions
                        WHERE organization_id = ? AND is_active = ?
                        ORDER BY code ASC
                        """;
                return jdbcTemplate.query(sql, POSITION_ROW_MAPPER, organizationId, activeFilter);
            }
        } catch (DataAccessException e) {
            throw new PositionDataAccessException(e);
        }
    }

    @Override
    public void insert(Position position) {
        Objects.requireNonNull(position, "position no puede ser nulo");

        String sql = """
                INSERT INTO positions (
                    id, organization_id, code, name, is_active, lock_version, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?
                )
                """;
        try {
            jdbcTemplate.update(
                    sql,
                    position.id(),
                    position.organizationId(),
                    position.code(),
                    position.name(),
                    position.isActive(),
                    position.lockVersion(),
                    OffsetDateTime.ofInstant(position.createdAt(), ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(position.updatedAt(), ZoneOffset.UTC)
            );
        } catch (DataAccessException e) {
            if (isUniqueCodeViolation(e)) {
                throw new PositionCodeAlreadyExistsException(position.code());
            }
            throw new PositionDataAccessException(e);
        }
    }

    @Override
    public Optional<Position> update(Position updatedPosition, long expectedLockVersion) {
        Objects.requireNonNull(updatedPosition, "updatedPosition no puede ser nulo");

        String sql = """
                SET NOCOUNT ON;
                UPDATE positions
                SET name = ?,
                    is_active = ?,
                    lock_version = ?,
                    updated_at = SYSUTCDATETIME()
                OUTPUT inserted.id,
                       inserted.organization_id,
                       inserted.code,
                       inserted.name,
                       inserted.is_active,
                       inserted.lock_version,
                       inserted.created_at,
                       inserted.updated_at
                WHERE organization_id = ?
                  AND id = ?
                  AND lock_version = ?;
                """;
        try {
            List<Position> results = jdbcTemplate.query(
                    sql,
                    POSITION_ROW_MAPPER,
                    updatedPosition.name(),
                    updatedPosition.isActive(),
                    updatedPosition.lockVersion(),
                    updatedPosition.organizationId(),
                    updatedPosition.id(),
                    expectedLockVersion
            );
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new PositionDataAccessException(e);
        }
    }

    private boolean isUniqueCodeViolation(DataAccessException e) {
        Throwable current = e;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("uq_positions_org_code")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
