package com.turnoshift.schedule.infrastructure.persistence;

import com.turnoshift.schedule.domain.Schedule;
import com.turnoshift.schedule.domain.ScheduleConcurrentModificationException;
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

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: JdbcScheduleRepository — persistencia de cuadrantes semanales")
class JdbcScheduleRepositoryIT {

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

    private UUID orgId;
    private UUID siteId;
    private LocalDate mondayWeek;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        mondayWeek = LocalDate.of(2026, 11, 2);

        // Crear organización
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, 'ACTIVA', 'America/Bogota', 10, 1);
                """, orgId, "ORG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "Org Test");

        // Crear sede
        jdbc.update("""
                INSERT INTO sites (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, ?, 1);
                """, siteId, orgId, "SITE-1", "Sede Principal");
    }

    @Test
    @DisplayName("Crear y consultar schedule por ID y por sede/semana")
    void crearYConsultarSchedule() {
        Schedule newSchedule = Schedule.initial(orgId, siteId, mondayWeek);
        Schedule created = scheduleRepository.createSchedule(newSchedule);

        assertThat(created.id()).isEqualTo(newSchedule.id());
        assertThat(created.organizationId()).isEqualTo(orgId);
        assertThat(created.siteId()).isEqualTo(siteId);
        assertThat(created.weekStart()).isEqualTo(mondayWeek);
        assertThat(created.currentPublicationId()).isNull();
        assertThat(created.lockVersion()).isEqualTo(0L);
        assertThat(created.createdAt()).isNotNull();

        // Consulta por ID
        Optional<Schedule> byId = scheduleRepository.findById(orgId, created.id());
        assertThat(byId).isPresent();
        assertThat(byId.get().weekStart()).isEqualTo(mondayWeek);

        // Consulta por Sede y Semana
        Optional<Schedule> bySiteWeek = scheduleRepository.findBySiteAndWeek(orgId, siteId, mondayWeek);
        assertThat(bySiteWeek).isPresent();
        assertThat(bySiteWeek.get().id()).isEqualTo(created.id());
    }

    @Test
    @DisplayName("Aislamiento multi-tenant: schedule no es visible para otra organización")
    void aislamientoMultiTenant() {
        Schedule created = scheduleRepository.createSchedule(Schedule.initial(orgId, siteId, mondayWeek));

        UUID otherOrgId = UUID.randomUUID();
        Optional<Schedule> crossQuery = scheduleRepository.findById(otherOrgId, created.id());
        assertThat(crossQuery).isEmpty();

        Optional<Schedule> crossSiteQuery = scheduleRepository.findBySiteAndWeek(otherOrgId, siteId, mondayWeek);
        assertThat(crossSiteQuery).isEmpty();
    }

    @Test
    @DisplayName("Actualización concurrente de publicación vigente con lock_version desfasada es rechazada")
    void rechazoConcurrenciaLockVersion() {
        Schedule created = scheduleRepository.createSchedule(Schedule.initial(orgId, siteId, mondayWeek));

        UUID fakePubId = UUID.randomUUID();

        // Intento con expectedLockVersion errónea (999 en lugar de 0)
        assertThatThrownBy(() ->
                scheduleRepository.updateCurrentPublication(orgId, created.id(), fakePubId, 999L)
        ).isInstanceOf(ScheduleConcurrentModificationException.class);
    }
}
