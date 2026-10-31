package com.turnoshift.schedule.infrastructure.persistence;

import com.turnoshift.schedule.domain.PublishedShiftRecord;
import com.turnoshift.schedule.domain.ScheduleDataAccessException;
import com.turnoshift.schedule.domain.ports.PlanningContextSnapshotPort;
import com.turnoshift.schedule.domain.validation.BasePublicationSnapshot;
import com.turnoshift.schedule.domain.validation.EmployeeSnapshot;
import com.turnoshift.schedule.domain.validation.FrontierShiftsSnapshot;
import com.turnoshift.schedule.domain.validation.PolicyVersionSnapshot;
import com.turnoshift.schedule.domain.validation.PositionSnapshot;
import com.turnoshift.schedule.domain.validation.UnavailabilitySnapshot;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptador JDBC para obtener las instantáneas del contexto operativo (políticas, empleados, puestos,
 * indisponibilidades y turnos publicados adyacentes) requeridas por el motor de validación.
 */
@Component
public class JdbcPlanningContextSnapshotAdapter implements PlanningContextSnapshotPort {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public JdbcPlanningContextSnapshotAdapter(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = Objects.requireNonNull(namedJdbcTemplate, "namedJdbcTemplate no puede ser nulo");
    }

    @Override
    public Optional<PolicyVersionSnapshot> findActivePolicySnapshot(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");

        String sql = """
                SELECT p.id, p.version_number, p.minimum_rest_minutes, p.weekly_warning_minutes
                FROM planning_policy_versions p
                JOIN organizations o
                  ON o.id = p.organization_id AND o.current_policy_version_id = p.id
                WHERE o.id = :orgId;
                """;
        try {
            List<PolicyVersionSnapshot> list = namedJdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource("orgId", organizationId),
                    (rs, rn) -> new PolicyVersionSnapshot(
                            toUuid(rs.getString("id")),
                            rs.getInt("version_number"),
                            rs.getInt("minimum_rest_minutes"),
                            rs.getInt("weekly_warning_minutes")
                    )
            );
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar política activa para validación", e);
        }
    }

    @Override
    public Map<UUID, EmployeeSnapshot> findEmployeeSnapshots(UUID organizationId, Set<UUID> employeeIds) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = """
                SELECT id, display_name, position_id, is_active
                FROM employees
                WHERE organization_id = :orgId AND id IN (:empIds);
                """;
        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("orgId", organizationId)
                    .addValue("empIds", employeeIds);

            Map<UUID, EmployeeSnapshot> map = new HashMap<>();
            namedJdbcTemplate.query(sql, params, rs -> {
                UUID id = toUuid(rs.getString("id"));
                String displayName = rs.getString("display_name");
                UUID positionId = toUuid(rs.getString("position_id"));
                boolean isActive = rs.getBoolean("is_active");
                map.put(id, new EmployeeSnapshot(id, displayName, positionId, isActive));
            });
            return Collections.unmodifiableMap(map);
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar instantáneas de empleados", e);
        }
    }

    @Override
    public Map<UUID, PositionSnapshot> findPositionSnapshots(UUID organizationId, Set<UUID> positionIds) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        if (positionIds == null || positionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = """
                SELECT id, name, is_active
                FROM positions
                WHERE organization_id = :orgId AND id IN (:posIds);
                """;
        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("orgId", organizationId)
                    .addValue("posIds", positionIds);

            Map<UUID, PositionSnapshot> map = new HashMap<>();
            namedJdbcTemplate.query(sql, params, rs -> {
                UUID id = toUuid(rs.getString("id"));
                String name = rs.getString("name");
                boolean isActive = rs.getBoolean("is_active");
                map.put(id, new PositionSnapshot(id, name, isActive));
            });
            return Collections.unmodifiableMap(map);
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar instantáneas de puestos", e);
        }
    }

    @Override
    public List<UnavailabilitySnapshot> findActiveUnavailabilities(
            UUID organizationId,
            Set<UUID> employeeIds,
            Instant windowStart,
            Instant windowEnd
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(windowStart, "windowStart no puede ser nulo");
        Objects.requireNonNull(windowEnd, "windowEnd no puede ser nulo");
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Collections.emptyList();
        }

        String sql = """
                SELECT id, employee_id, starts_at, ends_at, cancelled_at
                FROM employee_unavailabilities
                WHERE organization_id = :orgId
                  AND employee_id IN (:empIds)
                  AND cancelled_at IS NULL
                  AND starts_at < :windowEnd
                  AND ends_at > :windowStart;
                """;
        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("orgId", organizationId)
                    .addValue("empIds", employeeIds)
                    .addValue("windowStart", OffsetDateTime.ofInstant(windowStart, ZoneOffset.UTC))
                    .addValue("windowEnd", OffsetDateTime.ofInstant(windowEnd, ZoneOffset.UTC));

            return namedJdbcTemplate.query(
                    sql,
                    params,
                    (rs, rn) -> new UnavailabilitySnapshot(
                            toUuid(rs.getString("id")),
                            toUuid(rs.getString("employee_id")),
                            toInstant(rs, "starts_at"),
                            toInstant(rs, "ends_at"),
                            rs.getObject("cancelled_at") != null
                    )
            );
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar indisponibilidades activas", e);
        }
    }

    @Override
    public FrontierShiftsSnapshot findFrontierShifts(
            UUID organizationId,
            UUID siteId,
            LocalDate weekStart,
            ZoneId orgZone,
            int minRestMinutes
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(siteId, "siteId no puede ser nulo");
        Objects.requireNonNull(weekStart, "weekStart no puede ser nulo");
        Objects.requireNonNull(orgZone, "orgZone no puede ser nulo");

        Instant weekStartInstant = weekStart.atStartOfDay(orgZone).toInstant();
        Instant weekEndInstant = weekStart.plusDays(7).atStartOfDay(orgZone).toInstant();

        // Ventana W-1: turnos que terminan en o después de (weekStart - minRest) y comenzaron antes de weekStart
        Instant wMinusStart = weekStartInstant.minus(Duration.ofMinutes(minRestMinutes));

        // Ventana W+1: turnos que empiezan en o después de weekEnd y antes de (weekEnd + minRest + 960m)
        Instant wPlusEnd = weekEndInstant.plus(Duration.ofMinutes(minRestMinutes + 960L));

        String sql = """
                SELECT a.id AS assignment_id, ar.id AS revision_id, ar.employee_id, ar.position_id,
                       ar.starts_at, ar.ends_at, ar.employee_name_snapshot, ar.position_name_snapshot,
                       s.id AS schedule_id, s.week_start
                FROM publication_assignments pa
                JOIN schedule_publications p
                  ON pa.organization_id = p.organization_id AND pa.publication_id = p.id
                JOIN schedules s
                  ON p.organization_id = s.organization_id AND p.schedule_id = s.id
                JOIN assignments a
                  ON pa.organization_id = a.organization_id AND pa.assignment_id = a.id
                JOIN assignment_revisions ar
                  ON pa.organization_id = ar.organization_id AND pa.assignment_revision_id = ar.id
                WHERE s.organization_id = :orgId
                  AND s.site_id = :siteId
                  AND s.current_publication_id = p.id
                  AND ar.starts_at < :windowEnd
                  AND ar.ends_at > :windowStart
                ORDER BY ar.starts_at;
                """;

        try {
            // Turnos W-1
            MapSqlParameterSource paramsWPrev = new MapSqlParameterSource()
                    .addValue("orgId", organizationId)
                    .addValue("siteId", siteId)
                    .addValue("windowStart", OffsetDateTime.ofInstant(wMinusStart, ZoneOffset.UTC))
                    .addValue("windowEnd", OffsetDateTime.ofInstant(weekStartInstant, ZoneOffset.UTC));

            List<PublishedShiftRecord> prevList = namedJdbcTemplate.query(sql, paramsWPrev, (rs, rn) -> mapShiftRecord(rs));

            // Turnos W+1
            MapSqlParameterSource paramsWNext = new MapSqlParameterSource()
                    .addValue("orgId", organizationId)
                    .addValue("siteId", siteId)
                    .addValue("windowStart", OffsetDateTime.ofInstant(weekEndInstant, ZoneOffset.UTC))
                    .addValue("windowEnd", OffsetDateTime.ofInstant(wPlusEnd, ZoneOffset.UTC));

            List<PublishedShiftRecord> nextList = namedJdbcTemplate.query(sql, paramsWNext, (rs, rn) -> mapShiftRecord(rs));

            return new FrontierShiftsSnapshot(prevList, nextList);
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar turnos de frontera", e);
        }
    }

    @Override
    public Optional<BasePublicationSnapshot> findBasePublicationSnapshot(UUID organizationId, UUID publicationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(publicationId, "publicationId no puede ser nulo");

        String sql = """
                SELECT a.id AS assignment_id, ar.id AS revision_id, ar.employee_id, ar.position_id,
                       ar.starts_at, ar.ends_at, ar.employee_name_snapshot, ar.position_name_snapshot,
                       s.id AS schedule_id, s.week_start
                FROM publication_assignments pa
                JOIN schedule_publications p
                  ON pa.organization_id = p.organization_id AND pa.publication_id = p.id
                JOIN schedules s
                  ON p.organization_id = s.organization_id AND p.schedule_id = s.id
                JOIN assignments a
                  ON pa.organization_id = a.organization_id AND pa.assignment_id = a.id
                JOIN assignment_revisions ar
                  ON pa.organization_id = ar.organization_id AND pa.assignment_revision_id = ar.id
                WHERE p.organization_id = :orgId AND p.id = :pubId
                ORDER BY ar.starts_at;
                """;

        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("orgId", organizationId)
                    .addValue("pubId", publicationId);

            List<PublishedShiftRecord> list = namedJdbcTemplate.query(sql, params, (rs, rn) -> mapShiftRecord(rs));

            // Si la lista es vacía, verificar si la publicación existe (caso publicación vacía DEC-03)
            if (list.isEmpty()) {
                String checkSql = "SELECT COUNT(1) FROM schedule_publications WHERE organization_id = :orgId AND id = :pubId;";
                Integer count = namedJdbcTemplate.queryForObject(checkSql, params, Integer.class);
                if (count != null && count > 0) {
                    return Optional.of(new BasePublicationSnapshot(publicationId, List.of()));
                }
                return Optional.empty();
            }

            return Optional.of(new BasePublicationSnapshot(publicationId, list));
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar instantánea de publicación base", e);
        }
    }

    private static PublishedShiftRecord mapShiftRecord(ResultSet rs) throws SQLException {
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
