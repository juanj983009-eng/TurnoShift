package com.turnoshift.schedule.infrastructure.persistence;

import com.turnoshift.schedule.domain.DraftAssignment;
import com.turnoshift.schedule.domain.DraftStatus;
import com.turnoshift.schedule.domain.ScheduleConcurrentModificationException;
import com.turnoshift.schedule.domain.ScheduleDataAccessException;
import com.turnoshift.schedule.domain.ScheduleDomainException;
import com.turnoshift.schedule.domain.ScheduleDraft;
import com.turnoshift.schedule.domain.ShiftInterval;
import com.turnoshift.schedule.domain.ports.ScheduleDraftRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para el agregado ScheduleDraft y sus asignaciones DraftAssignment en SQL Server 2022.
 */
@Repository
public class JdbcScheduleDraftRepository implements ScheduleDraftRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcScheduleDraftRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    private static final RowMapper<DraftAssignment> ASSIGNMENT_ROW_MAPPER = (rs, rowNum) -> {
        UUID id = toUuid(rs.getString("id"));
        UUID orgId = toUuid(rs.getString("organization_id"));
        UUID scheduleId = toUuid(rs.getString("schedule_id"));
        UUID draftId = toUuid(rs.getString("draft_id"));
        UUID assignmentId = toUuid(rs.getString("assignment_id"));
        UUID baseRevisionId = toUuid(rs.getString("base_revision_id"));
        UUID employeeId = toUuid(rs.getString("employee_id"));
        UUID positionId = toUuid(rs.getString("position_id"));
        Instant startsAt = toInstant(rs, "starts_at");
        Instant endsAt = toInstant(rs, "ends_at");
        Instant createdAt = toInstant(rs, "created_at");
        Instant updatedAt = toInstant(rs, "updated_at");

