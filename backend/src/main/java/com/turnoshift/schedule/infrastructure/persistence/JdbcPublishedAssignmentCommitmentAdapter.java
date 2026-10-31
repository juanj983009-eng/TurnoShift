package com.turnoshift.schedule.infrastructure.persistence;

import com.turnoshift.schedule.domain.PublishedShiftRecord;
import com.turnoshift.schedule.domain.ScheduleDataAccessException;
import com.turnoshift.schedule.domain.ports.PublishedAssignmentCommitmentQueryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador JDBC para consultar compromisos de planificación vigentes (publicaciones oficiales).
 *
 * <p>Asegura aislamiento multiempresa estricto mediante {@code organization_id}, consulta exclusivamente
 * publicaciones vigentes vinculadas a {@code schedules.current_publication_id}, no consulta borradores
 * y no expone notas confidenciales ni información ajena al compromiso operativo.
 */
@Repository
public class JdbcPublishedAssignmentCommitmentAdapter implements PublishedAssignmentCommitmentQueryPort {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public JdbcPublishedAssignmentCommitmentAdapter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    public JdbcPublishedAssignmentCommitmentAdapter(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    @Override
    public boolean hasFuturePublishedAssignmentsForEmployee(UUID organizationId, UUID employeeId, Instant from) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(from, "from no puede ser nulo");

        String sql = """
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM publication_assignments pa
                    JOIN schedule_publications p
                      ON pa.organization_id = p.organization_id AND pa.publication_id = p.id
                    JOIN schedules s
                      ON p.organization_id = s.organization_id AND p.schedule_id = s.id
                    JOIN assignment_revisions ar
                      ON pa.organization_id = ar.organization_id AND pa.assignment_revision_id = ar.id
                    WHERE s.organization_id = ?
                      AND s.current_publication_id = p.id
                      AND ar.employee_id = ?
                      AND ar.ends_at > ?
                ) THEN 1 ELSE 0 END;
                """;
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    sql,
                    Integer.class,
                    organizationId,
                    employeeId,
                    OffsetDateTime.ofInstant(from, ZoneOffset.UTC)
            );
            return exists != null && exists == 1;
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException(
                    "Error al consultar compromisos futuros de colaborador en publicaciones vigentes", e
            );
        }
    }

    @Override
    public boolean hasFuturePublishedAssignmentsForEmployeeAndPosition(
            UUID organizationId, UUID employeeId, UUID positionId, Instant from) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(positionId, "positionId no puede ser nulo");
        Objects.requireNonNull(from, "from no puede ser nulo");

        String sql = """
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM publication_assignments pa
                    JOIN schedule_publications p
                      ON pa.organization_id = p.organization_id AND pa.publication_id = p.id
                    JOIN schedules s
                      ON p.organization_id = s.organization_id AND p.schedule_id = s.id
                    JOIN assignment_revisions ar
                      ON pa.organization_id = ar.organization_id AND pa.assignment_revision_id = ar.id
                    WHERE s.organization_id = ?
                      AND s.current_publication_id = p.id
                      AND ar.employee_id = ?
                      AND ar.position_id = ?
                      AND ar.starts_at > ?
                ) THEN 1 ELSE 0 END;
                """;
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    sql,
                    Integer.class,
                    organizationId,
                    employeeId,
                    positionId,
                    OffsetDateTime.ofInstant(from, ZoneOffset.UTC)
            );
            return exists != null && exists == 1;
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException(
                    "Error al consultar compromisos futuros de colaborador y puesto en publicaciones vigentes", e
            );
        }
    }

    @Override
    public List<PublishedShiftRecord> findFuturePublishedAssignmentsOverlapping(
            UUID organizationId, UUID employeeId, Instant startsAt, Instant endsAt) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(startsAt, "startsAt no puede ser nulo");
        Objects.requireNonNull(endsAt, "endsAt no puede ser nulo");
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt debe ser anterior a endsAt");
        }

        String sql = """
                SELECT pa.assignment_id,
                       ar.id AS revision_id,
                       ar.employee_id,
                       ar.position_id,
                       ar.starts_at,
                       ar.ends_at,
                       ar.employee_name_snapshot,
                       ar.position_name_snapshot,
                       s.id AS schedule_id,
                       s.week_start
                FROM publication_assignments pa
                JOIN schedule_publications p
                  ON pa.organization_id = p.organization_id AND pa.publication_id = p.id
                JOIN schedules s
                  ON p.organization_id = s.organization_id AND p.schedule_id = s.id
                JOIN assignment_revisions ar
                  ON pa.organization_id = ar.organization_id AND pa.assignment_revision_id = ar.id
                WHERE s.organization_id = ?
                  AND s.current_publication_id = p.id
                  AND ar.employee_id = ?
                  AND ar.starts_at < ?
                  AND ar.ends_at > ?
                  AND ar.ends_at > ?
                ORDER BY ar.starts_at ASC;
                """;
        try {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> mapPublishedShiftRecord(rs),
                    organizationId,
                    employeeId,
                    OffsetDateTime.ofInstant(endsAt, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            );
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException(
                    "Error al consultar turnos publicados vigentes solapados para el colaborador", e
            );
        }
    }

    private static PublishedShiftRecord mapPublishedShiftRecord(ResultSet rs) throws SQLException {
        return new PublishedShiftRecord(
                toUuid(rs.getString("assignment_id")),
                toUuid(rs.getString("revision_id")),
                toUuid(rs.getString("employee_id")),
                toUuid(rs.getString("position_id")),
                toInstant(rs, "starts_at"),
                toInstant(rs, "ends_at"),
                rs.getString("employee_name_snapshot"),
                rs.getString("position_name_snapshot"),
                toUuid(rs.getString("schedule_id")),
                rs.getDate("week_start").toLocalDate()
        );
    }

    private static UUID toUuid(String val) {
        return val != null ? UUID.fromString(val) : null;
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
