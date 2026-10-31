package com.turnoshift.unavailability.infrastructure.persistence;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.RestrictedNote;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: Adaptador JDBC de persistencia de indisponibilidades en SQL Server")
class JdbcEmployeeUnavailabilityRepositoryIT {

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

    private JdbcEmployeeUnavailabilityRepository repository;

    private UUID orgId;
    private UUID empId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        repository = new JdbcEmployeeUnavailabilityRepository(jdbc);
        orgId = UUID.fromString(insertarOrganizacion("ORG-REPO-1", "Org Repo 1"));
        String siteId = insertarSede(orgId.toString(), "SED-R1", "Sede R1");
        String posId = insertarPuesto(orgId.toString(), "POS-R1", "Puesto R1");
        empId = UUID.fromString(insertarEmpleado(orgId.toString(), "EMP-R1", "Carlos R", posId, siteId));
        userId = UUID.fromString(insertarUsuario(orgId.toString(), "user.r1@turnoshift.com", "User R1"));
    }

    @Test
    @DisplayName("1. Inserción con insert(...) genera created_at y updated_at con SYSUTCDATETIME() y OUTPUT inserted.*")
    void insercionGeneraTimestampsAutoritativos() {
        Instant t0 = Instant.now().minus(2, ChronoUnit.SECONDS);
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-11-01T08:00:00Z"),
                Instant.parse("2026-11-01T16:00:00Z")
        );

        EmployeeUnavailability nueva = EmployeeUnavailability.create(
                orgId, empId, interval, UnavailabilityReason.MEDICO,
                RestrictedNote.of("Nota privada de salud"), userId, Instant.now()
        );

        EmployeeUnavailability insertada = repository.insert(nueva);

        assertThat(insertada).isNotNull();
        assertThat(insertada.id()).isEqualTo(nueva.id());
        assertThat(insertada.organizationId()).isEqualTo(orgId);
        assertThat(insertada.employeeId()).isEqualTo(empId);
        assertThat(insertada.interval().startsAt()).isEqualTo(interval.startsAt());
        assertThat(insertada.interval().endsAt()).isEqualTo(interval.endsAt());
        assertThat(insertada.reason()).contains(UnavailabilityReason.MEDICO);
        assertThat(insertada.restrictedNote().get().value()).isEqualTo("Nota privada de salud");
        assertThat(insertada.createdByUserId()).isEqualTo(userId);
        assertThat(insertada.lockVersion()).isEqualTo(0L);
        assertThat(insertada.cancellationInfo()).isEmpty();
        assertThat(insertada.isActive()).isTrue();
        assertThat(insertada.isCancelled()).isFalse();

        // Verificar que los timestamps fueron generados por SQL Server (SYSUTCDATETIME)
        assertThat(insertada.createdAt()).isNotNull().isAfter(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(insertada.updatedAt()).isNotNull().isAfter(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("2. Aislamiento multi-tenant: findById con otra organización retorna Optional.empty()")
    void aislamientoMultiTenantFindById() {
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-11-01T08:00:00Z"),
                Instant.parse("2026-11-01T16:00:00Z")
        );
        EmployeeUnavailability insertada = repository.insert(EmployeeUnavailability.create(
                orgId, empId, interval, UnavailabilityReason.VACACIONES, null, userId, Instant.now()
        ));

        UUID otraOrgId = UUID.randomUUID();
        Optional<EmployeeUnavailability> encontrada = repository.findById(otraOrgId, insertada.id());

        assertThat(encontrada).isEmpty();
    }

    @Test
    @DisplayName("3. Actualización condicional por lock_version con OUTPUT inserted.*")
    void actualizacionCondicionalLockVersion() {
        TimeInterval originalInterval = TimeInterval.of(
                Instant.parse("2026-11-01T08:00:00Z"),
                Instant.parse("2026-11-01T16:00:00Z")
        );
        EmployeeUnavailability insertada = repository.insert(EmployeeUnavailability.create(
                orgId, empId, originalInterval, UnavailabilityReason.VACACIONES, null, userId, Instant.now()
        ));

        TimeInterval nuevoIntervalo = TimeInterval.of(
                Instant.parse("2026-11-02T08:00:00Z"),
                Instant.parse("2026-11-02T16:00:00Z")
        );
        EmployeeUnavailability aModificar = insertada.update(
                nuevoIntervalo, UnavailabilityReason.MEDICO, RestrictedNote.of("Actualizada"), Instant.now()
        );

        // Actualización exitosa con expectedLockVersion = 0
        Optional<EmployeeUnavailability> actualizadaOpt = repository.update(aModificar, 0L);
        assertThat(actualizadaOpt).isPresent();
        EmployeeUnavailability actualizada = actualizadaOpt.get();
        assertThat(actualizada.lockVersion()).isEqualTo(1L);
        assertThat(actualizada.interval().startsAt()).isEqualTo(nuevoIntervalo.startsAt());
        assertThat(actualizada.reason()).contains(UnavailabilityReason.MEDICO);
        assertThat(actualizada.restrictedNote().get().value()).isEqualTo("Actualizada");

        // Intento concurrente con expectedLockVersion desactualizada (0L) falla
        Optional<EmployeeUnavailability> conflictoOpt = repository.update(aModificar, 0L);
        assertThat(conflictoOpt).isEmpty();
    }

    @Test
    @DisplayName("4. Cancelación condicional por lock_version y validación de cancelled_at generado en BD")
    void cancelacionCondicionalLockVersion() {
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-11-01T08:00:00Z"),
                Instant.parse("2026-11-01T16:00:00Z")
        );
        EmployeeUnavailability insertada = repository.insert(EmployeeUnavailability.create(
                orgId, empId, interval, UnavailabilityReason.VACACIONES, null, userId, Instant.now()
        ));

        Instant t0 = Instant.now().minus(2, ChronoUnit.SECONDS);

        // Cancelar exitosamente
        Optional<EmployeeUnavailability> canceladaOpt = repository.cancel(orgId, insertada.id(), userId, 0L);
        assertThat(canceladaOpt).isPresent();
        EmployeeUnavailability cancelada = canceladaOpt.get();
        assertThat(cancelada.isCancelled()).isTrue();
        assertThat(cancelada.isActive()).isFalse();
        assertThat(cancelada.lockVersion()).isEqualTo(1L);
        assertThat(cancelada.cancellationInfo()).isPresent();
        assertThat(cancelada.cancellationInfo().get().cancelledByUserId()).isEqualTo(userId);
        assertThat(cancelada.cancellationInfo().get().cancelledAt()).isNotNull().isAfter(Instant.parse("2026-01-01T00:00:00Z"));

        // Reintentar cancelar falla (ya cancelada, cancelled_at IS NOT NULL)
        Optional<EmployeeUnavailability> reintentoOpt = repository.cancel(orgId, insertada.id(), userId, 1L);
        assertThat(reintentoOpt).isEmpty();
    }

    @Test
    @DisplayName("5. Consultas de rango: exclusión e inclusión de canceladas por empleado y por organización")
    void consultasDeRangoExcluyenEIncluyenCanceladas() {
        Instant s1 = Instant.parse("2026-11-10T08:00:00Z");
        Instant e1 = Instant.parse("2026-11-10T16:00:00Z");
        EmployeeUnavailability activa = repository.insert(EmployeeUnavailability.create(
                orgId, empId, TimeInterval.of(s1, e1), UnavailabilityReason.VACACIONES, null, userId, Instant.now()
        ));

        Instant s2 = Instant.parse("2026-11-12T08:00:00Z");
        Instant e2 = Instant.parse("2026-11-12T16:00:00Z");
        EmployeeUnavailability aCancelar = repository.insert(EmployeeUnavailability.create(
                orgId, empId, TimeInterval.of(s2, e2), UnavailabilityReason.MEDICO, null, userId, Instant.now()
        ));
        repository.cancel(orgId, aCancelar.id(), userId, 0L);

        Instant windowStart = Instant.parse("2026-11-01T00:00:00Z");
        Instant windowEnd = Instant.parse("2026-11-30T23:59:59Z");

        // Por empleado
        List<EmployeeUnavailability> soloActivasEmp = repository.findByEmployeeAndInterval(
                orgId, empId, windowStart, windowEnd, false
        );
        assertThat(soloActivasEmp).extracting(EmployeeUnavailability::id).containsExactly(activa.id());

        List<EmployeeUnavailability> todasEmp = repository.findByEmployeeAndInterval(
                orgId, empId, windowStart, windowEnd, true
        );
        assertThat(todasEmp).extracting(EmployeeUnavailability::id).containsExactlyInAnyOrder(activa.id(), aCancelar.id());

        // Por organización
        List<EmployeeUnavailability> soloActivasOrg = repository.findByOrganizationAndInterval(
                orgId, windowStart, windowEnd, false
        );
        assertThat(soloActivasOrg).extracting(EmployeeUnavailability::id).containsExactly(activa.id());

        List<EmployeeUnavailability> todasOrg = repository.findByOrganizationAndInterval(
                orgId, windowStart, windowEnd, true
        );
        assertThat(todasOrg).extracting(EmployeeUnavailability::id).containsExactlyInAnyOrder(activa.id(), aCancelar.id());
    }

    @Test
    @DisplayName("6. RN-013 Guard SQL Server: update retorna Optional.empty() y no altera la fila si starts_at <= SYSUTCDATETIME()")
    void updateRechazaModificacionSiIntervaloYaInicioPorGuardSqlServer() {
        // Intervalo cuyo inicio ya transcurrió en el pasado
        Instant pastStart = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant futureEnd = Instant.now().plus(2, ChronoUnit.DAYS);
        TimeInterval pastInterval = TimeInterval.of(pastStart, futureEnd);

        EmployeeUnavailability insertada = repository.insert(EmployeeUnavailability.create(
                orgId, empId, pastInterval, UnavailabilityReason.VACACIONES,
                RestrictedNote.of("Nota original"), userId, Instant.now()
        ));

        // Obtener estado original persistido
        Map<String, Object> estadoOriginal = jdbc.queryForMap(
                "SELECT updated_at, lock_version, cancelled_at FROM employee_unavailabilities WHERE id = ?",
                insertada.id().toString()
        );
        Object originalUpdatedAt = estadoOriginal.get("updated_at");

        // Intentar update directo en el repositorio con nueva ventana temporal
        TimeInterval nuevoIntervalo = TimeInterval.of(
                Instant.now().plus(5, ChronoUnit.DAYS),
                Instant.now().plus(6, ChronoUnit.DAYS)
        );
        EmployeeUnavailability aModificar = EmployeeUnavailability.reconstruct(
                insertada.id(), orgId, empId, nuevoIntervalo,
                UnavailabilityReason.MEDICO, RestrictedNote.of("Intento modificar"),
                userId, insertada.createdAt(), insertada.updatedAt(), 0L, null
        );

        Optional<EmployeeUnavailability> resultadoUpdate = repository.update(aModificar, 0L);

        // Guard SQL Server rechazó el update (starts_at > SYSUTCDATETIME() no se cumplió)
        assertThat(resultadoUpdate).isEmpty();

        // Verificar que la fila en base de datos quedó 100% intacta
        Map<String, Object> filaActual = jdbc.queryForMap(
                "SELECT starts_at, ends_at, reason_code, restricted_note, lock_version, cancelled_at, updated_at FROM employee_unavailabilities WHERE id = ?",
                insertada.id().toString()
        );
        assertThat(((Number) filaActual.get("lock_version")).longValue()).isEqualTo(0L);
        assertThat(filaActual.get("cancelled_at")).isNull();
        assertThat(filaActual.get("reason_code")).isEqualTo("VACACIONES");
        assertThat(filaActual.get("restricted_note")).isEqualTo("Nota original");
        assertThat(filaActual.get("updated_at")).isEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName("7. RN-013 Guard SQL Server: cancel retorna Optional.empty() y no altera la fila si starts_at <= SYSUTCDATETIME()")
    void cancelRechazaCancelacionSiIntervaloYaInicioPorGuardSqlServer() {
        // Intervalo cuyo inicio ya transcurrió en el pasado
        Instant pastStart = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant futureEnd = Instant.now().plus(3, ChronoUnit.DAYS);
        TimeInterval pastInterval = TimeInterval.of(pastStart, futureEnd);

        EmployeeUnavailability insertada = repository.insert(EmployeeUnavailability.create(
                orgId, empId, pastInterval, UnavailabilityReason.PERMISO_PERSONAL,
                RestrictedNote.of("Permiso pasado"), userId, Instant.now()
        ));

        // Obtener estado original persistido
        Map<String, Object> estadoOriginal = jdbc.queryForMap(
                "SELECT updated_at, lock_version, cancelled_at FROM employee_unavailabilities WHERE id = ?",
                insertada.id().toString()
        );
        Object originalUpdatedAt = estadoOriginal.get("updated_at");

        // Intentar cancel directo en el repositorio
        Optional<EmployeeUnavailability> resultadoCancel = repository.cancel(orgId, insertada.id(), userId, 0L);

        // Guard SQL Server rechazó la cancelación (starts_at > SYSUTCDATETIME() no se cumplió)
        assertThat(resultadoCancel).isEmpty();

        // Verificar que la fila en base de datos quedó 100% intacta
        Map<String, Object> filaActual = jdbc.queryForMap(
                "SELECT starts_at, ends_at, reason_code, restricted_note, lock_version, cancelled_at, updated_at FROM employee_unavailabilities WHERE id = ?",
                insertada.id().toString()
        );
        assertThat(((Number) filaActual.get("lock_version")).longValue()).isEqualTo(0L);
        assertThat(filaActual.get("cancelled_at")).isNull();
        assertThat(filaActual.get("reason_code")).isEqualTo("PERMISO_PERSONAL");
        assertThat(filaActual.get("restricted_note")).isEqualTo("Permiso pasado");
        assertThat(filaActual.get("updated_at")).isEqualTo(originalUpdatedAt);
    }

    // =========================================================================
    // Helpers de inserción relacional
    // =========================================================================

    private String insertarOrganizacion(String code, String displayName) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, 'ACTIVA', 'America/Bogota', 10, 1)
                """, id, code, displayName);
        return id;
    }

    private String insertarSede(String orgId, String code, String name) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO sites (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, ?, 1)
                """, id, orgId, code, name);
        return id;
    }

    private String insertarPuesto(String orgId, String code, String name) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO positions (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, ?, 1)
                """, id, orgId, code, name);
        return id;
    }

    private String insertarEmpleado(String orgId, String code, String displayName, String posId, String siteId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO employees (id, organization_id, code, display_name, position_id, site_id, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """, id, orgId, code, displayName, posId, siteId);
        return id;
    }

    private String insertarUsuario(String orgId, String email, String displayName) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                VALUES (?, ?, ?, ?, ?, 'SUPERVISOR', NULL)
                """, id, orgId, email, email.toLowerCase(), displayName);
        return id;
    }
}
