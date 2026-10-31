package com.turnoshift.schedule.infrastructure.persistence;

import com.turnoshift.schedule.domain.Assignment;
import com.turnoshift.schedule.domain.AssignmentRevision;
import com.turnoshift.schedule.domain.DraftAssignment;
import com.turnoshift.schedule.domain.PlanningChangeSet;
import com.turnoshift.schedule.domain.PlanningChangeType;
import com.turnoshift.schedule.domain.PublicationOrigin;
import com.turnoshift.schedule.domain.PublishedAssignmentRef;
import com.turnoshift.schedule.domain.PublishedShiftRecord;
import com.turnoshift.schedule.domain.Schedule;
import com.turnoshift.schedule.domain.ScheduleDraft;
import com.turnoshift.schedule.domain.SchedulePublication;
import com.turnoshift.schedule.domain.ShiftInterval;
import com.turnoshift.schedule.domain.ports.ScheduleDraftRepository;
import com.turnoshift.schedule.domain.ports.SchedulePublicationRepository;
import com.turnoshift.schedule.domain.ports.ScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@DisplayName("IT: Adaptador de Consulta de Compromisos Publicados (JdbcPublishedAssignmentCommitmentAdapter)")
class JdbcPublishedAssignmentCommitmentAdapterIT {

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
    private JdbcTemplate jdbc;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleDraftRepository draftRepository;

    @Autowired
    private SchedulePublicationRepository publicationRepository;

    @Autowired
    private JdbcPublishedAssignmentCommitmentAdapter commitmentAdapter;

    private static final ZoneId ORG_ZONE = ZoneId.of("America/Bogota");

    // =========================================================================
    // Helpers de creación de fixture relacional
    // =========================================================================

    private record OrgFixture(
            UUID orgId,
            UUID siteId,
            UUID userId,
            UUID policyId,
            UUID employeeId,
            UUID positionId,
            UUID secondaryPositionId
    ) {}

