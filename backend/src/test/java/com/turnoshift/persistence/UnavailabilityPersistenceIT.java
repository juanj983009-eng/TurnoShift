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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integración de persistencia y migraciones para I2.4-a:
 * Verifica la migración V17__employee_unavailabilities.sql, integridad multi-tenant con FKs
 * compuestas a employees y app_users, restricciones CHECK, índices filtrados y ruta de upgrade V16->V17.
 */
@SpringBootTest
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: Persistencia de indisponibilidades e integridad multi-tenant (V17)")
class UnavailabilityPersistenceIT {

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
    // Helpers para armado de grafos relacionales
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

    private String insertarUsuario(String orgId, String email, String displayName) {
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                VALUES (?, ?, ?, ?, ?, 'SUPERVISOR', NULL)
                """, userId, orgId, email, email.toLowerCase(), displayName);
        return userId;
    }

    private String insertarIndisponibilidad(String orgId, String empId, Instant startsAt, Instant endsAt,
                                            String reasonCode, String note, String createdByUserId) {
        String unavId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO employee_unavailabilities (
                    id, organization_id, employee_id, starts_at, ends_at,
                    reason_code, restricted_note, created_by_user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, unavId, orgId, empId, startsAt.toString(), endsAt.toString(),
                reasonCode, note, createdByUserId);
        return unavId;
    }

    // =========================================================================
    // Casos de prueba
    // =========================================================================

    @Test
    @DisplayName("1. Flyway aplica V1 a V19 en orden desde una base vacía")
    void aplicacionLimpiaDeMigraciones() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(versiones).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19");
    }

    @Test
    @DisplayName("2. La tabla employee_unavailabilities y sus constraints existen con nombres exactos")
    void existenciaDeEstructurasRelacionalesYConstraints() {
        Integer tableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'employee_unavailabilities' AND TABLE_TYPE = 'BASE TABLE'",
                Integer.class);
        assertThat(tableCount).isEqualTo(1);

        for (String constraintName : List.of(
                "pk_employee_unavailabilities",
                "uq_unavailabilities_org_id",
                "fk_unavailabilities_organization",
                "fk_unavailabilities_employee",
                "fk_unavailabilities_created_by",
                "fk_unavailabilities_cancelled_by",
                "chk_unavailabilities_interval",
                "chk_unavailabilities_lock_version_non_negative",
                "chk_unavailabilities_note_not_blank",
                "chk_unavailabilities_reason_code",
                "chk_unavailabilities_cancellation_coherence"
        )) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys.objects
                    WHERE name = ?
                    """, Integer.class, constraintName);
            assertThat(count).as("La restricción " + constraintName + " debe existir físicamente").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("3. Inserción válida y recuperación completa de campos, defaults y marcas de tiempo")
    void insercionValidaYRecuperacion() {
        String orgId = insertarOrganizacion("ORG-UNAV-01", "Org Unav 01");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Puesto 1");
        String empId = insertarEmpleado(orgId, "EMP-01", "Carlos Gomez", posId, siteId);
        String userId = insertarUsuario(orgId, "supervisor@unav01.com", "Supervisor Unav 01");

        Instant startsAt = Instant.parse("2026-11-01T08:00:00Z");
        Instant endsAt = Instant.parse("2026-11-01T17:00:00Z");
        String unavId = insertarIndisponibilidad(orgId, empId, startsAt, endsAt, "MEDICO", "Cita odontológica", userId);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT id, organization_id, employee_id, reason_code, restricted_note,
                       created_by_user_id, cancelled_at, cancelled_by_user_id, lock_version
                FROM employee_unavailabilities
                WHERE id = ?
                """, unavId);

        assertThat(row.get("id").toString()).isEqualToIgnoringCase(unavId);
        assertThat(row.get("organization_id").toString()).isEqualToIgnoringCase(orgId);
        assertThat(row.get("employee_id").toString()).isEqualToIgnoringCase(empId);
        assertThat(row.get("reason_code")).isEqualTo("MEDICO");
        assertThat(row.get("restricted_note")).isEqualTo("Cita odontológica");
        assertThat(row.get("created_by_user_id").toString()).isEqualToIgnoringCase(userId);
        assertThat(row.get("cancelled_at")).isNull();
        assertThat(row.get("cancelled_by_user_id")).isNull();
        assertThat(((Number) row.get("lock_version")).longValue()).isEqualTo(0L);
    }

    @Test
    @DisplayName("4. Aislamiento multi-tenant: consultas filtradas no retornan datos de otras organizaciones")
    void aislamientoMultiTenant() {
        String orgAId = insertarOrganizacion("ORG-TEN-A", "Org A");
        String siteAId = insertarSede(orgAId, "SED-A", "Sede A");
        String posAId = insertarPuesto(orgAId, "PST-A", "Puesto A");
        String empAId = insertarEmpleado(orgAId, "EMP-A", "Empleado A", posAId, siteAId);
        String userAId = insertarUsuario(orgAId, "user@orga.com", "User A");

        String orgBId = insertarOrganizacion("ORG-TEN-B", "Org B");
        String siteBId = insertarSede(orgBId, "SED-B", "Sede B");
        String posBId = insertarPuesto(orgBId, "PST-B", "Puesto B");
        String empBId = insertarEmpleado(orgBId, "EMP-B", "Empleado B", posBId, siteBId);
        String userBId = insertarUsuario(orgBId, "user@orgb.com", "User B");

        Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(8, ChronoUnit.HOURS);

        insertarIndisponibilidad(orgAId, empAId, startsAt, endsAt, "VACACIONES", "Vacaciones Org A", userAId);
        insertarIndisponibilidad(orgBId, empBId, startsAt, endsAt, "VACACIONES", "Vacaciones Org B", userBId);

        List<Map<String, Object>> filasOrgA = jdbc.queryForList(
                "SELECT id FROM employee_unavailabilities WHERE organization_id = ?", orgAId);
        assertThat(filasOrgA).hasSize(1);

        List<Map<String, Object>> filasOrgB = jdbc.queryForList(
                "SELECT id FROM employee_unavailabilities WHERE organization_id = ?", orgBId);
        assertThat(filasOrgB).hasSize(1);
    }

    @Test
    @DisplayName("5. fk_unavailabilities_employee rechaza colaborador perteneciente a otra organización")
    void rechazoFkCruzada_EmpleadoDeOtraOrganizacion() {
        String orgAId = insertarOrganizacion("ORG-FK-A", "Org A");
        String userAId = insertarUsuario(orgAId, "admin@fka.com", "Admin A");

        String orgBId = insertarOrganizacion("ORG-FK-B", "Org B");
        String siteBId = insertarSede(orgBId, "SED-B", "Sede B");
        String posBId = insertarPuesto(orgBId, "PST-B", "Puesto B");
        String empBId = insertarEmpleado(orgBId, "EMP-B", "Empleado B", posBId, siteBId);

        Instant startsAt = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(4, ChronoUnit.HOURS);

        assertThatThrownBy(() ->
                insertarIndisponibilidad(orgAId, empBId, startsAt, endsAt, "OTRO", "Intento cruzado", userAId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_unavailabilities_employee");
    }

    @Test
    @DisplayName("6. fk_unavailabilities_created_by rechaza autor de creación de otra organización")
    void rechazoFkCruzada_AutorDeCreacionDeOtraOrganizacion() {
        String orgAId = insertarOrganizacion("ORG-AUT-A", "Org A");
        String siteAId = insertarSede(orgAId, "SED-A", "Sede A");
        String posAId = insertarPuesto(orgAId, "PST-A", "Puesto A");
        String empAId = insertarEmpleado(orgAId, "EMP-A", "Empleado A", posAId, siteAId);

        String orgBId = insertarOrganizacion("ORG-AUT-B", "Org B");
        String userBId = insertarUsuario(orgBId, "user@autb.com", "User B");

        Instant startsAt = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(4, ChronoUnit.HOURS);

        assertThatThrownBy(() ->
                insertarIndisponibilidad(orgAId, empAId, startsAt, endsAt, "OTRO", "Autor cruzado", userBId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_unavailabilities_created_by");
    }

    @Test
    @DisplayName("7. fk_unavailabilities_cancelled_by rechaza autor de cancelación de otra organización")
    void rechazoFkCruzada_AutorDeCancelacionDeOtraOrganizacion() {
        String orgAId = insertarOrganizacion("ORG-CNC-A", "Org A");
        String siteAId = insertarSede(orgAId, "SED-A", "Sede A");
        String posAId = insertarPuesto(orgAId, "PST-A", "Puesto A");
        String empAId = insertarEmpleado(orgAId, "EMP-A", "Empleado A", posAId, siteAId);
        String userAId = insertarUsuario(orgAId, "admin@cnca.com", "Admin A");

        String orgBId = insertarOrganizacion("ORG-CNC-B", "Org B");
        String userBId = insertarUsuario(orgBId, "admin@cncb.com", "Admin B");

        Instant startsAt = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(8, ChronoUnit.HOURS);
        String unavId = insertarIndisponibilidad(orgAId, empAId, startsAt, endsAt, "FORMACION", "Curso", userAId);

        assertThatThrownBy(() ->
                jdbc.update("""
                        UPDATE employee_unavailabilities
                        SET cancelled_at = SYSUTCDATETIME(), cancelled_by_user_id = ?
                        WHERE id = ?
                        """, userBId, unavId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_unavailabilities_cancelled_by");
    }

    @Test
    @DisplayName("8. chk_unavailabilities_interval rechaza starts_at >= ends_at")
    void rechazoIntervaloInvalido() {
        String orgId = insertarOrganizacion("ORG-INT-01", "Org Int 01");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Puesto 1");
        String empId = insertarEmpleado(orgId, "EMP-01", "Empleado 1", posId, siteId);
        String userId = insertarUsuario(orgId, "user@int01.com", "User 1");

        Instant t1 = Instant.parse("2026-11-10T12:00:00Z");

        // starts_at == ends_at
        assertThatThrownBy(() ->
                insertarIndisponibilidad(orgId, empId, t1, t1, "OTRO", "Intervalo cero", userId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_unavailabilities_interval");

        // starts_at > ends_at
        assertThatThrownBy(() ->
                insertarIndisponibilidad(orgId, empId, t1, t1.minusSeconds(3600), "OTRO", "Intervalo invertido", userId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_unavailabilities_interval");
    }

    @Test
    @DisplayName("9. chk_unavailabilities_reason_code rechaza códigos no contemplados en el catálogo cerrado")
    void rechazoCatalogoInvalido() {
        String orgId = insertarOrganizacion("ORG-CAT-01", "Org Cat 01");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Puesto 1");
        String empId = insertarEmpleado(orgId, "EMP-01", "Empleado 1", posId, siteId);
        String userId = insertarUsuario(orgId, "user@cat01.com", "User 1");

        Instant startsAt = Instant.now().plus(4, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(4, ChronoUnit.HOURS);

        assertThatThrownBy(() ->
                insertarIndisponibilidad(orgId, empId, startsAt, endsAt, "INVENTADO", "Código ilegal", userId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_unavailabilities_reason_code");
    }

    @Test
    @DisplayName("10. chk_unavailabilities_cancellation_coherence rechaza estados de cancelación parciales")
    void rechazoCoherenciaCancelacionParcial() {
        String orgId = insertarOrganizacion("ORG-COH-01", "Org Coh 01");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Puesto 1");
        String empId = insertarEmpleado(orgId, "EMP-01", "Empleado 1", posId, siteId);
        String userId = insertarUsuario(orgId, "user@coh01.com", "User 1");

        Instant startsAt = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(8, ChronoUnit.HOURS);
        String unavId = insertarIndisponibilidad(orgId, empId, startsAt, endsAt, "VACACIONES", "Vacaciones", userId);

        // Intento 1: cancelled_at presente, pero cancelled_by_user_id nulo
        assertThatThrownBy(() ->
                jdbc.update("UPDATE employee_unavailabilities SET cancelled_at = SYSUTCDATETIME() WHERE id = ?", unavId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_unavailabilities_cancellation_coherence");

        // Intento 2: cancelled_by_user_id presente, pero cancelled_at nulo
        assertThatThrownBy(() ->
                jdbc.update("UPDATE employee_unavailabilities SET cancelled_by_user_id = ? WHERE id = ?", userId, unavId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_unavailabilities_cancellation_coherence");
    }

    @Test
    @DisplayName("11. Los índices filtrados de indisponibilidades activas existen físicamente con filtro cancelled_at IS NULL")
    void verificacionIndicesFiltrados() {
        List<Map<String, Object>> indices = jdbc.queryForList("""
                SELECT i.name AS index_name, i.has_filter, i.filter_definition
                FROM sys.indexes i
                JOIN sys.objects o ON i.object_id = o.object_id
                WHERE o.name = 'employee_unavailabilities'
                  AND i.name IN ('ix_unavailabilities_employee_active', 'ix_unavailabilities_org_interval_active')
                """);

        assertThat(indices).hasSize(2);
        for (Map<String, Object> idx : indices) {
            assertThat((Boolean) idx.get("has_filter")).isTrue();
            assertThat(idx.get("filter_definition").toString().toUpperCase().replace("[", "").replace("]", "")).contains("CANCELLED_AT IS NULL");
        }
    }

    @Test
    @DisplayName("12. Upgrade progresivo V16→V17 conserva datos existentes y habilita la nueva tabla")
    void upgradeProgresivoV16aV17_ConservaDatos() {
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

            // 1. Migrar hasta Flyway target 16 (estado previo a indisponibilidades)
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("16")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            String orgId = "11111111-1111-1111-1111-111111111111";
            String siteId = "22222222-2222-2222-2222-222222222222";
            String posId = "33333333-3333-3333-3333-333333333333";
            String empId = "44444444-4444-4444-4444-444444444444";
            String userId = "55555555-5555-5555-5555-555555555555";

            // 2. Insertar grafo relacional previo en V16
            isolatedJdbc.update("""
                    INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                        max_active_employees, max_active_sites)
                    VALUES (?, 'ORG-UPG-16', 'Org Upgrade V16', 'ACTIVA', 'America/Bogota', 15, 1)
                    """, orgId);

            isolatedJdbc.update("""
                    INSERT INTO sites (id, organization_id, code, name, is_active)
                    VALUES (?, ?, 'HQ-16', 'Sede V16', 1)
                    """, siteId, orgId);

            isolatedJdbc.update("""
                    INSERT INTO positions (id, organization_id, code, name, is_active)
                    VALUES (?, ?, 'POS-16', 'Puesto V16', 1)
                    """, posId, orgId);

            isolatedJdbc.update("""
                    INSERT INTO employees (id, organization_id, code, display_name, position_id, site_id, is_active)
                    VALUES (?, ?, 'EMP-16', 'Colaborador V16', ?, ?, 1)
                    """, empId, orgId, posId, siteId);

            isolatedJdbc.update("""
                    INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                    VALUES (?, ?, 'v16user@turnoshift.com', 'v16user@turnoshift.com', 'Usuario V16', 'ADMINISTRADOR', ?)
                    """, userId, orgId, empId);

            // 3. Migrar aplicando V17
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("17")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            // 4. Verificar que los datos preexistentes se conservan intactos
            Map<String, Object> empRow = isolatedJdbc.queryForMap(
                    "SELECT id, code, display_name FROM employees WHERE id = ?", empId);
            assertThat(empRow.get("code")).isEqualTo("EMP-16");

            // 5. Verificar que la tabla employee_unavailabilities existe y permite registrar una fila vinculada
            Integer tableCount = isolatedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'employee_unavailabilities'",
                    Integer.class);
            assertThat(tableCount).isEqualTo(1);

            String unavId = UUID.randomUUID().toString();
            isolatedJdbc.update("""
                    INSERT INTO employee_unavailabilities (
                        id, organization_id, employee_id, starts_at, ends_at,
                        reason_code, restricted_note, created_by_user_id
                    )
                    VALUES (?, ?, ?, '2026-12-01T00:00:00Z', '2026-12-05T00:00:00Z', 'VACACIONES', 'Post upgrade', ?)
                    """, unavId, orgId, empId, userId);

            String storedUnavId = isolatedJdbc.queryForObject(
                    "SELECT id FROM employee_unavailabilities WHERE id = ?", String.class, unavId);
            assertThat(storedUnavId).isEqualToIgnoringCase(unavId);
        }
    }
}