        ShiftInterval interval = ShiftInterval.of(startsAt, endsAt);
        return new DraftAssignment(id, orgId, scheduleId, draftId, assignmentId, baseRevisionId, employeeId, positionId, interval, createdAt, updatedAt);
    };

    @Override
    public Optional<ScheduleDraft> findOpenDraftBySchedule(UUID organizationId, UUID scheduleId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");

        String sql = """
                SELECT id, organization_id, schedule_id, base_publication_id, status,
                       result_publication_id, change_reason, created_by_user_id,
                       lock_version, created_at, updated_at
                FROM schedule_drafts
                WHERE organization_id = ? AND schedule_id = ? AND status = 'BORRADOR';
                """;
        try {
            List<ScheduleDraft> list = jdbcTemplate.query(sql, (rs, rowNum) -> mapDraftHeader(rs), organizationId, scheduleId);
            if (list.isEmpty()) {
                return Optional.empty();
            }
            ScheduleDraft header = list.get(0);
            List<DraftAssignment> assignments = findAssignmentsByDraftId(organizationId, header.id());
            return Optional.of(new ScheduleDraft(
                    header.id(),
                    header.organizationId(),
                    header.scheduleId(),
                    header.basePublicationId(),
                    header.status(),
                    header.resultPublicationId(),
                    header.changeReason(),
                    header.createdByUserId(),
                    header.lockVersion(),
                    header.createdAt(),
                    header.updatedAt(),
                    assignments
            ));
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar borrador abierto por schedule", e);
        }
    }

    @Override
    public Optional<ScheduleDraft> findById(UUID organizationId, UUID draftId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(draftId, "draftId no puede ser nulo");

        String sql = """
                SELECT id, organization_id, schedule_id, base_publication_id, status,
                       result_publication_id, change_reason, created_by_user_id,
                       lock_version, created_at, updated_at
                FROM schedule_drafts
                WHERE organization_id = ? AND id = ?;
                """;
        try {
            List<ScheduleDraft> list = jdbcTemplate.query(sql, (rs, rowNum) -> mapDraftHeader(rs), organizationId, draftId);
            if (list.isEmpty()) {
                return Optional.empty();
            }
            ScheduleDraft header = list.get(0);
            List<DraftAssignment> assignments = findAssignmentsByDraftId(organizationId, header.id());
            return Optional.of(new ScheduleDraft(
                    header.id(),
                    header.organizationId(),
                    header.scheduleId(),
                    header.basePublicationId(),
                    header.status(),
                    header.resultPublicationId(),
                    header.changeReason(),
                    header.createdByUserId(),
                    header.lockVersion(),
                    header.createdAt(),
                    header.updatedAt(),
                    assignments
            ));
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar borrador por ID", e);
        }
    }

    @Override
    @Transactional
    public ScheduleDraft createDraft(ScheduleDraft draft) {
        Objects.requireNonNull(draft, "draft no puede ser nulo");

        String sql = """
                SET NOCOUNT ON;
                INSERT INTO schedule_drafts (
                    id, organization_id, schedule_id, base_publication_id, status,
                    result_publication_id, change_reason, created_by_user_id,
                    lock_version, created_at, updated_at
                )
                OUTPUT inserted.id, inserted.organization_id, inserted.schedule_id,
                       inserted.base_publication_id, inserted.status, inserted.result_publication_id,
                       inserted.change_reason, inserted.created_by_user_id,
                       inserted.lock_version, inserted.created_at, inserted.updated_at
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME(), SYSUTCDATETIME());
                """;

        try {
            List<ScheduleDraft> list = jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> mapDraftHeader(rs),
                    draft.id(),
                    draft.organizationId(),
                    draft.scheduleId(),
                    draft.basePublicationId(),
                    draft.status().name(),
                    draft.resultPublicationId(),
                    draft.changeReason(),
                    draft.createdByUserId(),
                    draft.lockVersion()
            );

            if (list.isEmpty()) {
                throw new ScheduleDataAccessException("No se pudo obtener el borrador creado");
            }
            ScheduleDraft createdHeader = list.get(0);

            // Si el borrador inicial incluye asignaciones, insertarlas
            if (draft.assignments() != null && !draft.assignments().isEmpty()) {
                for (DraftAssignment a : draft.assignments()) {
                    DraftAssignment toInsert = a.draftId().equals(createdHeader.id())
                            ? a
                            : new DraftAssignment(a.id(), a.organizationId(), a.scheduleId(), createdHeader.id(), a.assignmentId(), a.baseRevisionId(), a.employeeId(), a.positionId(), a.timeInterval(), a.createdAt(), a.updatedAt());
                    insertAssignmentInternal(toInsert);
                }
            }

            List<DraftAssignment> createdAssignments = findAssignmentsByDraftId(draft.organizationId(), draft.id());
            return new ScheduleDraft(
                    createdHeader.id(),
                    createdHeader.organizationId(),
                    createdHeader.scheduleId(),
                    createdHeader.basePublicationId(),
                    createdHeader.status(),
                    createdHeader.resultPublicationId(),
                    createdHeader.changeReason(),
                    createdHeader.createdByUserId(),
                    createdHeader.lockVersion(),
                    createdHeader.createdAt(),
                    createdHeader.updatedAt(),
                    createdAssignments
            );
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al crear borrador de horario", e);
        }
    }

    @Override
    public void updateDraftStatus(
            UUID organizationId,
            UUID draftId,
            DraftStatus status,
            UUID resultPublicationId,
            long expectedLockVersion
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(draftId, "draftId no puede ser nulo");
        Objects.requireNonNull(status, "status no puede ser nulo");

        String sql = """
                UPDATE schedule_drafts
                SET status = ?,
                    result_publication_id = ?,
                    lock_version = lock_version + 1,
                    updated_at = SYSUTCDATETIME()
                WHERE organization_id = ? AND id = ? AND lock_version = ?;
                """;
        try {
            int rows = jdbcTemplate.update(sql, status.name(), resultPublicationId, organizationId, draftId, expectedLockVersion);
            if (rows == 0) {
                checkLockVersionConflict(organizationId, draftId, expectedLockVersion);
            }
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al actualizar estado del borrador", e);
        }
    }

    @Override
    public void updateDraftLockVersion(UUID organizationId, UUID draftId, long expectedLockVersion) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(draftId, "draftId no puede ser nulo");

        String sql = """
                UPDATE schedule_drafts
                SET lock_version = lock_version + 1,
                    updated_at = SYSUTCDATETIME()
                WHERE organization_id = ? AND id = ? AND status = 'BORRADOR' AND lock_version = ?;
                """;
        try {
            int rows = jdbcTemplate.update(sql, organizationId, draftId, expectedLockVersion);
            if (rows == 0) {
                checkLockVersionConflict(organizationId, draftId, expectedLockVersion);
            }
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al incrementar lock_version del borrador", e);
        }
    }

    @Override
    public List<DraftAssignment> findAssignmentsByDraftId(UUID organizationId, UUID draftId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(draftId, "draftId no puede ser nulo");

        String sql = """
                SELECT id, organization_id, schedule_id, draft_id, assignment_id, base_revision_id,
                       employee_id, position_id, starts_at, ends_at, created_at, updated_at
                FROM draft_assignments
                WHERE organization_id = ? AND draft_id = ?
                ORDER BY starts_at, employee_id;
                """;
        try {
            return jdbcTemplate.query(sql, ASSIGNMENT_ROW_MAPPER, organizationId, draftId);
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar asignaciones del borrador", e);
        }
    }

    @Override
    @Transactional
    public DraftAssignment insertAssignment(DraftAssignment assignment, long expectedDraftLockVersion) {
        Objects.requireNonNull(assignment, "assignment no puede ser nulo");
        updateDraftLockVersion(assignment.organizationId(), assignment.draftId(), expectedDraftLockVersion);
        return insertAssignmentInternal(assignment);
    }

    private DraftAssignment insertAssignmentInternal(DraftAssignment assignment) {
        String sql = """
                SET NOCOUNT ON;
                INSERT INTO draft_assignments (
                    id, organization_id, schedule_id, draft_id, assignment_id, base_revision_id,
                    employee_id, position_id, starts_at, ends_at, created_at, updated_at
                )
                OUTPUT inserted.id, inserted.organization_id, inserted.schedule_id, inserted.draft_id,
                       inserted.assignment_id, inserted.base_revision_id, inserted.employee_id,
                       inserted.position_id, inserted.starts_at, inserted.ends_at,
                       inserted.created_at, inserted.updated_at
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME(), SYSUTCDATETIME());
                """;

        try {
            List<DraftAssignment> list = jdbcTemplate.query(
                    sql,
                    ASSIGNMENT_ROW_MAPPER,
                    assignment.id(),
                    assignment.organizationId(),
                    assignment.scheduleId(),
                    assignment.draftId(),
                    assignment.assignmentId(),
                    assignment.baseRevisionId(),
                    assignment.employeeId(),
                    assignment.positionId(),
                    OffsetDateTime.ofInstant(assignment.startsAt(), ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(assignment.endsAt(), ZoneOffset.UTC)
            );
            if (list.isEmpty()) {
                throw new ScheduleDataAccessException("No se pudo obtener la asignación creada en el borrador");
            }
            return list.get(0);
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al insertar asignación en borrador", e);
        }
    }

    @Override
    @Transactional
    public void updateAssignment(DraftAssignment assignment, long expectedDraftLockVersion) {
        Objects.requireNonNull(assignment, "assignment no puede ser nulo");
        updateDraftLockVersion(assignment.organizationId(), assignment.draftId(), expectedDraftLockVersion);

        String sql = """
                UPDATE draft_assignments
                SET employee_id = ?,
                    position_id = ?,
                    starts_at = ?,
                    ends_at = ?,
                    updated_at = SYSUTCDATETIME()
                WHERE organization_id = ? AND draft_id = ? AND id = ?;
                """;
        try {
            int rows = jdbcTemplate.update(
                    sql,
                    assignment.employeeId(),
                    assignment.positionId(),
                    OffsetDateTime.ofInstant(assignment.startsAt(), ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(assignment.endsAt(), ZoneOffset.UTC),
                    assignment.organizationId(),
                    assignment.draftId(),
                    assignment.id()
            );
            if (rows == 0) {
                throw new ScheduleDomainException("Asignación de borrador no encontrada para actualización: " + assignment.id());
            }
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al actualizar asignación del borrador", e);
        }
    }

    @Override
    @Transactional
    public void deleteAssignment(UUID organizationId, UUID draftId, UUID assignmentId, long expectedDraftLockVersion) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(draftId, "draftId no puede ser nulo");
        Objects.requireNonNull(assignmentId, "assignmentId no puede ser nulo");

        updateDraftLockVersion(organizationId, draftId, expectedDraftLockVersion);

        String sql = """
                DELETE FROM draft_assignments
                WHERE organization_id = ? AND draft_id = ? AND id = ?;
                """;
        try {
            int rows = jdbcTemplate.update(sql, organizationId, draftId, assignmentId);
            if (rows == 0) {
                throw new ScheduleDomainException("Asignación de borrador no encontrada para eliminación: " + assignmentId);
            }
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al eliminar asignación del borrador", e);
        }
    }

    @Override
    @Transactional
    public void deleteAssignmentsByDraftId(UUID organizationId, UUID draftId, long expectedDraftLockVersion) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(draftId, "draftId no puede ser nulo");

        updateDraftLockVersion(organizationId, draftId, expectedDraftLockVersion);

        String sql = """
                DELETE FROM draft_assignments
                WHERE organization_id = ? AND draft_id = ?;
                """;
        try {
            jdbcTemplate.update(sql, organizationId, draftId);
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al eliminar todas las asignaciones del borrador", e);
        }
    }

    private void checkLockVersionConflict(UUID organizationId, UUID draftId, long expectedLockVersion) {
        String checkSql = "SELECT lock_version, status FROM schedule_drafts WHERE organization_id = ? AND id = ?;";
        List<Long> versions = jdbcTemplate.query(checkSql, (rs, rn) -> rs.getLong("lock_version"), organizationId, draftId);
        if (!versions.isEmpty()) {
            throw new ScheduleConcurrentModificationException(
                    String.format("Conflicto de concurrencia en borrador %s: lock_version esperada %d, actual %d",
                            draftId, expectedLockVersion, versions.get(0))
            );
        } else {
            throw new ScheduleDomainException("Borrador no encontrado: " + draftId);
        }
    }

    private static ScheduleDraft mapDraftHeader(ResultSet rs) throws SQLException {
        UUID id = toUuid(rs.getString("id"));
        UUID orgId = toUuid(rs.getString("organization_id"));
        UUID schedId = toUuid(rs.getString("schedule_id"));
        UUID basePubId = toUuid(rs.getString("base_publication_id"));
        DraftStatus status = DraftStatus.valueOf(rs.getString("status"));
        UUID resultPubId = toUuid(rs.getString("result_publication_id"));
        String changeReason = rs.getString("change_reason");
        UUID createdBy = toUuid(rs.getString("created_by_user_id"));
        long lockVer = rs.getLong("lock_version");
        Instant created = toInstant(rs, "created_at");
        Instant updated = toInstant(rs, "updated_at");

        return new ScheduleDraft(
                id,
                orgId,
                schedId,
                basePubId,
                status,
                resultPubId,
                changeReason,
                createdBy,
                lockVer,
                created,
                updated,
                List.of()
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