    private OrgFixture createOrgFixture(String codeSuffix) {
        UUID orgId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        UUID secondaryPositionId = UUID.randomUUID();

        // Organización
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, 'ACTIVA', 'America/Bogota', 10, 1);
                """, orgId, "ORG-" + codeSuffix, "Org " + codeSuffix);

        // Usuario
        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                VALUES (?, ?, ?, ?, ?, 'ADMINISTRADOR', NULL);
                """, userId, orgId, "user-" + codeSuffix + "@test.com", "user-" + codeSuffix + "@test.com", "Admin " + codeSuffix);

        // Política
        jdbc.update("""
                INSERT INTO planning_policy_versions (
                    id, organization_id, version_number, minimum_rest_minutes, weekly_warning_minutes,
                    effective_at, created_by_user_id, change_reason, created_at
                ) VALUES (?, ?, 1, 660, 2400, SYSUTCDATETIME(), ?, 'Politica inicial', SYSUTCDATETIME());
                """, policyId, orgId, userId);
        jdbc.update("UPDATE organizations SET current_policy_version_id = ? WHERE id = ?;", policyId, orgId);

        // Sede
        jdbc.update("""
                INSERT INTO sites (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, ?, 1);
                """, siteId, orgId, "SITE-" + codeSuffix, "Sede " + codeSuffix);

        // Puestos
        jdbc.update("""
                INSERT INTO positions (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, 'Cajero', 1);
                """, positionId, orgId, "POS-CAJ-" + codeSuffix);

        jdbc.update("""
                INSERT INTO positions (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, 'Supervisor', 1);
                """, secondaryPositionId, orgId, "POS-SUP-" + codeSuffix);

        // Empleado
        jdbc.update("""
                INSERT INTO employees (id, organization_id, code, display_name, position_id, site_id, is_active)
                VALUES (?, ?, ?, 'Juan Pérez', ?, ?, 1);
                """, employeeId, orgId, "EMP-" + codeSuffix, positionId, siteId);

        return new OrgFixture(orgId, siteId, userId, policyId, employeeId, positionId, secondaryPositionId);
    }

    private byte[] dummyFingerprint() throws Exception {
        return MessageDigest.getInstance("SHA-256").digest("dummy-fp".getBytes(StandardCharsets.UTF_8));
    }

    private SchedulePublication publishSingleShift(
            OrgFixture fixture,
            LocalDate weekStart,
            Instant shiftStart,
            Instant shiftEnd
    ) throws Exception {
        Schedule schedule = scheduleRepository.createSchedule(Schedule.initial(fixture.orgId(), fixture.siteId(), weekStart));
        ScheduleDraft draft = draftRepository.createDraft(ScheduleDraft.initial(fixture.orgId(), schedule.id(), fixture.userId(), List.of()));

        UUID pubId = UUID.randomUUID();
        UUID changeSetId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();

        PlanningChangeSet changeSet = new PlanningChangeSet(
                changeSetId, fixture.orgId(), schedule.id(), PlanningChangeType.PUBLICACION_BORRADOR,
                fixture.userId(), fixture.policyId(), draft.id(), "Publicación de prueba", false,
                dummyFingerprint(), 1, UUID.randomUUID(), Instant.now()
        );

        Assignment assignment = new Assignment(assignmentId, fixture.orgId(), schedule.id(), Instant.now());
        AssignmentRevision revision = new AssignmentRevision(
                revisionId, fixture.orgId(), schedule.id(), assignmentId, 1,
                fixture.employeeId(), fixture.positionId(), ShiftInterval.of(shiftStart, shiftEnd),
                "Juan Pérez", "Cajero", fixture.userId(), Instant.now()
        );

        PublishedAssignmentRef pubRef = new PublishedAssignmentRef(assignmentId, revisionId);

        SchedulePublication pub = new SchedulePublication(
                pubId, fixture.orgId(), schedule.id(), 1, null, fixture.policyId(),
                ORG_ZONE, PublicationOrigin.INICIAL, changeSetId, Instant.now(), List.of(pubRef)
        );

        return publicationRepository.createPublicationAtomic(
                pub, changeSet, List.of(), List.of(assignment), List.of(revision), List.of(pubRef),
                draft.lockVersion(), schedule.lockVersion()
        );
    }

    // =========================================================================
    // Pruebas
    // =========================================================================

    @Test
    @DisplayName("hasFuturePublishedAssignmentsForEmployee: detecta turnos futuros y turno ya finalizado no bloquea")
    void compromisoColaboradorFuturoYTurnoFinalizadoNoBloquea() throws Exception {
        OrgFixture fix = createOrgFixture("COMP-FUT");
        LocalDate monday = LocalDate.of(2026, 11, 2);
        Instant mondayStart = monday.atStartOfDay(ORG_ZONE).toInstant();
        Instant shiftStart = mondayStart.plus(Duration.ofHours(8));  // 08:00
        Instant shiftEnd = mondayStart.plus(Duration.ofHours(16));   // 16:00

        publishSingleShift(fix, monday, shiftStart, shiftEnd);

        // from antes del fin del turno (08:00 < 16:00) -> ends_at > from es true
        boolean hasCommitmentBeforeEnd = commitmentAdapter.hasFuturePublishedAssignmentsForEmployee(
                fix.orgId(), fix.employeeId(), shiftStart
        );
        assertThat(hasCommitmentBeforeEnd).isTrue();

        // from exactamente al fin del turno (16:00) -> ends_at > from es false
        boolean hasCommitmentAtEnd = commitmentAdapter.hasFuturePublishedAssignmentsForEmployee(
                fix.orgId(), fix.employeeId(), shiftEnd
        );
        assertThat(hasCommitmentAtEnd).isFalse();

        // from después del fin del turno (18:00) -> ends_at > from es false (turno ya finalizado no bloquea)
        boolean hasCommitmentAfterEnd = commitmentAdapter.hasFuturePublishedAssignmentsForEmployee(
                fix.orgId(), fix.employeeId(), shiftEnd.plus(Duration.ofHours(2))
        );
        assertThat(hasCommitmentAfterEnd).isFalse();

        // Colaborador inexistente -> false
        boolean hasCommitmentUnknownEmp = commitmentAdapter.hasFuturePublishedAssignmentsForEmployee(
                fix.orgId(), UUID.randomUUID(), shiftStart
        );
        assertThat(hasCommitmentUnknownEmp).isFalse();
    }

    @Test
    @DisplayName("hasFuturePublishedAssignmentsForEmployeeAndPosition: detecta puesto futuro (starts_at > from) y descarta otros puestos o pasados")
    void compromisoConPuestoAnteriorFuturo() throws Exception {
        OrgFixture fix = createOrgFixture("COMP-POS");
        LocalDate monday = LocalDate.of(2026, 11, 9);
        Instant mondayStart = monday.atStartOfDay(ORG_ZONE).toInstant();
        Instant shiftStart = mondayStart.plus(Duration.ofHours(10)); // 10:00
        Instant shiftEnd = mondayStart.plus(Duration.ofHours(18));   // 18:00

        publishSingleShift(fix, monday, shiftStart, shiftEnd);

        // from antes del inicio (08:00 < 10:00) y puesto coincide -> true
        boolean hasPosFuture = commitmentAdapter.hasFuturePublishedAssignmentsForEmployeeAndPosition(
                fix.orgId(), fix.employeeId(), fix.positionId(), shiftStart.minus(Duration.ofHours(2))
        );
        assertThat(hasPosFuture).isTrue();

        // from igual o posterior al inicio (10:00 no es < 10:00) -> false (ya iniciado o pasado)
        boolean hasPosAtStart = commitmentAdapter.hasFuturePublishedAssignmentsForEmployeeAndPosition(
                fix.orgId(), fix.employeeId(), fix.positionId(), shiftStart
        );
        assertThat(hasPosAtStart).isFalse();

        // Puesto secundario diferente -> false
        boolean hasDifferentPos = commitmentAdapter.hasFuturePublishedAssignmentsForEmployeeAndPosition(
                fix.orgId(), fix.employeeId(), fix.secondaryPositionId(), shiftStart.minus(Duration.ofHours(2))
        );
        assertThat(hasDifferentPos).isFalse();
    }

    @Test
    @DisplayName("findFuturePublishedAssignmentsOverlapping: evalúa solape semiabierto estricto (adyacente no solapa) y vigencia futura")
    void interseccionSemiabierta_turnosAdyacentesNoSolapan() throws Exception {
        OrgFixture fix = createOrgFixture("SEMI-OPEN");
        LocalDate monday = LocalDate.of(2026, 11, 16);
        Instant mondayStart = monday.atStartOfDay(ORG_ZONE).toInstant();
        Instant shiftStart = mondayStart.plus(Duration.ofHours(10)); // 10:00
        Instant shiftEnd = mondayStart.plus(Duration.ofHours(14));   // 14:00

        publishSingleShift(fix, monday, shiftStart, shiftEnd);

        // Configurar adaptador con reloj fijado a las 08:00 (el turno finaliza a las 14:00 en el futuro)
        Clock clockAt08 = Clock.fixed(mondayStart.plus(Duration.ofHours(8)), ZoneId.of("UTC"));
        JdbcPublishedAssignmentCommitmentAdapter customAdapter =
                new JdbcPublishedAssignmentCommitmentAdapter(jdbc, clockAt08);

        // 1. Solapamiento real en ventana intermedia [12:00, 16:00) -> solapa en [12:00, 14:00)
        List<PublishedShiftRecord> overlapping = customAdapter.findFuturePublishedAssignmentsOverlapping(
                fix.orgId(), fix.employeeId(),
                shiftStart.plus(Duration.ofHours(2)), shiftEnd.plus(Duration.ofHours(2))
        );
        assertThat(overlapping).hasSize(1);
        assertThat(overlapping.get(0).startsAt()).isEqualTo(shiftStart);
        assertThat(overlapping.get(0).endsAt()).isEqualTo(shiftEnd);
        assertThat(overlapping.get(0).employeeNameSnapshot()).isEqualTo("Juan Pérez");

        // 2. Intervalo adyacente anterior [06:00, 10:00): termina exactamente cuando inicia el turno -> NO solapa
        List<PublishedShiftRecord> adjacentBefore = customAdapter.findFuturePublishedAssignmentsOverlapping(
                fix.orgId(), fix.employeeId(),
                shiftStart.minus(Duration.ofHours(4)), shiftStart
        );
        assertThat(adjacentBefore).isEmpty();

        // 3. Intervalo adyacente posterior [14:00, 18:00): inicia exactamente cuando termina el turno -> NO solapa
        List<PublishedShiftRecord> adjacentAfter = customAdapter.findFuturePublishedAssignmentsOverlapping(
                fix.orgId(), fix.employeeId(),
                shiftEnd, shiftEnd.plus(Duration.ofHours(4))
        );
        assertThat(adjacentAfter).isEmpty();

        // 4. Intervalo completamente disjunto [16:00, 20:00) -> NO solapa
        List<PublishedShiftRecord> disjoint = customAdapter.findFuturePublishedAssignmentsOverlapping(
                fix.orgId(), fix.employeeId(),
                shiftEnd.plus(Duration.ofHours(2)), shiftEnd.plus(Duration.ofHours(6))
        );
        assertThat(disjoint).isEmpty();
    }

    @Test
    @DisplayName("findFuturePublishedAssignmentsOverlapping: un turno ya finalizado en el pasado no se retorna aunque el intervalo coincida")
    void turnoYaFinalizadoNoSolapa() throws Exception {
        OrgFixture fix = createOrgFixture("PAST-SHIFT");
        LocalDate monday = LocalDate.of(2026, 11, 23);
        Instant mondayStart = monday.atStartOfDay(ORG_ZONE).toInstant();
        Instant shiftStart = mondayStart.plus(Duration.ofHours(10)); // 10:00
        Instant shiftEnd = mondayStart.plus(Duration.ofHours(14));   // 14:00

        publishSingleShift(fix, monday, shiftStart, shiftEnd);

        // Reloj fijado a las 15:00 (el turno finalizó a las 14:00, en el pasado respecto a clock.instant())
        Clock clockAt15 = Clock.fixed(mondayStart.plus(Duration.ofHours(15)), ZoneId.of("UTC"));
        JdbcPublishedAssignmentCommitmentAdapter pastAdapter =
                new JdbcPublishedAssignmentCommitmentAdapter(jdbc, clockAt15);

        // Intervalo idéntico [10:00, 14:00) pero el turno ya finalizó en el pasado -> no cuenta como compromiso futuro
        List<PublishedShiftRecord> pastShifts = pastAdapter.findFuturePublishedAssignmentsOverlapping(
                fix.orgId(), fix.employeeId(), shiftStart, shiftEnd
        );
        assertThat(pastShifts).isEmpty();
    }

    @Test
    @DisplayName("Aislamiento multiempresa: consultas no mezclan organizaciones ni colaboradores cruzados")
    void consultasNoMezclanOrganizaciones() throws Exception {
        OrgFixture fixA = createOrgFixture("ISOL-A");
        OrgFixture fixB = createOrgFixture("ISOL-B");

        LocalDate monday = LocalDate.of(2026, 11, 30);
        Instant mondayStart = monday.atStartOfDay(ORG_ZONE).toInstant();
        Instant shiftStart = mondayStart.plus(Duration.ofHours(8));
        Instant shiftEnd = mondayStart.plus(Duration.ofHours(16));

        publishSingleShift(fixA, monday, shiftStart, shiftEnd);

        // Consultar empA con orgB -> false y lista vacía
        assertThat(commitmentAdapter.hasFuturePublishedAssignmentsForEmployee(
                fixB.orgId(), fixA.employeeId(), shiftStart
        )).isFalse();

        assertThat(commitmentAdapter.hasFuturePublishedAssignmentsForEmployeeAndPosition(
                fixB.orgId(), fixA.employeeId(), fixA.positionId(), shiftStart.minus(Duration.ofHours(1))
        )).isFalse();

        assertThat(commitmentAdapter.findFuturePublishedAssignmentsOverlapping(
                fixB.orgId(), fixA.employeeId(), shiftStart, shiftEnd
        )).isEmpty();

        // Validar que con orgA sí responde true
        assertThat(commitmentAdapter.hasFuturePublishedAssignmentsForEmployee(
                fixA.orgId(), fixA.employeeId(), shiftStart
        )).isTrue();
    }

    @Test
    @DisplayName("soloSeConsideranCurrentPublicationId: borradores no publicados y publicaciones históricas superadas no bloquean")
    void soloSeConsideranCurrentPublicationId_noHistoricasNiBorradores() throws Exception {
        OrgFixture fix = createOrgFixture("CURR-ONLY");
        LocalDate monday = LocalDate.of(2026, 12, 7);
        Instant mondayStart = monday.atStartOfDay(ORG_ZONE).toInstant();
        Instant shiftStart = mondayStart.plus(Duration.ofHours(9));
        Instant shiftEnd = mondayStart.plus(Duration.ofHours(17));

        // Escenario A: Solo existe Schedule y Borrador con líneas, pero NUNCA publicado (current_publication_id es null)
        Schedule schedule = scheduleRepository.createSchedule(Schedule.initial(fix.orgId(), fix.siteId(), monday));
        DraftAssignment draftLine = new DraftAssignment(
                UUID.randomUUID(),
                fix.orgId(),
                schedule.id(),
                UUID.randomUUID(),
                null,
                null,
                fix.employeeId(),
                fix.positionId(),
                ShiftInterval.of(shiftStart, shiftEnd),
                Instant.now(),
                Instant.now()
        );
        ScheduleDraft draft = draftRepository.createDraft(ScheduleDraft.initial(fix.orgId(), schedule.id(), fix.userId(), List.of(draftLine)));

        // Las consultas deben retornar false / vacío porque no hay publicación vigente oficial
        assertThat(commitmentAdapter.hasFuturePublishedAssignmentsForEmployee(
                fix.orgId(), fix.employeeId(), shiftStart.minus(Duration.ofHours(1))
        )).isFalse();
        assertThat(commitmentAdapter.findFuturePublishedAssignmentsOverlapping(
                fix.orgId(), fix.employeeId(), shiftStart, shiftEnd
        )).isEmpty();

        // Escenario B: Se publica la versión 1 conteniendo el turno
        UUID pub1Id = UUID.randomUUID();
        UUID changeSet1Id = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();

        PlanningChangeSet cs1 = new PlanningChangeSet(
                changeSet1Id, fix.orgId(), schedule.id(), PlanningChangeType.PUBLICACION_BORRADOR,
                fix.userId(), fix.policyId(), draft.id(), "Pub 1", false,
                dummyFingerprint(), 1, UUID.randomUUID(), Instant.now()
        );
        Assignment assignment = new Assignment(assignmentId, fix.orgId(), schedule.id(), Instant.now());
        AssignmentRevision revision = new AssignmentRevision(
                revisionId, fix.orgId(), schedule.id(), assignmentId, 1,
                fix.employeeId(), fix.positionId(), ShiftInterval.of(shiftStart, shiftEnd),
                "Juan Pérez", "Cajero", fix.userId(), Instant.now()
        );
        PublishedAssignmentRef pubRef = new PublishedAssignmentRef(assignmentId, revisionId);
        SchedulePublication pub1 = new SchedulePublication(
                pub1Id, fix.orgId(), schedule.id(), 1, null, fix.policyId(),
                ORG_ZONE, PublicationOrigin.INICIAL, changeSet1Id, Instant.now(), List.of(pubRef)
        );

        publicationRepository.createPublicationAtomic(
                pub1, cs1, List.of(), List.of(assignment), List.of(revision), List.of(pubRef),
                draft.lockVersion(), schedule.lockVersion()
        );

        // Ahora pub1 es current_publication_id -> retorna true
        assertThat(commitmentAdapter.hasFuturePublishedAssignmentsForEmployee(
                fix.orgId(), fix.employeeId(), shiftStart.minus(Duration.ofHours(1))
        )).isTrue();
        assertThat(commitmentAdapter.findFuturePublishedAssignmentsOverlapping(
                fix.orgId(), fix.employeeId(), shiftStart, shiftEnd
        )).hasSize(1);

        // Escenario C: Se publica la versión 2 (publicación vacía de cierre/remodelación)
        // La versión 1 pasa a ser histórica y current_publication_id pasa a ser pub2Id
        Schedule updatedSchedule = scheduleRepository.findById(fix.orgId(), schedule.id()).orElseThrow();
        ScheduleDraft draft2 = draftRepository.createDraft(
                ScheduleDraft.initial(fix.orgId(), schedule.id(), fix.userId(), List.of())
        );

        UUID pub2Id = UUID.randomUUID();
        UUID changeSet2Id = UUID.randomUUID();
        PlanningChangeSet cs2 = new PlanningChangeSet(
                changeSet2Id, fix.orgId(), schedule.id(), PlanningChangeType.PUBLICACION_BORRADOR,
                fix.userId(), fix.policyId(), draft2.id(), "Pub 2 vacía", true,
                dummyFingerprint(), 1, UUID.randomUUID(), Instant.now()
        );
        SchedulePublication pub2 = new SchedulePublication(
                pub2Id, fix.orgId(), schedule.id(), 2, pub1Id, fix.policyId(),
                ORG_ZONE, PublicationOrigin.REVISION, changeSet2Id, Instant.now(), List.of()
        );

        publicationRepository.createPublicationAtomic(
                pub2, cs2, List.of(), List.of(), List.of(), List.of(),
                draft2.lockVersion(), updatedSchedule.lockVersion()
        );

        // Ahora pub2 es la vigente y está vacía; pub1 ya es histórica -> compromiso no debe detectarse
        assertThat(commitmentAdapter.hasFuturePublishedAssignmentsForEmployee(
                fix.orgId(), fix.employeeId(), shiftStart.minus(Duration.ofHours(1))
        )).isFalse();
        assertThat(commitmentAdapter.findFuturePublishedAssignmentsOverlapping(
                fix.orgId(), fix.employeeId(), shiftStart, shiftEnd
        )).isEmpty();
    }
}
