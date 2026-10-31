package com.turnoshift.schedule.infrastructure.persistence;

import com.turnoshift.schedule.domain.DraftAssignment;
import com.turnoshift.schedule.domain.DraftStatus;
import com.turnoshift.schedule.domain.Schedule;
import com.turnoshift.schedule.domain.ScheduleConcurrentModificationException;
import com.turnoshift.schedule.domain.ScheduleDraft;
import com.turnoshift.schedule.domain.ShiftInterval;
import com.turnoshift.schedule.domain.ports.ScheduleDraftRepository;
import com.turnoshift.schedule.domain.ports.ScheduleRepository;
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
@DisplayName("IT: JdbcScheduleDraftRepository — persistencia y concurrencia optimista de borradores")
class JdbcScheduleDraftRepositoryIT {

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

    private UUID orgId;
    private UUID siteId;
    private UUID userId;
    private UUID employeeId;
    private UUID positionId;
    private Schedule schedule;
    private LocalDate mondayWeek;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        userId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        mondayWeek = LocalDate.of(2026, 11, 2);

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

        // Schedule
        schedule = scheduleRepository.createSchedule(Schedule.initial(orgId, siteId, mondayWeek));
    }

    @Test
    @DisplayName("Crear y consultar borrador abierto con asignaciones")
    void crearYConsultarBorrador() {
        Instant mondayStart = mondayWeek.atStartOfDay(ZoneId.of("America/Bogota")).toInstant();
        DraftAssignment a1 = new DraftAssignment(
                UUID.randomUUID(), orgId, schedule.id(), UUID.randomUUID(), null, null,
                employeeId, positionId,
                ShiftInterval.of(mondayStart.plus(Duration.ofHours(8)), mondayStart.plus(Duration.ofHours(16))),
                Instant.now(), Instant.now()
        );

        ScheduleDraft draft = ScheduleDraft.initial(orgId, schedule.id(), userId, List.of(a1));
        ScheduleDraft created = draftRepository.createDraft(draft);

        assertThat(created.id()).isEqualTo(draft.id());
        assertThat(created.status()).isEqualTo(DraftStatus.BORRADOR);
        assertThat(created.lockVersion()).isEqualTo(0L);
        assertThat(created.assignments()).hasSize(1);

        // Consulta borrador abierto
        Optional<ScheduleDraft> open = draftRepository.findOpenDraftBySchedule(orgId, schedule.id());
        assertThat(open).isPresent();
        assertThat(open.get().id()).isEqualTo(draft.id());
        assertThat(open.get().assignments()).hasSize(1);
    }

    @Test
    @DisplayName("Mutaciones de asignaciones avanzan lock_version del borrador e impiden colisiones concurrentes")
    void mutacionesAvanzanLockVersionYProtegenConcurrencia() {
        Instant mondayStart = mondayWeek.atStartOfDay(ZoneId.of("America/Bogota")).toInstant();
        ScheduleDraft draft = ScheduleDraft.initial(orgId, schedule.id(), userId, List.of());
        ScheduleDraft created = draftRepository.createDraft(draft);
        assertThat(created.lockVersion()).isEqualTo(0L);

        // 1. Insertar línea con lock_version esperada 0 -> avanza a 1
        DraftAssignment a1 = new DraftAssignment(
                UUID.randomUUID(), orgId, schedule.id(), created.id(), null, null,
                employeeId, positionId,
                ShiftInterval.of(mondayStart.plus(Duration.ofHours(8)), mondayStart.plus(Duration.ofHours(16))),
                Instant.now(), Instant.now()
        );
        DraftAssignment inserted = draftRepository.insertAssignment(a1, 0L);
        assertThat(inserted.id()).isEqualTo(a1.id());

        Optional<ScheduleDraft> dAfterInsert = draftRepository.findById(orgId, created.id());
        assertThat(dAfterInsert.orElseThrow().lockVersion()).isEqualTo(1L);

        // 2. Intento de actualizar línea con lock_version desfasada (0 en vez de 1) -> falla
        DraftAssignment modifiedA1 = new DraftAssignment(
                inserted.id(), orgId, schedule.id(), created.id(), null, null,
                employeeId, positionId,
                ShiftInterval.of(mondayStart.plus(Duration.ofHours(9)), mondayStart.plus(Duration.ofHours(17))),
                Instant.now(), Instant.now()
        );
        assertThatThrownBy(() -> draftRepository.updateAssignment(modifiedA1, 0L))
                .isInstanceOf(ScheduleConcurrentModificationException.class);

        // 3. Actualizar con lock_version correcta (1) -> avanza a 2
        draftRepository.updateAssignment(modifiedA1, 1L);
        Optional<ScheduleDraft> dAfterUpdate = draftRepository.findById(orgId, created.id());
        assertThat(dAfterUpdate.orElseThrow().lockVersion()).isEqualTo(2L);

        // 4. Eliminar asignación con lock_version 2 -> avanza a 3
        draftRepository.deleteAssignment(orgId, created.id(), inserted.id(), 2L);
        Optional<ScheduleDraft> dAfterDelete = draftRepository.findById(orgId, created.id());
        assertThat(dAfterDelete.orElseThrow().lockVersion()).isEqualTo(3L);
        assertThat(dAfterDelete.get().assignments()).isEmpty();
    }

    @Test
    @DisplayName("Descartar borrador cambia su estado a DESCARTADO y avanza lock_version")
    void descartarBorrador() {
        ScheduleDraft draft = ScheduleDraft.initial(orgId, schedule.id(), userId, List.of());
        ScheduleDraft created = draftRepository.createDraft(draft);

        draftRepository.updateDraftStatus(orgId, created.id(), DraftStatus.DESCARTADO, null, 0L);

        Optional<ScheduleDraft> loaded = draftRepository.findById(orgId, created.id());
        assertThat(loaded.orElseThrow().status()).isEqualTo(DraftStatus.DESCARTADO);
        assertThat(loaded.get().lockVersion()).isEqualTo(1L);

        // Ya no hay borrador abierto
        Optional<ScheduleDraft> open = draftRepository.findOpenDraftBySchedule(orgId, schedule.id());
        assertThat(open).isEmpty();
    }
}
