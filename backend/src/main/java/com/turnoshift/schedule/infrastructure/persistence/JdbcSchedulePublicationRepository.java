package com.turnoshift.schedule.infrastructure.persistence;

import com.turnoshift.schedule.domain.Assignment;
import com.turnoshift.schedule.domain.AssignmentRevision;
import com.turnoshift.schedule.domain.PlanningChangeSet;
import com.turnoshift.schedule.domain.PlanningWarningAcknowledgement;
import com.turnoshift.schedule.domain.PublicationOrigin;
import com.turnoshift.schedule.domain.PublishedAssignmentRef;
import com.turnoshift.schedule.domain.PublishedShiftRecord;
import com.turnoshift.schedule.domain.ScheduleConcurrentModificationException;
import com.turnoshift.schedule.domain.ScheduleDataAccessException;
import com.turnoshift.schedule.domain.SchedulePublication;
import com.turnoshift.schedule.domain.ports.SchedulePublicationRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para SchedulePublication, su histórico y la secuencia de publicación atómica en SQL Server 2022.
 */
@Repository
public class JdbcSchedulePublicationRepository implements SchedulePublicationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSchedulePublicationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public Optional<SchedulePublication> findById(UUID organizationId, UUID publicationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(publicationId, "publicationId no puede ser nulo");

        String sql = """
                SELECT id, organization_id, schedule_id, publication_number, previous_publication_id,
                       policy_version_id, timezone_id_snapshot, origin, change_set_id, published_at
                FROM schedule_publications
                WHERE organization_id = ? AND id = ?;
                """;
        try {
            List<SchedulePublication> list = jdbcTemplate.query(sql, (rs, rn) -> mapHeader(rs), organizationId, publicationId);
            if (list.isEmpty()) {
                return Optional.empty();
            }
            SchedulePublication header = list.get(0);
            List<PublishedAssignmentRef> assignments = findPublicationAssignments(organizationId, header.scheduleId(), publicationId);
            return Optional.of(new SchedulePublication(
                    header.id(),
                    header.organizationId(),
                    header.scheduleId(),
                    header.publicationNumber(),
                    header.previousPublicationId(),
                    header.policyVersionId(),
                    header.timezoneIdSnapshot(),
                    header.origin(),
                    header.changeSetId(),
                    header.publishedAt(),
                    assignments
            ));
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar publicación por ID", e);
        }
    }

    @Override
    public Optional<SchedulePublication> findCurrentPublication(UUID organizationId, UUID scheduleId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");

        String sql = """
                SELECT p.id, p.organization_id, p.schedule_id, p.publication_number, p.previous_publication_id,
                       p.policy_version_id, p.timezone_id_snapshot, p.origin, p.change_set_id, p.published_at
                FROM schedule_publications p
                JOIN schedules s
                  ON p.organization_id = s.organization_id AND p.id = s.current_publication_id
                WHERE s.organization_id = ? AND s.id = ?;
                """;
        try {
            List<SchedulePublication> list = jdbcTemplate.query(sql, (rs, rn) -> mapHeader(rs), organizationId, scheduleId);
            if (list.isEmpty()) {
                return Optional.empty();
            }
            SchedulePublication header = list.get(0);
            List<PublishedAssignmentRef> assignments = findPublicationAssignments(organizationId, scheduleId, header.id());
            return Optional.of(new SchedulePublication(
                    header.id(),
                    header.organizationId(),
                    header.scheduleId(),
                    header.publicationNumber(),
                    header.previousPublicationId(),
                    header.policyVersionId(),
                    header.timezoneIdSnapshot(),
                    header.origin(),
                    header.changeSetId(),
                    header.publishedAt(),
                    assignments
            ));
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar publicación vigente", e);
        }
    }

    @Override
    public List<SchedulePublication> findPublicationHistory(UUID organizationId, UUID scheduleId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");

        String sql = """
                SELECT id, organization_id, schedule_id, publication_number, previous_publication_id,
                       policy_version_id, timezone_id_snapshot, origin, change_set_id, published_at
                FROM schedule_publications
                WHERE organization_id = ? AND schedule_id = ?
                ORDER BY publication_number DESC;
                """;
        try {
            List<SchedulePublication> headers = jdbcTemplate.query(sql, (rs, rn) -> mapHeader(rs), organizationId, scheduleId);
            List<SchedulePublication> result = new ArrayList<>(headers.size());
            for (SchedulePublication h : headers) {
                List<PublishedAssignmentRef> assignments = findPublicationAssignments(organizationId, scheduleId, h.id());
                result.add(new SchedulePublication(
                        h.id(),
                        h.organizationId(),
                        h.scheduleId(),
                        h.publicationNumber(),
                        h.previousPublicationId(),
                        h.policyVersionId(),
                        h.timezoneIdSnapshot(),
                        h.origin(),
                        h.changeSetId(),
                        h.publishedAt(),
                        assignments
                ));
            }
            return Collections.unmodifiableList(result);
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar histórico de publicaciones", e);
        }
    }

    @Override
    public int findNextPublicationNumber(UUID organizationId, UUID scheduleId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");

        String sql = "SELECT COALESCE(MAX(publication_number), 0) + 1 FROM schedule_publications WHERE organization_id = ? AND schedule_id = ?;";
        try {
            Integer next = jdbcTemplate.queryForObject(sql, Integer.class, organizationId, scheduleId);
            return next != null ? next : 1;
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al calcular siguiente número de publicación", e);
        }
    }

    @Override
    public List<PublishedShiftRecord> findPublishedShiftsInInterval(UUID organizationId, UUID siteId, Instant from, Instant to) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(siteId, "siteId no puede ser nulo");
        Objects.requireNonNull(from, "from no puede ser nulo");
        Objects.requireNonNull(to, "to no puede ser nulo");

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
                WHERE s.organization_id = ?
                  AND s.site_id = ?
                  AND s.current_publication_id = p.id
                  AND ar.starts_at < ?
                  AND ar.ends_at > ?
                ORDER BY ar.starts_at;
                """;
        try {
            return jdbcTemplate.query(
                    sql,
                    (rs, rn) -> new PublishedShiftRecord(
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
                    ),
                    organizationId,
                    siteId,
                    OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(from, ZoneOffset.UTC)
            );
        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error al consultar turnos publicados en intervalo", e);
        }
    }

    @Override
    @Transactional
    public SchedulePublication createPublicationAtomic(
            SchedulePublication publication,
            PlanningChangeSet changeSet,
            List<PlanningWarningAcknowledgement> acknowledgements,
            List<Assignment> newAssignments,
            List<AssignmentRevision> newRevisions,
            List<PublishedAssignmentRef> publicationAssignments,
            long expectedDraftLockVersion,
            long expectedScheduleLockVersion
    ) {
        Objects.requireNonNull(publication, "publication no puede ser nula");
        Objects.requireNonNull(changeSet, "changeSet no puede ser nulo");
        acknowledgements = acknowledgements == null ? List.of() : acknowledgements;
        newAssignments = newAssignments == null ? List.of() : newAssignments;
        newRevisions = newRevisions == null ? List.of() : newRevisions;
        publicationAssignments = publicationAssignments == null ? List.of() : publicationAssignments;

        try {
            // Paso 1: INSERT planning_change_sets
            String insertChangeSetSql = """
                    SET NOCOUNT ON;
                    INSERT INTO planning_change_sets (
                        id, organization_id, schedule_id, change_type, actor_user_id, policy_version_id,
                        source_draft_id, reason, empty_schedule_confirmed, validation_fingerprint,
                        validation_schema_version, correlation_id, confirmed_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME());
                    """;
            jdbcTemplate.update(
                    insertChangeSetSql,
                    changeSet.id(),
                    changeSet.organizationId(),
                    changeSet.scheduleId(),
                    changeSet.changeType().name(),
                    changeSet.actorUserId(),
                    changeSet.policyVersionId(),
                    changeSet.sourceDraftId(),
                    changeSet.reason(),
                    changeSet.emptyScheduleConfirmed() ? 1 : 0,
                    changeSet.validationFingerprint(),
                    changeSet.validationSchemaVersion(),
                    changeSet.correlationId()
            );

            // Paso 2: INSERT planning_warning_acknowledgements
            if (!acknowledgements.isEmpty()) {
                String insertAckSql = """
                        INSERT INTO planning_warning_acknowledgements (
                            id, organization_id, change_set_id, employee_id, warning_code,
                            period_start, period_end_exclusive, timezone_id_snapshot, planned_minutes,
                            threshold_minutes, warning_fingerprint, context_schema_version, context_data, created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME());
                        """;
                for (PlanningWarningAcknowledgement ack : acknowledgements) {
                    jdbcTemplate.update(
                            insertAckSql,
                            ack.id(),
                            ack.organizationId(),
                            ack.changeSetId(),
                            ack.employeeId(),
                            ack.warningCode(),
                            java.sql.Date.valueOf(ack.periodStart()),
                            java.sql.Date.valueOf(ack.periodEndExclusive()),
                            ack.timezoneIdSnapshot().getId(),
                            ack.plannedMinutes(),
                            ack.thresholdMinutes(),
                            ack.warningFingerprint(),
                            ack.contextSchemaVersion(),
                            ack.contextData()
                    );
                }
            }

            // Paso 3: INSERT assignments (turnos nuevos)
            if (!newAssignments.isEmpty()) {
                String insertAssignSql = """
                        INSERT INTO assignments (id, organization_id, schedule_id, created_at)
                        VALUES (?, ?, ?, SYSUTCDATETIME());
                        """;
                for (Assignment a : newAssignments) {
                    jdbcTemplate.update(insertAssignSql, a.id(), a.organizationId(), a.scheduleId());
                }
            }

            // Paso 4: INSERT assignment_revisions
            if (!newRevisions.isEmpty()) {
                String insertRevSql = """
                        INSERT INTO assignment_revisions (
                            id, organization_id, schedule_id, assignment_id, revision_number,
                            employee_id, position_id, starts_at, ends_at, employee_name_snapshot,
                            position_name_snapshot, created_by_user_id, created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME());
                        """;
                for (AssignmentRevision rev : newRevisions) {
                    jdbcTemplate.update(
                            insertRevSql,
                            rev.id(),
                            rev.organizationId(),
                            rev.scheduleId(),
                            rev.assignmentId(),
                            rev.revisionNumber(),
                            rev.employeeId(),
                            rev.positionId(),
                            OffsetDateTime.ofInstant(rev.startsAt(), ZoneOffset.UTC),
                            OffsetDateTime.ofInstant(rev.endsAt(), ZoneOffset.UTC),
                            rev.employeeNameSnapshot(),
                            rev.positionNameSnapshot(),
                            rev.createdByUserId()
                    );
                }
            }

            // Paso 5: INSERT schedule_publications
            String insertPubSql = """
                    SET NOCOUNT ON;
                    INSERT INTO schedule_publications (
                        id, organization_id, schedule_id, publication_number, previous_publication_id,
                        policy_version_id, timezone_id_snapshot, origin, change_set_id, published_at
                    )
                    OUTPUT inserted.id, inserted.organization_id, inserted.schedule_id,
                           inserted.publication_number, inserted.previous_publication_id,
                           inserted.policy_version_id, inserted.timezone_id_snapshot,
                           inserted.origin, inserted.change_set_id, inserted.published_at
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME());
                    """;
            List<SchedulePublication> pubs = jdbcTemplate.query(
                    insertPubSql,
                    (rs, rn) -> mapHeader(rs),
                    publication.id(),
                    publication.organizationId(),
                    publication.scheduleId(),
                    publication.publicationNumber(),
                    publication.previousPublicationId(),
                    publication.policyVersionId(),
                    publication.timezoneIdSnapshot().getId(),
                    publication.origin().name(),
                    publication.changeSetId()
            );
            if (pubs.isEmpty()) {
                throw new ScheduleDataAccessException("No se pudo obtener la publicación creada");
            }
            SchedulePublication createdPub = pubs.get(0);

            // Paso 6: INSERT publication_assignments
            if (!publicationAssignments.isEmpty()) {
                String insertPubAssignSql = """
                        INSERT INTO publication_assignments (
                            organization_id, schedule_id, publication_id, assignment_id, assignment_revision_id
                        )
                        VALUES (?, ?, ?, ?, ?);
                        """;
                for (PublishedAssignmentRef ref : publicationAssignments) {
                    jdbcTemplate.update(
                            insertPubAssignSql,
                            publication.organizationId(),
                            publication.scheduleId(),
                            publication.id(),
                            ref.assignmentId(),
                            ref.assignmentRevisionId()
                    );
                }
            }

            // Paso 7: UPDATE schedule_drafts (si proviene de un borrador)
            if (changeSet.sourceDraftId() != null) {
                String updateDraftSql = """
                        UPDATE schedule_drafts
                        SET status = 'PUBLICADO',
                            result_publication_id = ?,
                            lock_version = lock_version + 1,
                            updated_at = SYSUTCDATETIME()
                        WHERE organization_id = ? AND id = ? AND status = 'BORRADOR' AND lock_version = ?;
                        """;
                int draftRows = jdbcTemplate.update(
                        updateDraftSql,
                        createdPub.id(),
                        publication.organizationId(),
                        changeSet.sourceDraftId(),
                        expectedDraftLockVersion
                );
                if (draftRows == 0) {
                    throw new ScheduleConcurrentModificationException(
                            "Conflicto de concurrencia al actualizar el estado del borrador a PUBLICADO: " + changeSet.sourceDraftId()
                    );
                }
            }

            // Paso 8: UPDATE schedules
            String updateScheduleSql = """
                    UPDATE schedules
                    SET current_publication_id = ?,
                        lock_version = lock_version + 1,
                        updated_at = SYSUTCDATETIME()
                    WHERE organization_id = ? AND id = ? AND lock_version = ?;
                    """;
            int schedRows = jdbcTemplate.update(
                    updateScheduleSql,
                    createdPub.id(),
                    publication.organizationId(),
                    publication.scheduleId(),
                    expectedScheduleLockVersion
            );
            if (schedRows == 0) {
                throw new ScheduleConcurrentModificationException(
                        "Conflicto de concurrencia al actualizar publicación actual del schedule: " + publication.scheduleId()
                );
            }

            return new SchedulePublication(
                    createdPub.id(),
                    createdPub.organizationId(),
                    createdPub.scheduleId(),
                    createdPub.publicationNumber(),
                    createdPub.previousPublicationId(),
                    createdPub.policyVersionId(),
                    createdPub.timezoneIdSnapshot(),
                    createdPub.origin(),
                    createdPub.changeSetId(),
                    createdPub.publishedAt(),
                    publicationAssignments
            );

        } catch (DataAccessException e) {
            throw new ScheduleDataAccessException("Error en la secuencia atómica de publicación", e);
        }
    }

    private List<PublishedAssignmentRef> findPublicationAssignments(UUID organizationId, UUID scheduleId, UUID publicationId) {
        String sql = """
                SELECT assignment_id, assignment_revision_id
                FROM publication_assignments
                WHERE organization_id = ? AND schedule_id = ? AND publication_id = ?
                ORDER BY assignment_id;
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rn) -> new PublishedAssignmentRef(
                        toUuid(rs.getString("assignment_id")),
                        toUuid(rs.getString("assignment_revision_id"))
                ),
                organizationId,
                scheduleId,
                publicationId
        );
    }

    private static SchedulePublication mapHeader(ResultSet rs) throws SQLException {
        UUID id = toUuid(rs.getString("id"));
        UUID orgId = toUuid(rs.getString("organization_id"));
        UUID schedId = toUuid(rs.getString("schedule_id"));
        int pubNum = rs.getInt("publication_number");
        UUID prevPubId = toUuid(rs.getString("previous_publication_id"));
        UUID policyVerId = toUuid(rs.getString("policy_version_id"));
        ZoneId tz = ZoneId.of(rs.getString("timezone_id_snapshot"));
        PublicationOrigin origin = PublicationOrigin.valueOf(rs.getString("origin"));
        UUID changeSetId = toUuid(rs.getString("change_set_id"));
        Instant pubAt = toInstant(rs, "published_at");

        return new SchedulePublication(
                id,
                orgId,
                schedId,
                pubNum,
                prevPubId,
                policyVerId,
                tz,
                origin,
                changeSetId,
                pubAt,
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
