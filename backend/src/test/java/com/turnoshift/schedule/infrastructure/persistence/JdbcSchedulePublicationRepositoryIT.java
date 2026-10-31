package com.turnoshift.schedule.infrastructure.persistence;

import com.turnoshift.schedule.domain.Assignment;
import com.turnoshift.schedule.domain.AssignmentRevision;
import com.turnoshift.schedule.domain.DraftStatus;
import com.turnoshift.schedule.domain.PlanningChangeSet;
import com.turnoshift.schedule.domain.PlanningChangeType;
import com.turnoshift.schedule.domain.PlanningWarningAcknowledgement;
import com.turnoshift.schedule.domain.PublicationOrigin;
import com.turnoshift.schedule.domain.PublishedAssignmentRef;
import com.turnoshift.schedule.domain.PublishedShiftRecord;
import com.turnoshift.schedule.domain.Schedule;
import com.turnoshift.schedule.domain.ScheduleConcurrentModificationException;
import com.turnoshift.schedule.domain.ScheduleDraft;
import com.turnoshift.schedule.domain.SchedulePublication;
import com.turnoshift.schedule.domain.ShiftInterval;
import com.turnoshift.schedule.domain.ports.ScheduleDraftRepository;
import com.turnoshift.schedule.domain.ports.SchedulePublicationRepository;
import com.turnoshift.schedule.domain.ports.ScheduleRepository;
import com.turnoshift.schedule.domain.validation.WarningFingerprintCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: JdbcSchedulePublicationRepository — publicación atómica y claves foráneas circulares V19")
class JdbcSchedulePublicationRepositoryIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    @DynamicPropertySource
    static void configurarDataSource(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleDraftRepository draftRepository;

    @Autowired
    private SchedulePublicationRepository publicationRepository;

    private UUID orgId;
    private UUID siteId;
    private UUID userId;
    private UUID employeeId;
    private UUID positionId;
    private UUID policyId;
    private Schedule schedule;
    private ScheduleDraft draft;
    private LocalDate mondayWeek;
    private ZoneId orgZone;

    @BeforeEach
    void setUp() throws Exception {
        orgId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        userId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        policyId = UUID.randomUUID();
        mondayWeek = LocalDate.of(2026, 11, 2);
        orgZone = ZoneId.of("America/Bogota");

        // Org
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, 'ACTIVA', 'America/Bogota', 10, 1);
                """, orgId, "ORG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "Org Test");

        // User
        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                VALUES (?, ?, ?, ?, ?, 'ADMINISTRADOR', NULL);
                """, userId, orgId, "planner@test.com", "planner@test.com", "Planificador Test");

        // Policy
        jdbc.update("""
                INSERT INTO planning_policy_versions (
                    id, organization_id, version_number, minimum_rest_minutes, weekly_warning_minutes,
                    effective_at, created_by_user_id, change_reason, created_at
                ) VALUES (?, ?, 1, 660, 2400, SYSUTCDATETIME(), ?, 'Politica inicial', SYSUTCDATETIME());
                """, policyId, orgId, userId);

        jdbc.update("UPDATE organizations SET current_policy_version_id = ? WHERE id = ?;", policyId, orgId);

        // Site
        jdbc.update("""
                INSERT INTO sites (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, ?, 1);
                """, siteId, orgId, "SITE-1", "Sede Central");

        // Position
        jdbc.update("""
                INSERT INTO positions (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, ?, 1);
                """, positionId, orgId, "POS-1", "Cajero");

        // Employee
        jdbc.update("""
                INSERT INTO employees (id, organization_id, code, display_name, position_id, site_id, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 1);
                """, employeeId, orgId, "EMP-1", "Juan Pérez", positionId, siteId);

        // Schedule y Borrador inicial
        schedule = scheduleRepository.createSchedule(Schedule.initial(orgId, siteId, mondayWeek));
        draft = draftRepository.createDraft(ScheduleDraft.initial(orgId, schedule.id(), userId, List.of()));
    }

    private byte[] dummyFingerprint() throws Exception {
        return MessageDigest.getInstance("SHA-256").digest("dummy-fingerprint".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Publicación atómica inicial exitosa: satisface FKs circulares de V19 y actualiza punteros")
    void publicacionInicialAtomic() throws Exception {
        Instant mondayStart = mondayWeek.atStartOfDay(orgZone).toInstant();
        Instant shiftStart = mondayStart.plus(Duration.ofHours(8));
        Instant shiftEnd = mondayStart.plus(Duration.ofHours(16));

        UUID pubId = UUID.randomUUID();
        UUID changeSetId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();

        PlanningChangeSet changeSet = new PlanningChangeSet(
                changeSetId, orgId, schedule.id(), PlanningChangeType.PUBLICACION_BORRADOR,
                userId, policyId, draft.id(), "Primera versión oficial", false,
                dummyFingerprint(), 1, UUID.randomUUID(), Instant.now()
        );

        Assignment assignment = new Assignment(assignmentId, orgId, schedule.id(), Instant.now());
        AssignmentRevision revision = new AssignmentRevision(
                revisionId, orgId, schedule.id(), assignmentId, 1,
                employeeId, positionId, ShiftInterval.of(shiftStart, shiftEnd),
                "Juan Pérez", "Cajero", userId, Instant.now()
        );

        PublishedAssignmentRef pubRef = new PublishedAssignmentRef(assignmentId, revisionId);

        SchedulePublication pub = new SchedulePublication(
                pubId, orgId, schedule.id(), 1, null, policyId,
                orgZone, PublicationOrigin.INICIAL, changeSetId, Instant.now(), List.of(pubRef)
        );

        // Ejecutar publicación atómica
        SchedulePublication result = publicationRepository.createPublicationAtomic(
                pub, changeSet, List.of(), List.of(assignment), List.of(revision), List.of(pubRef),
                0L, 0L
        );

        assertThat(result.id()).isEqualTo(pubId);
        assertThat(result.publicationNumber()).isEqualTo(1);
        assertThat(result.origin()).isEqualTo(PublicationOrigin.INICIAL);

        // Validar que el schedule tiene ahora current_publication_id apuntando a pubId y lock_version = 1
        Schedule updatedSchedule = scheduleRepository.findById(orgId, schedule.id()).orElseThrow();
        assertThat(updatedSchedule.currentPublicationId()).isEqualTo(pubId);
        assertThat(updatedSchedule.lockVersion()).isEqualTo(1L);

        // Validar que el borrador pasó a PUBLICADO con result_publication_id apuntando a pubId y lock_version = 1
        ScheduleDraft updatedDraft = draftRepository.findById(orgId, draft.id()).orElseThrow();
        assertThat(updatedDraft.status()).isEqualTo(DraftStatus.PUBLICADO);
        assertThat(updatedDraft.resultPublicationId()).isEqualTo(pubId);
        assertThat(updatedDraft.lockVersion()).isEqualTo(1L);

        // Consultar turnos publicados en la ventana
        List<PublishedShiftRecord> publishedShifts = publicationRepository.findPublishedShiftsInInterval(
                orgId, siteId, shiftStart.minus(Duration.ofHours(1)), shiftEnd.plus(Duration.ofHours(1))
        );
        assertThat(publishedShifts).hasSize(1);
        assertThat(publishedShifts.get(0).employeeNameSnapshot()).isEqualTo("Juan Pérez");
    }

    @Test
    @DisplayName("Publicación atómica vacía conforme a DEC-03: se permite con motivo explícito")
    void publicacionVaciaConformeDEC03() throws Exception {
        UUID pubId = UUID.randomUUID();
        UUID changeSetId = UUID.randomUUID();

        PlanningChangeSet changeSet = new PlanningChangeSet(
                changeSetId, orgId, schedule.id(), PlanningChangeType.PUBLICACION_BORRADOR,
                userId, policyId, draft.id(), "Semana de remodelación total de la sede", true,
                dummyFingerprint(), 1, UUID.randomUUID(), Instant.now()
        );

        SchedulePublication pub = new SchedulePublication(
                pubId, orgId, schedule.id(), 1, null, policyId,
                orgZone, PublicationOrigin.INICIAL, changeSetId, Instant.now(), List.of()
        );

        SchedulePublication result = publicationRepository.createPublicationAtomic(
                pub, changeSet, List.of(), List.of(), List.of(), List.of(),
                0L, 0L
        );

        assertThat(result.id()).isEqualTo(pubId);
        assertThat(result.assignments()).isEmpty();

        Schedule updatedSchedule = scheduleRepository.findById(orgId, schedule.id()).orElseThrow();
        assertThat(updatedSchedule.currentPublicationId()).isEqualTo(pubId);
    }

    @Test
    @DisplayName("Publicación con reconocimiento de advertencias por exceso semanal conforme a DEC-04")
    void publicacionConReconocimientoAdvertencias() throws Exception {
        UUID pubId = UUID.randomUUID();
        UUID changeSetId = UUID.randomUUID();
        UUID ackId = UUID.randomUUID();

        byte[] warningFp = WarningFingerprintCalculator.calculate(
                orgId, employeeId, mondayWeek, mondayWeek.plusDays(7), 2700, 2400
        );

        PlanningChangeSet changeSet = new PlanningChangeSet(
                changeSetId, orgId, schedule.id(), PlanningChangeType.PUBLICACION_BORRADOR,
                userId, policyId, draft.id(), "Horas extras autorizadas por gerencia", true,
                dummyFingerprint(), 1, UUID.randomUUID(), Instant.now()
        );

        PlanningWarningAcknowledgement ack = new PlanningWarningAcknowledgement(
                ackId, orgId, changeSetId, employeeId, "WEEKLY_PLANNED_MINUTES_EXCEEDED",
                mondayWeek, mondayWeek.plusDays(7), orgZone, 2700, 2400, warningFp,
                1, null, Instant.now()
        );

        SchedulePublication pub = new SchedulePublication(
                pubId, orgId, schedule.id(), 1, null, policyId,
                orgZone, PublicationOrigin.INICIAL, changeSetId, Instant.now(), List.of()
        );

        SchedulePublication result = publicationRepository.createPublicationAtomic(
                pub, changeSet, List.of(ack), List.of(), List.of(), List.of(),
                0L, 0L
        );

        assertThat(result.id()).isEqualTo(pubId);

        // Verificar que el reconocimiento quedó persistido
        Integer ackCount = jdbc.queryForObject(
                "SELECT COUNT(1) FROM planning_warning_acknowledgements WHERE organization_id = ? AND id = ?;",
                Integer.class, orgId, ackId
        );
        assertThat(ackCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Rechazo por concurrencia si expectedDraftLockVersion está desfasada")
    void rechazoConcurrenciaDraftLockVersionDesfasada() throws Exception {
        UUID pubId = UUID.randomUUID();
        UUID changeSetId = UUID.randomUUID();

        PlanningChangeSet changeSet = new PlanningChangeSet(
                changeSetId, orgId, schedule.id(), PlanningChangeType.PUBLICACION_BORRADOR,
                userId, policyId, draft.id(), "Semana vacía", true,
                dummyFingerprint(), 1, UUID.randomUUID(), Instant.now()
        );

        SchedulePublication pub = new SchedulePublication(
                pubId, orgId, schedule.id(), 1, null, policyId,
                orgZone, PublicationOrigin.INICIAL, changeSetId, Instant.now(), List.of()
        );

        // Pasamos expectedDraftLockVersion = 99L (cuando es 0L)
        assertThatThrownBy(() ->
                publicationRepository.createPublicationAtomic(
                        pub, changeSet, List.of(), List.of(), List.of(), List.of(),
                        99L, 0L
                )
        ).isInstanceOf(ScheduleConcurrentModificationException.class);
    }
}
