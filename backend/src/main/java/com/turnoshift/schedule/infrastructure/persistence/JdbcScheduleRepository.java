package com.turnoshift.schedule.infrastructure.persistence;

import com.turnoshift.schedule.domain.Schedule;
import com.turnoshift.schedule.domain.ScheduleConcurrentModificationException;
import com.turnoshift.schedule.domain.ScheduleDataAccessException;
import com.turnoshift.schedule.domain.ScheduleDomainException;
import com.turnoshift.schedule.domain.ports.ScheduleRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para el agregado Schedule en SQL Server 2022.
 */
@Repository
public class JdbcScheduleRepository implements ScheduleRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    private static final RowMapper<Schedule> ROW_MAPPER = (rs, rowNum) -> {
        UUID id = toUuid(rs.getString("id"));
        UUID orgId = toUuid(rs.getString("organization_id"));
        UUID siteId = toUuid(rs.getString("site_id"));
        LocalDate weekStart = rs.getDate("week_start").toLocalDate();
        UUID currentPubId = toUuid(rs.getString("current_publication_id"));
        long lockVersion = rs.getLong("lock_version");
        Instant createdAt = toInstant(rs, "created_at");
        Instant updatedAt = toInstant(rs, "updated_at");

        return new Schedule(id, orgId, siteId, weekStart, currentPubId, lockVersion, createdAt, updatedAt);
    };

    @Override
    public Optional<Schedule> findBySiteAndWeek(UUID organizationId, UUID siteId, LocalDate weekStart) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(siteId, "siteId no puede ser nulo");
        Objects.requireNonNull(weekStart, "weekStart no puede ser nulo");

        String sql = """
                SELECT id, organization_id, site_id, week_start, current_publication_id,
                       lock_version, created_at, updated_at
                FROM schedules
                WHERE organization_id = ? AND site_id = ? AND week_start = ?;
                """;
        try {
            List<Schedule> list = jdbcTemplate.query(sql, ROW_MAPPER, organizationId, siteId, java.sql.Date.valueOf(weekStart));
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar schedule por sede y semana", e);
        }
    }

    @Override
    public Optional<Schedule> findById(UUID organizationId, UUID scheduleId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");

        String sql = """
                SELECT id, organization_id, site_id, week_start, current_publication_id,
                       lock_version, created_at, updated_at
                FROM schedules
                WHERE organization_id = ? AND id = ?;
                """;
        try {
            List<Schedule> list = jdbcTemplate.query(sql, ROW_MAPPER, organizationId, scheduleId);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar schedule por ID", e);
        }
    }

    @Override
    public Schedule createSchedule(Schedule schedule) {
        Objects.requireNonNull(schedule, "schedule no puede ser nulo");

        String sql = """
                SET NOCOUNT ON;
                INSERT INTO schedules (
                    id, organization_id, site_id, week_start, current_publication_id,
                    lock_version, created_at, updated_at
                )
                OUTPUT inserted.id, inserted.organization_id, inserted.site_id,
                       inserted.week_start, inserted.current_publication_id,
                       inserted.lock_version, inserted.created_at, inserted.updated_at
                VALUES (?, ?, ?, ?, ?, ?, SYSUTCDATETIME(), SYSUTCDATETIME());
                """;

        try {
            List<Schedule> list = jdbcTemplate.query(
                    sql,
                    ROW_MAPPER,
                    schedule.id(),
                    schedule.organizationId(),
                    schedule.siteId(),
                    java.sql.Date.valueOf(schedule.weekStart()),
                    schedule.currentPublicationId(),
                    schedule.lockVersion()
            );
            if (list.isEmpty()) {
                throw new ScheduleDataAccessException("No se pudo obtener el schedule creado");
            }
            return list.get(0);
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al crear schedule", e);
        }
    }

    @Override
    public void updateCurrentPublication(UUID organizationId, UUID scheduleId, UUID currentPublicationId, long expectedLockVersion) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");

        String sql = """
                UPDATE schedules
                SET current_publication_id = ?,
                    lock_version = lock_version + 1,
                    updated_at = SYSUTCDATETIME()
                WHERE organization_id = ? AND id = ? AND lock_version = ?;
                """;
        try {
            int rows = jdbcTemplate.update(sql, currentPublicationId, organizationId, scheduleId, expectedLockVersion);
            if (rows == 0) {
                // Verificar si existe para distinguir concurrencia de inexistencia
                Optional<Schedule> existing = findById(organizationId, scheduleId);
                if (existing.isPresent()) {
                    throw new ScheduleConcurrentModificationException(
                            String.format("Conflicto de concurrencia al actualizar schedule %s: lock_version esperada %d, actual %d",
                                    scheduleId, expectedLockVersion, existing.get().lockVersion())
                    );
                } else {
                    throw new ScheduleDomainException("Schedule no encontrado: " + scheduleId);
                }
            }
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al actualizar publicación vigente de schedule", e);
        }
    }

    private static UUID toUuid(String str) {
        return str != null ? UUID.fromString(str) : null;
    }

    private static Instant toInstant(ResultSet rs, String columnLabel) throws SQLException {
        Object obj = rs.getObject(columnLabel);
        if (obj == null) {
            return null;
        }
        if (obj instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (obj instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        try {
            OffsetDateTime odt = rs.getObject(columnLabel, OffsetDateTime.class);
            if (odt != null) {
                return odt.toInstant();
            }
        } catch (Exception ignored) {
        }
        java.sql.Timestamp ts = rs.getTimestamp(columnLabel);
        return ts != null ? ts.toInstant() : null;
    }
}
