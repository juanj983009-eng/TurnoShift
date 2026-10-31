package com.turnoshift.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suite de integración de persistencia para el dominio de planificación de turnos (I3.1-a).
 * <p>
 * Valida:
 * <ol>
 *   <li>Migración limpia V1–V19 desde base vacía e inspección de catálogo (9 tablas, PKs, UQs, FKs, Checks, índices).</li>
 *   <li>Ciclo de publicación en dos pasos y claves foráneas circulares (V19).</li>
 *   <li>Aislamiento multi-tenant estricto mediante claves foráneas compuestas.</li>
 *   <li>Rechazo de referencias cruzadas entre distintas semanas del mismo tenant.</li>
 *   <li>Unicidad de semana por sede (uq_schedules_org_site_week).</li>
 *   <li>Unicidad de un solo borrador abierto (uix_schedule_drafts_one_open, RN-027).</li>
 *   <li>Restricciones de dominio: lunes canónico, duración de turnos (15 a 960 min), estados y tipos de cambio.</li>
 *   <li>Publicación de semana vacía (DEC-03) y reconocimientos de advertencias (DEC-04).</li>
 *   <li>Upgrade progresivo V17 → V19 preservando datos previos.</li>
 * </ol>
 */
@SpringBootTest
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: Persistencia y migraciones de planificación de turnos (V18 y V19)")
class SchedulePlanningPersistenceIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    // =========================================================================
    // Helpers de inserción de grafos relacionales
    // =========================================================================

    private String insertarOrganizacion(String code, String displayName) {
        String orgId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, code, displayName);
        return orgId;
    }

    private String insertarSede(String orgId, String code, String name) {
        String siteId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO sites (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, ?, 1)
                """, siteId, orgId, code, name);
        return siteId;
    }

    private String insertarPuesto(String orgId, String code, String name) {
        String positionId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO positions (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, ?, 1)
                """, positionId, orgId, code, name);
        return positionId;
    }

    private String insertarEmpleado(String orgId, String code, String displayName, String posId, String siteId) {
        String empId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO employees (id, organization_id, code, display_name, position_id, site_id, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """, empId, orgId, code, displayName, posId, siteId);
        return empId;
    }

    private String insertarUsuario(String orgId, String email, String displayName, String roleCode) {
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                VALUES (?, ?, ?, ?, ?, ?, NULL)
                """, userId, orgId, email, email.toLowerCase(), displayName, roleCode);
        return userId;
    }

    private String insertarPoliticaActiva(String orgId, String authorUserId) {
        String policyId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO planning_policy_versions (
                    id, organization_id, version_number, minimum_rest_minutes, weekly_warning_minutes,
                    created_by_user_id, change_reason
                )
                VALUES (?, ?, 1, 660, 2400, ?, 'Politica inicial para pruebas')
                """, policyId, orgId, authorUserId);

        jdbc.update("""
                UPDATE organizations
                SET current_policy_version_id = ?
                WHERE id = ?
                """, policyId, orgId);

        return policyId;
    }

    private String insertarSegundaPolitica(String orgId, String authorUserId) {
        String policyId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO planning_policy_versions (
                    id, organization_id, version_number, minimum_rest_minutes, weekly_warning_minutes,
                    created_by_user_id, change_reason
                )
                VALUES (?, ?, 2, 720, 2400, ?, 'Segunda politica para pruebas')
                """, policyId, orgId, authorUserId);
        return policyId;
    }

    private String insertarSchedule(String orgId, String siteId, String weekStart) {
        String scheduleId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO schedules (id, organization_id, site_id, week_start, current_publication_id)
                VALUES (?, ?, ?, ?, NULL)
                """, scheduleId, orgId, siteId, weekStart);
        return scheduleId;
    }

    private String insertarScheduleDraft(String orgId, String scheduleId, String status,
                                         String basePubId, String resultPubId, String authorUserId) {
        String draftId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO schedule_drafts (
                    id, organization_id, schedule_id, base_publication_id, status,
                    result_publication_id, created_by_user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, draftId, orgId, scheduleId, basePubId, status, resultPubId, authorUserId);
        return draftId;
    }

    private String insertarDraftAssignment(String orgId, String scheduleId, String draftId,
                                           String assignId, String baseRevId, String empId,
                                           String posId, Instant startsAt, Instant endsAt) {
        String draftAssignId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO draft_assignments (
                    id, organization_id, schedule_id, draft_id, assignment_id, base_revision_id,
                    employee_id, position_id, starts_at, ends_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, draftAssignId, orgId, scheduleId, draftId, assignId, baseRevId,
                empId, posId, startsAt.toString(), endsAt.toString());
        return draftAssignId;
    }

    private String insertarChangeSet(String orgId, String scheduleId, String changeType,
                                     String actorUserId, String policyVersionId, String sourceDraftId,
                                     String reason, boolean emptyScheduleConfirmed, byte[] fingerprint) {
        String changeSetId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO planning_change_sets (
                    id, organization_id, schedule_id, change_type, actor_user_id, policy_version_id,
                    source_draft_id, reason, empty_schedule_confirmed, validation_fingerprint
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, changeSetId, orgId, scheduleId, changeType, actorUserId, policyVersionId,
                sourceDraftId, reason, emptyScheduleConfirmed ? 1 : 0, fingerprint);
        return changeSetId;
    }

    private String insertarAssignment(String orgId, String scheduleId) {
        String assignId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO assignments (id, organization_id, schedule_id)
                VALUES (?, ?, ?)
                """, assignId, orgId, scheduleId);
        return assignId;
    }

    private String insertarAssignmentRevision(String orgId, String scheduleId, String assignId,
                                              int revNumber, String empId, String posId,
                                              Instant startsAt, Instant endsAt, String empName,
                                              String posName, String authorUserId) {
        String revId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO assignment_revisions (
                    id, organization_id, schedule_id, assignment_id, revision_number,
                    employee_id, position_id, starts_at, ends_at,
                    employee_name_snapshot, position_name_snapshot, created_by_user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, revId, orgId, scheduleId, assignId, revNumber,
                empId, posId, startsAt.toString(), endsAt.toString(), empName, posName, authorUserId);
        return revId;
    }

    private String insertarSchedulePublication(String orgId, String scheduleId, int pubNumber,
                                               String prevPubId, String policyVersionId,
                                               String timezoneId, String origin, String changeSetId) {
        String pubId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO schedule_publications (
                    id, organization_id, schedule_id, publication_number, previous_publication_id,
                    policy_version_id, timezone_id_snapshot, origin, change_set_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, pubId, orgId, scheduleId, pubNumber, prevPubId,
                policyVersionId, timezoneId, origin, changeSetId);
        return pubId;
    }

    private void insertarPublicationAssignment(String orgId, String scheduleId, String pubId,
                                               String assignId, String revId) {
        jdbc.update("""
                INSERT INTO publication_assignments (
                    organization_id, schedule_id, publication_id, assignment_id, assignment_revision_id
                )
                VALUES (?, ?, ?, ?, ?)
                """, orgId, scheduleId, pubId, assignId, revId);
    }

    private byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // 1. Esquema nuevo desde base vacía e inspección de catálogo
    // =========================================================================

    @Test
    @DisplayName("1. Flyway aplica V1 a V19 en orden e inspección de catálogo valida 9 tablas y constraints")
    void aplicacionLimpiaYCatalogo() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(versiones).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19");

        // 9 tablas de planificación
        List<String> tablas = List.of(
                "schedules", "schedule_drafts", "assignments", "assignment_revisions",
                "draft_assignments", "planning_change_sets", "planning_warning_acknowledgements",
                "schedule_publications", "publication_assignments");
        for (String tabla : tablas) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'",
                    Integer.class, tabla);
            assertThat(count).as("La tabla " + tabla + " debe existir").isEqualTo(1);
        }

        // Verificación de que shift_type_id no existe en draft_assignments ni en assignment_revisions
        Integer shiftTypeCols = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME IN ('draft_assignments', 'assignment_revisions')
                  AND COLUMN_NAME = 'shift_type_id'
                """, Integer.class);
        assertThat(shiftTypeCols).as("shift_type_id no debe existir en el esquema de I3").isZero();

        // Verificación de todas las restricciones por nombre exacto
        List<String> restricciones = List.of(
                // PKs
                "pk_schedules", "pk_schedule_drafts", "pk_assignments", "pk_assignment_revisions",
                "pk_draft_assignments", "pk_planning_change_sets", "pk_planning_warning_acknowledgements",
                "pk_schedule_publications", "pk_publication_assignments",
                // Candidate Keys (UQs)
                "uq_schedules_org_id", "uq_schedules_org_site_week", "uq_schedules_org_id_current_pub",
                "uq_schedule_drafts_org_id", "uq_schedule_drafts_org_schedule_id",
                "uq_assignments_org_schedule_id",
                "uq_assignment_revisions_org_sched_assign_id", "uq_assignment_revisions_number",
                "uq_draft_assignments_org_id",
                "uq_planning_change_sets_org_id", "uq_planning_change_sets_org_sched_id_policy",
                "uq_warning_acks_org_id", "uq_warning_acks_change_set_fingerprint",
                "uq_schedule_publications_org_schedule_id", "uq_schedule_publications_number",
                "uq_schedule_publications_change_set",
                // FKs de V18
                "fk_schedules_organization", "fk_schedules_site",
                "fk_schedule_drafts_organization", "fk_schedule_drafts_schedule", "fk_schedule_drafts_author",
                "fk_assignments_organization", "fk_assignments_schedule",
                "fk_assignment_revisions_organization", "fk_assignment_revisions_assignment",
                "fk_assignment_revisions_employee", "fk_assignment_revisions_position", "fk_assignment_revisions_author",
                "fk_draft_assignments_organization", "fk_draft_assignments_draft", "fk_draft_assignments_employee",
                "fk_draft_assignments_position", "fk_draft_assignments_assignment", "fk_draft_assignments_base_revision",
                "fk_planning_change_sets_organization", "fk_planning_change_sets_schedule",
                "fk_planning_change_sets_actor", "fk_planning_change_sets_policy_version",
                "fk_planning_change_sets_source_draft",
                "fk_warning_acks_organization", "fk_warning_acks_change_set", "fk_warning_acks_employee",
                "fk_schedule_publications_organization", "fk_schedule_publications_schedule",
                "fk_schedule_publications_previous", "fk_schedule_publications_policy_version",
                "fk_schedule_publications_change_set",
                "fk_pub_assignments_publication", "fk_pub_assignments_assignment", "fk_pub_assignments_revision",
                // FKs diferidas de V19
                "fk_schedules_current_publication", "fk_schedule_drafts_base_publication",
                "fk_schedule_drafts_result_publication",
                // Checks
                "chk_schedules_week_start_monday", "chk_schedules_lock_version_non_negative",
                "chk_schedule_drafts_status", "chk_schedule_drafts_result_coherence",
                "chk_schedule_drafts_reason_not_blank", "chk_schedule_drafts_lock_version_non_negative",
                "chk_assignment_revisions_rev_positive", "chk_assignment_revisions_interval",
                "chk_assignment_revisions_emp_snapshot_not_empty", "chk_assignment_revisions_pos_snapshot_not_empty",
                "chk_draft_assignments_base_coherence", "chk_draft_assignments_interval",
                "chk_planning_change_sets_type", "chk_planning_change_sets_draft_coherence",
                "chk_planning_change_sets_empty_reason", "chk_planning_change_sets_reason_not_blank",
                "chk_planning_change_sets_schema_ver_pos",
                "chk_warning_acks_code", "chk_warning_acks_minutes_exceeded", "chk_warning_acks_period",
                "chk_warning_acks_timezone_not_empty", "chk_warning_acks_json",
                "chk_schedule_publications_number_positive", "chk_schedule_publications_prev_coherence",
                "chk_schedule_publications_origin", "chk_schedule_publications_origin_coherence",
                "chk_schedule_publications_timezone_not_empty"
        );

        for (String constr : restricciones) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.objects WHERE name = ?",
                    Integer.class, constr);
            assertThat(count).as("La restricción " + constr + " debe existir físicamente").isEqualTo(1);
        }

        // Verificación de índices operativos y filtrados
        List<String> indices = List.of(
                "uix_schedule_drafts_one_open",
                "uix_draft_assignments_unique_published_ref",
                "ix_assignment_revisions_org_emp_interval",
                "ix_draft_assignments_draft_starts",
                "ix_pub_assignments_revision"
        );
        for (String idx : indices) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.indexes WHERE name = ?",
                    Integer.class, idx);
            assertThat(count).as("El índice " + idx + " debe existir").isEqualTo(1);
        }
    }

    // =========================================================================
    // 2. Flujo completo de publicación en dos pasos y referencias circulares (V19)
    // =========================================================================

    @Test
    @DisplayName("2. Ciclo de publicación en dos pasos y validación de FKs circulares de V19")
    void cicloPublicacionDosPasosYFksCirculares() {
        String orgId = insertarOrganizacion("ORG-CYCLE-01", "Org Cycle 01");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Cajero");
        String empId = insertarEmpleado(orgId, "EMP-01", "Ana Perez", posId, siteId);
        String userId = insertarUsuario(orgId, "admin@cycle01.com", "Admin Cycle 01", "ADMINISTRADOR");
        String policyId = insertarPoliticaActiva(orgId, userId);

        String scheduleId = insertarSchedule(orgId, siteId, "2026-10-05");
        String draftId = insertarScheduleDraft(orgId, scheduleId, "BORRADOR", null, null, userId);

        Instant startsAt = Instant.parse("2026-10-05T08:00:00Z");
        Instant endsAt = Instant.parse("2026-10-05T16:00:00Z");
        insertarDraftAssignment(orgId, scheduleId, draftId, null, null, empId, posId, startsAt, endsAt);

        // Protocolo transaccional de publicación:
        // Paso 1: ChangeSet
        byte[] fp = sha256("test-validation-fingerprint");
        String changeSetId = insertarChangeSet(orgId, scheduleId, "PUBLICACION_BORRADOR", userId,
                policyId, draftId, "Publicación inicial", false, fp);

        // Paso 2: Assignment + Revision
        String assignId = insertarAssignment(orgId, scheduleId);
        String revId = insertarAssignmentRevision(orgId, scheduleId, assignId, 1, empId, posId,
                startsAt, endsAt, "Ana Perez", "Cajero", userId);

        // Paso 3: SchedulePublication + PublicationAssignment
        String pubId = insertarSchedulePublication(orgId, scheduleId, 1, null, policyId,
                "America/Bogota", "INICIAL", changeSetId);
        insertarPublicationAssignment(orgId, scheduleId, pubId, assignId, revId);

        // Paso 4: Cierre del ciclo mediante UPDATEs
        jdbc.update("""
                UPDATE schedule_drafts
                SET status = 'PUBLICADO', result_publication_id = ?, lock_version = lock_version + 1, updated_at = SYSUTCDATETIME()
                WHERE organization_id = ? AND id = ?
                """, pubId, orgId, draftId);

        jdbc.update("""
                UPDATE schedules
                SET current_publication_id = ?, lock_version = lock_version + 1, updated_at = SYSUTCDATETIME()
                WHERE organization_id = ? AND id = ?
                """, pubId, orgId, scheduleId);

        // Verificación de lectura consistente
        String currentPubId = jdbc.queryForObject(
                "SELECT current_publication_id FROM schedules WHERE id = ?", String.class, scheduleId);
        assertThat(currentPubId).isEqualToIgnoringCase(pubId);

        String resultPubId = jdbc.queryForObject(
                "SELECT result_publication_id FROM schedule_drafts WHERE id = ?", String.class, draftId);
        assertThat(resultPubId).isEqualToIgnoringCase(pubId);

        // Verificación negativa: fk_schedules_current_publication rechaza publicación de otro schedule
        String otherScheduleId = insertarSchedule(orgId, siteId, "2026-10-12");
        assertThatThrownBy(() ->
                jdbc.update("UPDATE schedules SET current_publication_id = ? WHERE id = ?", pubId, otherScheduleId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_schedules_current_publication");

        // Verificación negativa: fk_schedule_drafts_base_publication rechaza publicación de otro schedule
        String otherDraftId = insertarScheduleDraft(orgId, otherScheduleId, "BORRADOR", null, null, userId);
        assertThatThrownBy(() ->
                jdbc.update("UPDATE schedule_drafts SET base_publication_id = ? WHERE id = ?", pubId, otherDraftId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_schedule_drafts_base_publication");

        // Verificación negativa: fk_schedule_drafts_result_publication rechaza publicación de otro schedule
        assertThatThrownBy(() ->
                jdbc.update("UPDATE schedule_drafts SET status = 'PUBLICADO', result_publication_id = ? WHERE id = ?", pubId, otherDraftId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_schedule_drafts_result_publication");
    }

    // =========================================================================
    // 3. Aislamiento multi-tenant estricto (Organization A vs Organization B)
    // =========================================================================

    @Test
    @DisplayName("3. FKs compuestas rechazan cruces entre Organization A y Organization B")
    void aislamientoMultiTenant() {
        String orgAId = insertarOrganizacion("ORG-ISO-A", "Org Isolation A");
        String siteAId = insertarSede(orgAId, "SED-A", "Sede A");
        String userAId = insertarUsuario(orgAId, "userA@iso.com", "User A", "ADMINISTRADOR");
        String policyAId = insertarPoliticaActiva(orgAId, userAId);
        String schedAId = insertarSchedule(orgAId, siteAId, "2026-10-05");
        String draftAId = insertarScheduleDraft(orgAId, schedAId, "BORRADOR", null, null, userAId);

        String orgBId = insertarOrganizacion("ORG-ISO-B", "Org Isolation B");
        String siteBId = insertarSede(orgBId, "SED-B", "Sede B");
        String posBId = insertarPuesto(orgBId, "PST-B", "Puesto B");
        String empBId = insertarEmpleado(orgBId, "EMP-B", "Empleado B", posBId, siteBId);
        String userBId = insertarUsuario(orgBId, "userB@iso.com", "User B", "ADMINISTRADOR");
        String policyBId = insertarPoliticaActiva(orgBId, userBId);

        // 1. fk_schedules_site: Org A intentando apuntar a site de Org B
        assertThatThrownBy(() ->
                insertarSchedule(orgAId, siteBId, "2026-10-12")
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_schedules_site");

        // 2. fk_draft_assignments_employee: Org A intentando asignar empleado de Org B
        Instant start = Instant.parse("2026-10-05T08:00:00Z");
        Instant end = Instant.parse("2026-10-05T16:00:00Z");
        String posAId = insertarPuesto(orgAId, "PST-A", "Puesto A");
        assertThatThrownBy(() ->
                insertarDraftAssignment(orgAId, schedAId, draftAId, null, null, empBId, posAId, start, end)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_draft_assignments_employee");

        // 3. fk_draft_assignments_position: Org A intentando asignar puesto de Org B
        String empAId = insertarEmpleado(orgAId, "EMP-A", "Empleado A", posAId, siteAId);
        assertThatThrownBy(() ->
                insertarDraftAssignment(orgAId, schedAId, draftAId, null, null, empAId, posBId, start, end)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_draft_assignments_position");

        // 4. fk_assignment_revisions_employee & position
        String assignAId = insertarAssignment(orgAId, schedAId);
        assertThatThrownBy(() ->
                insertarAssignmentRevision(orgAId, schedAId, assignAId, 1, empBId, posAId, start, end, "Snapshot", "Snapshot", userAId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_assignment_revisions_employee");

        assertThatThrownBy(() ->
                insertarAssignmentRevision(orgAId, schedAId, assignAId, 1, empAId, posBId, start, end, "Snapshot", "Snapshot", userAId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_assignment_revisions_position");

        // 5. fk_planning_change_sets_policy_version: Org A apuntando a politica de Org B
        byte[] fp = sha256("test-fp");
        assertThatThrownBy(() ->
                insertarChangeSet(orgAId, schedAId, "PUBLICACION_BORRADOR", userAId, policyBId, draftAId, "Motivo", false, fp)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_planning_change_sets_policy_version");

        // 6. fk_schedule_publications_policy_version: Org A apuntando a politica de Org B
        String changeSetAId = insertarChangeSet(orgAId, schedAId, "PUBLICACION_BORRADOR", userAId, policyAId, draftAId, "Motivo", false, fp);
        assertThatThrownBy(() ->
                insertarSchedulePublication(orgAId, schedAId, 1, null, policyBId, "America/Bogota", "INICIAL", changeSetAId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_schedule_publications_policy_version");

        // 7. fk_schedule_publications_change_set: policyVersion no coincide con la del changeSet
        String policyA2Id = insertarSegundaPolitica(orgAId, userAId);
        assertThatThrownBy(() ->
                insertarSchedulePublication(orgAId, schedAId, 1, null, policyA2Id, "America/Bogota", "INICIAL", changeSetAId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_schedule_publications_change_set");
    }

    // =========================================================================
    // 4. Escenarios negativos de mismo tenant, distinta semana (W1 vs W2)
    // =========================================================================

    @Test
    @DisplayName("4. Mismo tenant: rechazo de referencias cruzadas entre distintas semanas (W1 vs W2)")
    void rechazoReferenciasCruzadasMismoTenantDistintaSemana() {
        String orgId = insertarOrganizacion("ORG-SAME-01", "Org Same Tenant");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Puesto 1");
        String empId = insertarEmpleado(orgId, "EMP-01", "Empleado 1", posId, siteId);
        String userId = insertarUsuario(orgId, "user@same.com", "User Same", "ADMINISTRADOR");
        String policyId = insertarPoliticaActiva(orgId, userId);

        String schedW1 = insertarSchedule(orgId, siteId, "2026-10-05");
        String draftW1 = insertarScheduleDraft(orgId, schedW1, "BORRADOR", null, null, userId);

        String schedW2 = insertarSchedule(orgId, siteId, "2026-10-12");
        String draftW2 = insertarScheduleDraft(orgId, schedW2, "BORRADOR", null, null, userId);

        // Publicar W1
        Instant start = Instant.parse("2026-10-05T08:00:00Z");
        Instant end = Instant.parse("2026-10-05T16:00:00Z");
        byte[] fp = sha256("fp-w1");
        String csW1 = insertarChangeSet(orgId, schedW1, "PUBLICACION_BORRADOR", userId, policyId, draftW1, "Publicacion W1", false, fp);
        String assignW1 = insertarAssignment(orgId, schedW1);
        String revW1 = insertarAssignmentRevision(orgId, schedW1, assignW1, 1, empId, posId, start, end, "Emp", "Pos", userId);
        String pubW1 = insertarSchedulePublication(orgId, schedW1, 1, null, policyId, "America/Bogota", "INICIAL", csW1);
        insertarPublicationAssignment(orgId, schedW1, pubW1, assignW1, revW1);

        // 1. fk_planning_change_sets_source_draft: changeSet en W2 apuntando a borrador de W1
        assertThatThrownBy(() ->
                insertarChangeSet(orgId, schedW2, "PUBLICACION_BORRADOR", userId, policyId, draftW1, "Cruzado", false, fp)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_planning_change_sets_source_draft");

        // 2. fk_schedule_publications_change_set: publicacion en W2 asociando changeSet de W1
        String csCrossW1 = insertarChangeSet(orgId, schedW1, "PUBLICACION_BORRADOR", userId, policyId, draftW1, "ChangeSet libre en W1", false, fp);
        assertThatThrownBy(() ->
                insertarSchedulePublication(orgId, schedW2, 1, null, policyId, "America/Bogota", "INICIAL", csCrossW1)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_schedule_publications_change_set");

        // 3. fk_draft_assignments_assignment: draft_assignments en W2 apuntando a assignment de W1
        Instant startW2 = Instant.parse("2026-10-12T08:00:00Z");
        Instant endW2 = Instant.parse("2026-10-12T16:00:00Z");
        assertThatThrownBy(() ->
                insertarDraftAssignment(orgId, schedW2, draftW2, assignW1, revW1, empId, posId, startW2, endW2)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_draft_assignments_assignment");

        // 4. fk_pub_assignments_assignment / revision: publication en W2 asociando assignment de W1
        String csW2 = insertarChangeSet(orgId, schedW2, "PUBLICACION_BORRADOR", userId, policyId, draftW2, "Pub W2", false, fp);
        String pubW2 = insertarSchedulePublication(orgId, schedW2, 1, null, policyId, "America/Bogota", "INICIAL", csW2);
        assertThatThrownBy(() ->
                insertarPublicationAssignment(orgId, schedW2, pubW2, assignW1, revW1)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_pub_assignments_assignment");
    }

    // =========================================================================
    // 5. Unicidad de una planificación por sede/semana (uq_schedules_org_site_week)
    // =========================================================================

    @Test
    @DisplayName("5. uq_schedules_org_site_week impide duplicar semana para la misma sede")
    void unicidadSemanaPorSede() {
        String orgAId = insertarOrganizacion("ORG-SCHED-A", "Org A");
        String siteAId = insertarSede(orgAId, "SED-A", "Sede A");
        insertarSchedule(orgAId, siteAId, "2026-10-05");

        // Intento duplicar misma fecha para misma sede y organización
        assertThatThrownBy(() ->
                insertarSchedule(orgAId, siteAId, "2026-10-05")
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_schedules_org_site_week");

        // Mismo week_start permitido para otra organización
        String orgBId = insertarOrganizacion("ORG-SCHED-B", "Org B");
        String siteBId = insertarSede(orgBId, "SED-B", "Sede B");
        String schedBId = insertarSchedule(orgBId, siteBId, "2026-10-05");
        assertThat(schedBId).isNotNull();
    }

    // =========================================================================
    // 6. Unicidad de un único borrador abierto (uix_schedule_drafts_one_open, RN-027)
    // =========================================================================

    @Test
    @DisplayName("6. uix_schedule_drafts_one_open permite múltiples DESCARTADOS pero solo un BORRADOR abierto")
    void unicoBorradorAbierto() {
        String orgId = insertarOrganizacion("ORG-DRAFT-01", "Org Draft 01");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String userId = insertarUsuario(orgId, "user@draft.com", "User Draft", "ADMINISTRADOR");
        String scheduleId = insertarSchedule(orgId, siteId, "2026-10-05");

        // Crear primer borrador abierto
        String draft1Id = insertarScheduleDraft(orgId, scheduleId, "BORRADOR", null, null, userId);
        assertThat(draft1Id).isNotNull();

        // Intentar crear segundo borrador abierto -> falla
        assertThatThrownBy(() ->
                insertarScheduleDraft(orgId, scheduleId, "BORRADOR", null, null, userId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uix_schedule_drafts_one_open");

        // Descartar el primer borrador
        jdbc.update("UPDATE schedule_drafts SET status = 'DESCARTADO' WHERE id = ?", draft1Id);

        // Crear un segundo borrador descartado -> permitido
        String draft2Id = insertarScheduleDraft(orgId, scheduleId, "DESCARTADO", null, null, userId);
        assertThat(draft2Id).isNotNull();

        // Ahora que no hay ninguno en 'BORRADOR', se permite crear uno nuevo en 'BORRADOR'
        String draft3Id = insertarScheduleDraft(orgId, scheduleId, "BORRADOR", null, null, userId);
        assertThat(draft3Id).isNotNull();
    }

    // =========================================================================
    // 7. Checks de lunes, duración de turno, coherencia de borrador y change_type
    // =========================================================================

    @Test
    @DisplayName("7. Validaciones CHECK: semana inicia lunes, duración 15 a 960 min, coherencia de borrador y change_type")
    void restriccionesCheckDominio() {
        String orgId = insertarOrganizacion("ORG-CHK-01", "Org Checks");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Puesto 1");
        String empId = insertarEmpleado(orgId, "EMP-01", "Empleado 1", posId, siteId);
        String userId = insertarUsuario(orgId, "user@chk.com", "User Chk", "ADMINISTRADOR");
        String policyId = insertarPoliticaActiva(orgId, userId);

        // 1. chk_schedules_week_start_monday: martes o domingo rechazados
        assertThatThrownBy(() ->
                insertarSchedule(orgId, siteId, "2026-10-06") // Martes
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_schedules_week_start_monday");

        assertThatThrownBy(() ->
                insertarSchedule(orgId, siteId, "2026-10-04") // Domingo
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_schedules_week_start_monday");

        String scheduleId = insertarSchedule(orgId, siteId, "2026-10-05"); // Lunes válido
        String draftId = insertarScheduleDraft(orgId, scheduleId, "BORRADOR", null, null, userId);

        // 2. chk_draft_assignments_interval: duración de turnos
        Instant base = Instant.parse("2026-10-05T08:00:00Z");

        // Invertido (startsAt >= endsAt)
        assertThatThrownBy(() ->
                insertarDraftAssignment(orgId, scheduleId, draftId, null, null, empId, posId, base, base)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_draft_assignments_interval");

        // 14 minutos (< 15)
        assertThatThrownBy(() ->
                insertarDraftAssignment(orgId, scheduleId, draftId, null, null, empId, posId, base, base.plus(14, ChronoUnit.MINUTES))
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_draft_assignments_interval");

        // 961 minutos (> 960 = 16h)
        assertThatThrownBy(() ->
                insertarDraftAssignment(orgId, scheduleId, draftId, null, null, empId, posId, base, base.plus(961, ChronoUnit.MINUTES))
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_draft_assignments_interval");

        // Turno nocturno que cruza medianoche (22:00 a 06:00 del día siguiente = 8h = 480m) -> Válido
        Instant nightStart = Instant.parse("2026-10-05T22:00:00Z");
        Instant nightEnd = Instant.parse("2026-10-06T06:00:00Z");
        String nightLine = insertarDraftAssignment(orgId, scheduleId, draftId, null, null, empId, posId, nightStart, nightEnd);
        assertThat(nightLine).isNotNull();

        // 3. chk_schedule_drafts_result_coherence: estado y resultado
        assertThatThrownBy(() ->
                insertarScheduleDraft(orgId, scheduleId, "PUBLICADO", null, null, userId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_schedule_drafts_result_coherence");

        // 4. chk_planning_change_sets_type: rechazo de ACTIVACION_POLITICA
        byte[] fp = sha256("test-fp");
        assertThatThrownBy(() ->
                insertarChangeSet(orgId, scheduleId, "ACTIVACION_POLITICA", userId, policyId, draftId, "Prueba", false, fp)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_planning_change_sets_type");
    }

    // =========================================================================
    // 8. Publicación de semana vacía (DEC-03) y reconocimiento de advertencias (DEC-04)
    // =========================================================================

    @Test
    @DisplayName("8. Publicación vacía permitida con motivo (DEC-03) y reconocimientos de advertencia (DEC-04)")
    void publicacionVaciaYAdvertencias() {
        String orgId = insertarOrganizacion("ORG-EMPTY-01", "Org Empty");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Puesto 1");
        String empId = insertarEmpleado(orgId, "EMP-01", "Empleado 1", posId, siteId);
        String userId = insertarUsuario(orgId, "user@empty.com", "User Empty", "ADMINISTRADOR");
        String policyId = insertarPoliticaActiva(orgId, userId);

        String scheduleId = insertarSchedule(orgId, siteId, "2026-10-05");
        String draftId = insertarScheduleDraft(orgId, scheduleId, "BORRADOR", null, null, userId);

        // Publicación vacía sin motivo -> rechazada por chk_planning_change_sets_empty_reason
        byte[] fp = sha256("fp-empty");
        assertThatThrownBy(() ->
                insertarChangeSet(orgId, scheduleId, "PUBLICACION_BORRADOR", userId, policyId, draftId, null, true, fp)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_planning_change_sets_empty_reason");

        // Publicación vacía con motivo -> válida (DEC-03)
        String changeSetId = insertarChangeSet(orgId, scheduleId, "PUBLICACION_BORRADOR", userId, policyId,
                draftId, "Sede cerrada por mantenimiento programado", true, fp);
        String pubId = insertarSchedulePublication(orgId, scheduleId, 1, null, policyId,
                "America/Bogota", "INICIAL", changeSetId);
        assertThat(pubId).isNotNull();

        // Inserción en planning_warning_acknowledgements (DEC-04)
        byte[] wfp = sha256("warning-weekly-minutes");
        String ackId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO planning_warning_acknowledgements (
                    id, organization_id, change_set_id, employee_id, warning_code,
                    period_start, period_end_exclusive, timezone_id_snapshot,
                    planned_minutes, threshold_minutes, warning_fingerprint, context_data
                )
                VALUES (?, ?, ?, ?, 'WEEKLY_PLANNED_MINUTES_EXCEEDED',
                        '2026-10-05', '2026-10-12', 'America/Bogota',
                        2700, 2400, ?, '{"reason":"Cubre guardia"}')
                """, ackId, orgId, changeSetId, empId, wfp);

        Map<String, Object> ackRow = jdbc.queryForMap(
                "SELECT planned_minutes, threshold_minutes, warning_code FROM planning_warning_acknowledgements WHERE id = ?", ackId);
        assertThat(ackRow.get("warning_code")).isEqualTo("WEEKLY_PLANNED_MINUTES_EXCEEDED");
        assertThat(ackRow.get("planned_minutes")).isEqualTo(2700);

        // chk_warning_acks_minutes_exceeded: planned <= threshold rechazado
        String ackInvalidId = UUID.randomUUID().toString();
        byte[] wfp2 = sha256("wfp2");
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO planning_warning_acknowledgements (
                            id, organization_id, change_set_id, employee_id, warning_code,
                            period_start, period_end_exclusive, timezone_id_snapshot,
                            planned_minutes, threshold_minutes, warning_fingerprint
                        )
                        VALUES (?, ?, ?, ?, 'WEEKLY_PLANNED_MINUTES_EXCEEDED',
                                '2026-10-05', '2026-10-12', 'America/Bogota',
                                2400, 2400, ?)
                        """, ackInvalidId, orgId, changeSetId, empId, wfp2)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_warning_acks_minutes_exceeded");
    }

    // =========================================================================
    // 9. Upgrade progresivo desde V17 (target("17") → target("19"))
    // =========================================================================

    @Test
    @DisplayName("9. Upgrade progresivo V17 → V19 preserva datos previos y habilita planificación")
    void upgradeProgresivoV17aV19() {
        try (MSSQLServerContainer isolatedServer =
                     new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                             .acceptLicense()) {
            isolatedServer.start();

            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            ds.setUrl(isolatedServer.getJdbcUrl());
            ds.setUsername(isolatedServer.getUsername());
            ds.setPassword(isolatedServer.getPassword());

            JdbcTemplate isolatedJdbc = new JdbcTemplate(ds);

            // 1. Migrar hasta Flyway target 17
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("17")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            String orgId = "11111111-1111-1111-1111-111111111111";
            String siteId = "22222222-2222-2222-2222-222222222222";
            String posId = "33333333-3333-3333-3333-333333333333";
            String empId = "44444444-4444-4444-4444-444444444444";
            String userId = "55555555-5555-5555-5555-555555555555";
            String policyId = "66666666-6666-6666-6666-666666666666";

            // 2. Insertar grafo relacional previo en V17
            isolatedJdbc.update("""
                    INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                        max_active_employees, max_active_sites)
                    VALUES (?, 'ORG-UPG-17', 'Org Upgrade V17', 'ACTIVA', 'America/Bogota', 15, 1)
                    """, orgId);

            isolatedJdbc.update("""
                    INSERT INTO sites (id, organization_id, code, name, is_active)
                    VALUES (?, ?, 'HQ-17', 'Sede V17', 1)
                    """, siteId, orgId);

            isolatedJdbc.update("""
                    INSERT INTO positions (id, organization_id, code, name, is_active)
                    VALUES (?, ?, 'POS-17', 'Puesto V17', 1)
                    """, posId, orgId);

            isolatedJdbc.update("""
                    INSERT INTO employees (id, organization_id, code, display_name, position_id, site_id, is_active)
                    VALUES (?, ?, 'EMP-17', 'Colaborador V17', ?, ?, 1)
                    """, empId, orgId, posId, siteId);

            isolatedJdbc.update("""
                    INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                    VALUES (?, ?, 'v17user@turnoshift.com', 'v17user@turnoshift.com', 'Usuario V17', 'ADMINISTRADOR', ?)
                    """, userId, orgId, empId);

            isolatedJdbc.update("""
                    INSERT INTO planning_policy_versions (
                        id, organization_id, version_number, minimum_rest_minutes, weekly_warning_minutes,
                        created_by_user_id, change_reason
                    )
                    VALUES (?, ?, 1, 660, 2400, ?, 'Politica preexistente V17')
                    """, policyId, orgId, userId);

            isolatedJdbc.update("""
                    UPDATE organizations
                    SET current_policy_version_id = ?
                    WHERE id = ?
                    """, policyId, orgId);

            String unavId = UUID.randomUUID().toString();
            isolatedJdbc.update("""
                    INSERT INTO employee_unavailabilities (
                        id, organization_id, employee_id, starts_at, ends_at,
                        reason_code, restricted_note, created_by_user_id
                    )
                    VALUES (?, ?, ?, '2026-11-01T00:00:00Z', '2026-11-03T00:00:00Z', 'PERMISO_PERSONAL', 'Permiso', ?)
                    """, unavId, orgId, empId, userId);

            // 3. Migrar aplicando V18 y V19 (target 19)
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("19")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            // 4. Verificar que datos de V1..V17 se conservan
            String empCode = isolatedJdbc.queryForObject(
                    "SELECT code FROM employees WHERE id = ?", String.class, empId);
            assertThat(empCode).isEqualTo("EMP-17");

            String activePolicy = isolatedJdbc.queryForObject(
                    "SELECT current_policy_version_id FROM organizations WHERE id = ?", String.class, orgId);
            assertThat(activePolicy).isEqualToIgnoringCase(policyId);

            // 5. Verificar que se puede crear y publicar un cuadrante sobre los datos preexistentes
            String schedId = UUID.randomUUID().toString();
            isolatedJdbc.update("""
                    INSERT INTO schedules (id, organization_id, site_id, week_start)
                    VALUES (?, ?, ?, '2026-10-05')
                    """, schedId, orgId, siteId);

            String draftId = UUID.randomUUID().toString();
            isolatedJdbc.update("""
                    INSERT INTO schedule_drafts (id, organization_id, schedule_id, status, created_by_user_id)
                    VALUES (?, ?, ?, 'BORRADOR', ?)
                    """, draftId, orgId, schedId, userId);

            byte[] fp = new byte[32];
            String csId = UUID.randomUUID().toString();
            isolatedJdbc.update("""
                    INSERT INTO planning_change_sets (
                        id, organization_id, schedule_id, change_type, actor_user_id, policy_version_id,
                        source_draft_id, reason, empty_schedule_confirmed, validation_fingerprint
                    )
                    VALUES (?, ?, ?, 'PUBLICACION_BORRADOR', ?, ?, ?, 'Publicación post upgrade', 1, ?)
                    """, csId, orgId, schedId, userId, policyId, draftId, fp);

            String pubId = UUID.randomUUID().toString();
            isolatedJdbc.update("""
                    INSERT INTO schedule_publications (
                        id, organization_id, schedule_id, publication_number, policy_version_id,
                        timezone_id_snapshot, origin, change_set_id
                    )
                    VALUES (?, ?, ?, 1, ?, 'America/Bogota', 'INICIAL', ?)
                    """, pubId, orgId, schedId, policyId, csId);

            isolatedJdbc.update("""
                    UPDATE schedule_drafts
                    SET status = 'PUBLICADO', result_publication_id = ?
                    WHERE id = ?
                    """, pubId, draftId);

            isolatedJdbc.update("""
                    UPDATE schedules
                    SET current_publication_id = ?
                    WHERE id = ?
                    """, pubId, schedId);

            String storedPubId = isolatedJdbc.queryForObject(
                    "SELECT current_publication_id FROM schedules WHERE id = ?", String.class, schedId);
            assertThat(storedPubId).isEqualToIgnoringCase(pubId);
        }
    }
}
