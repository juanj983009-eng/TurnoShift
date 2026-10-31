package com.turnoshift.unavailability.infrastructure.persistence;

import com.turnoshift.unavailability.domain.CancellationInfo;
import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.RestrictedNote;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcEmployeeUnavailabilityRepository implements EmployeeUnavailabilityRepository {

    private final JdbcTemplate jdbc;

    public JdbcEmployeeUnavailabilityRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc no puede ser nulo");
    }

    private static final String INSERT_SQL = """
            SET NOCOUNT ON;
            INSERT INTO employee_unavailabilities (
                id, organization_id, employee_id, starts_at, ends_at,
                reason_code, restricted_note, created_by_user_id,
                created_at, updated_at, lock_version
            )
            OUTPUT inserted.id, inserted.organization_id, inserted.employee_id,
                   inserted.starts_at, inserted.ends_at, inserted.reason_code,
                   inserted.restricted_note, inserted.created_by_user_id,
                   inserted.created_at, inserted.updated_at, inserted.lock_version,
                   inserted.cancelled_at, inserted.cancelled_by_user_id
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME(), SYSUTCDATETIME(), 0);
            """;

    private static final String UPDATE_SQL = """
            SET NOCOUNT ON;
            UPDATE employee_unavailabilities
            SET starts_at = ?,
                ends_at = ?,
                reason_code = ?,
                restricted_note = ?,
                lock_version = lock_version + 1,
                updated_at = SYSUTCDATETIME()
            OUTPUT inserted.id, inserted.organization_id, inserted.employee_id,
                   inserted.starts_at, inserted.ends_at, inserted.reason_code,
                   inserted.restricted_note, inserted.created_by_user_id,
                   inserted.created_at, inserted.updated_at, inserted.lock_version,
                   inserted.cancelled_at, inserted.cancelled_by_user_id
            WHERE organization_id = ?
              AND id = ?
              AND lock_version = ?
              AND cancelled_at IS NULL
              AND starts_at > SYSUTCDATETIME();
            """;

    private static final String CANCEL_SQL = """
            SET NOCOUNT ON;
            UPDATE employee_unavailabilities
            SET cancelled_at = SYSUTCDATETIME(),
                cancelled_by_user_id = ?,
                lock_version = lock_version + 1,
                updated_at = SYSUTCDATETIME()
            OUTPUT inserted.id, inserted.organization_id, inserted.employee_id,
                   inserted.starts_at, inserted.ends_at, inserted.reason_code,
                   inserted.restricted_note, inserted.created_by_user_id,
                   inserted.created_at, inserted.updated_at, inserted.lock_version,
                   inserted.cancelled_at, inserted.cancelled_by_user_id
            WHERE organization_id = ?
              AND id = ?
              AND lock_version = ?
              AND cancelled_at IS NULL
              AND starts_at > SYSUTCDATETIME();
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT id, organization_id, employee_id, starts_at, ends_at,
                   reason_code, restricted_note, created_by_user_id,
                   created_at, updated_at, lock_version,
                   cancelled_at, cancelled_by_user_id
            FROM employee_unavailabilities
            WHERE organization_id = ? AND id = ?;
            """;

    private static final String FIND_BY_EMPLOYEE_SQL = """
            SELECT id, organization_id, employee_id, starts_at, ends_at,
                   reason_code, restricted_note, created_by_user_id,
                   created_at, updated_at, lock_version,
                   cancelled_at, cancelled_by_user_id
            FROM employee_unavailabilities
            WHERE organization_id = ? AND employee_id = ?
              AND (? = 1 OR cancelled_at IS NULL)
            ORDER BY starts_at ASC;
            """;

    private static final String FIND_BY_EMPLOYEE_AND_INTERVAL_SQL = """
            SELECT id, organization_id, employee_id, starts_at, ends_at,
                   reason_code, restricted_note, created_by_user_id,
                   created_at, updated_at, lock_version,
                   cancelled_at, cancelled_by_user_id
            FROM employee_unavailabilities
            WHERE organization_id = ? AND employee_id = ?
              AND starts_at < ? AND ends_at > ?
              AND (? = 1 OR cancelled_at IS NULL)
            ORDER BY starts_at ASC;
            """;

    private static final String FIND_BY_ORG_AND_INTERVAL_SQL = """
            SELECT id, organization_id, employee_id, starts_at, ends_at,
                   reason_code, restricted_note, created_by_user_id,
                   created_at, updated_at, lock_version,
                   cancelled_at, cancelled_by_user_id
            FROM employee_unavailabilities
            WHERE organization_id = ?
              AND starts_at < ? AND ends_at > ?
              AND (? = 1 OR cancelled_at IS NULL)
            ORDER BY starts_at ASC;
            """;

    private final RowMapper<EmployeeUnavailability> rowMapper = (rs, rowNum) -> {
        UUID id = toUuid(rs.getString("id"));
        UUID orgId = toUuid(rs.getString("organization_id"));
        UUID empId = toUuid(rs.getString("employee_id"));
        Instant startsAt = toInstant(rs, "starts_at");
        Instant endsAt = toInstant(rs, "ends_at");
        String reasonCode = rs.getString("reason_code");
        String noteStr = rs.getString("restricted_note");
        UUID createdBy = toUuid(rs.getString("created_by_user_id"));
        Instant createdAt = toInstant(rs, "created_at");
        Instant updatedAt = toInstant(rs, "updated_at");
        long lockVersion = rs.getLong("lock_version");

        Instant cancelledAt = toInstant(rs, "cancelled_at");
        String cancelledByStr = rs.getString("cancelled_by_user_id");
        UUID cancelledBy = cancelledByStr != null ? toUuid(cancelledByStr) : null;

        TimeInterval interval = TimeInterval.of(startsAt, endsAt);
        UnavailabilityReason reason = reasonCode != null ? UnavailabilityReason.valueOf(reasonCode) : null;
        RestrictedNote note = noteStr != null ? RestrictedNote.of(noteStr) : null;
        CancellationInfo cancellationInfo = (cancelledAt != null && cancelledBy != null)
                ? new CancellationInfo(cancelledAt, cancelledBy)
                : null;

        return EmployeeUnavailability.reconstruct(
                id, orgId, empId, interval, reason, note, createdBy,
                createdAt, updatedAt, lockVersion, cancellationInfo
        );
    };

    @Override
    public EmployeeUnavailability insert(EmployeeUnavailability unavailability) {
        Objects.requireNonNull(unavailability, "unavailability no puede ser nulo");
        try {
            List<EmployeeUnavailability> results = jdbc.query(
                    INSERT_SQL,
                    rowMapper,
                    unavailability.id().toString(),
                    unavailability.organizationId().toString(),
                    unavailability.employeeId().toString(),
                    OffsetDateTime.ofInstant(unavailability.interval().startsAt(), ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(unavailability.interval().endsAt(), ZoneOffset.UTC),
                    unavailability.reason().map(Enum::name).orElse(null),
                    unavailability.restrictedNote().map(RestrictedNote::value).orElse(null),
                    unavailability.createdByUserId().toString()
            );
            if (results.isEmpty()) {
                throw new UnavailabilityDataAccessException(new EmptyResultDataAccessException(1));
            }
            return results.get(0);
        } catch (DataAccessException e) {
            throw new UnavailabilityDataAccessException(e);
        }
    }

    @Override
    public Optional<EmployeeUnavailability> findById(UUID organizationId, UUID id) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(id, "id no puede ser nulo");
        try {
            List<EmployeeUnavailability> results = jdbc.query(
                    FIND_BY_ID_SQL,
                    rowMapper,
                    organizationId.toString(),
                    id.toString()
            );
            return results.stream().findFirst();
        } catch (DataAccessException e) {
            throw new UnavailabilityDataAccessException(e);
        }
    }

    @Override
    public List<EmployeeUnavailability> findByEmployee(
            UUID organizationId, UUID employeeId, boolean includeCancelled
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        try {
            return jdbc.query(
                    FIND_BY_EMPLOYEE_SQL,
                    rowMapper,
                    organizationId.toString(),
                    employeeId.toString(),
                    includeCancelled ? 1 : 0
            );
        } catch (DataAccessException e) {
            throw new UnavailabilityDataAccessException(e);
        }
    }

    @Override
    public List<EmployeeUnavailability> findByEmployeeAndInterval(
            UUID organizationId, UUID employeeId, Instant from, Instant to, boolean includeCancelled
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(from, "from no puede ser nulo");
        Objects.requireNonNull(to, "to no puede ser nulo");
        try {
            return jdbc.query(
                    FIND_BY_EMPLOYEE_AND_INTERVAL_SQL,
                    rowMapper,
                    organizationId.toString(),
                    employeeId.toString(),
                    OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                    includeCancelled ? 1 : 0
            );
        } catch (DataAccessException e) {
            throw new UnavailabilityDataAccessException(e);
        }
    }

    @Override
    public List<EmployeeUnavailability> findByOrganizationAndInterval(
            UUID organizationId, Instant from, Instant to, boolean includeCancelled
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(from, "from no puede ser nulo");
        Objects.requireNonNull(to, "to no puede ser nulo");
        try {
            return jdbc.query(
                    FIND_BY_ORG_AND_INTERVAL_SQL,
                    rowMapper,
                    organizationId.toString(),
                    OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                    includeCancelled ? 1 : 0
            );
        } catch (DataAccessException e) {
            throw new UnavailabilityDataAccessException(e);
        }
    }

    @Override
    public Optional<EmployeeUnavailability> update(EmployeeUnavailability unavailability, long expectedLockVersion) {
        Objects.requireNonNull(unavailability, "unavailability no puede ser nulo");
        try {
            List<EmployeeUnavailability> results = jdbc.query(
                    UPDATE_SQL,
                    rowMapper,
                    OffsetDateTime.ofInstant(unavailability.interval().startsAt(), ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(unavailability.interval().endsAt(), ZoneOffset.UTC),
                    unavailability.reason().map(Enum::name).orElse(null),
                    unavailability.restrictedNote().map(RestrictedNote::value).orElse(null),
                    unavailability.organizationId().toString(),
                    unavailability.id().toString(),
                    expectedLockVersion
            );
            return results.stream().findFirst();
        } catch (DataAccessException e) {
            throw new UnavailabilityDataAccessException(e);
        }
    }

    @Override
    public Optional<EmployeeUnavailability> cancel(
            UUID organizationId, UUID id, UUID actorUserId, long expectedLockVersion
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");
        try {
            List<EmployeeUnavailability> results = jdbc.query(
                    CANCEL_SQL,
                    rowMapper,
                    actorUserId.toString(),
                    organizationId.toString(),
                    id.toString(),
                    expectedLockVersion
            );
            return results.stream().findFirst();
        } catch (DataAccessException e) {
            throw new UnavailabilityDataAccessException(e);
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
        String str = rs.getString(columnLabel);
        return str != null ? Instant.parse(str) : null;
    }
}
