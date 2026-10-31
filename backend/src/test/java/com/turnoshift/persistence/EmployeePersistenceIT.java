package com.turnoshift.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de integración de persistencia y migraciones para I2.2-a:
 * Verifica la creación de la tabla employees (V13), el desmantelamiento de la guarda
 * de V7 y la incorporación de la FK compuesta app_users -> employees (V14).
 */
@SpringBootTest
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: Persistencia de empleados y clave foránea compuesta (V13-V14)")
class EmployeePersistenceIT {

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
    // Helpers
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
        String posId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO positions (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, ?, 1)
                """, posId, orgId, code, name);
        return posId;
    }

    private String insertarEmpleado(String orgId, String code, String displayName, String positionId, String siteId) {
        String empId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO employees (id, organization_id, code, display_name, position_id, site_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, empId, orgId, code, displayName, positionId, siteId);
        return empId;
    }

    private String insertarUsuario(String orgId, String email, String displayName, String employeeId) {
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                VALUES (?, ?, ?, ?, ?, 'COLABORADOR', ?)
                """, userId, orgId, email, email.toLowerCase(), displayName, employeeId);
        return userId;
    }

    // =========================================================================
    // Pruebas
    // =========================================================================

    @Test
    @DisplayName("1. Flyway aplica V1 a V14 desde una base vacía")
    void flywayAplicaV1aV14DesdeBaseVacia() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(versiones).contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
    }

    @Test
    @DisplayName("2. Un empleado válido con puesto y sede de la misma organización se inserta correctamente")
    void empleadoValidoSeInsertaCorrectamente() {
        String orgId = insertarOrganizacion("ORG-EMP-01", "Org Emp 01");
        String siteId = insertarSede(orgId, "SEDE-01", "Sede Central");
        String posId = insertarPuesto(orgId, "POS-01", "Barista");

        String empId = insertarEmpleado(orgId, "EMP-001", "Ana Gomez", posId, siteId);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT id, organization_id, code, display_name, position_id, site_id, is_active, lock_version, created_at, updated_at FROM employees WHERE id = ?",
                empId);

        assertThat(row.get("id").toString()).isEqualToIgnoringCase(empId);
        assertThat(row.get("organization_id").toString()).isEqualToIgnoringCase(orgId);
        assertThat(row.get("code")).isEqualTo("EMP-001");
        assertThat(row.get("display_name")).isEqualTo("Ana Gomez");
        assertThat(row.get("position_id").toString()).isEqualToIgnoringCase(posId);
        assertThat(row.get("site_id").toString()).isEqualToIgnoringCase(siteId);
        assertThat(row.get("is_active")).isEqualTo(true);
        assertThat(((Number) row.get("lock_version")).longValue()).isEqualTo(0L);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    @DisplayName("3. code duplicado dentro de la misma organización es rechazado")
    void codeDuplicadoMismaOrganizacionEsRechazado() {
        String orgId = insertarOrganizacion("ORG-EMP-02", "Org Emp 02");
        String siteId = insertarSede(orgId, "SEDE-02", "Sede 02");
        String posId = insertarPuesto(orgId, "POS-02", "Cajero");

        insertarEmpleado(orgId, "EMP-DUP", "Carlos Ruiz", posId, siteId);

        assertThatThrownBy(() -> insertarEmpleado(orgId, "EMP-DUP", "Carlos Otro", posId, siteId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_employees_org_code");
    }

    @Test
    @DisplayName("4. El mismo code en otra organización es permitido")
    void mismoCodeEnOtraOrganizacionEsPermitido() {
        String org1 = insertarOrganizacion("ORG-EMP-03A", "Org Emp 03A");
        String site1 = insertarSede(org1, "SEDE-03A", "Sede 03A");
        String pos1 = insertarPuesto(org1, "POS-03A", "Puesto 03A");

        String org2 = insertarOrganizacion("ORG-EMP-03B", "Org Emp 03B");
        String site2 = insertarSede(org2, "SEDE-03B", "Sede 03B");
        String pos2 = insertarPuesto(org2, "POS-03B", "Puesto 03B");

        String emp1 = insertarEmpleado(org1, "EMP-COMUN", "Empleado Org A", pos1, site1);
        String emp2 = insertarEmpleado(org2, "EMP-COMUN", "Empleado Org B", pos2, site2);

        assertThat(emp1).isNotNull();
        assertThat(emp2).isNotNull();
    }

    @Test
    @DisplayName("5. FK compuesta rechaza puesto de otra organización")
    void fkCompuestaRechazaPuestoDeOtraOrganizacion() {
        String org1 = insertarOrganizacion("ORG-EMP-04A", "Org Emp 04A");
        String site1 = insertarSede(org1, "SEDE-04A", "Sede 04A");

        String org2 = insertarOrganizacion("ORG-EMP-04B", "Org Emp 04B");
        String posDeOrg2 = insertarPuesto(org2, "POS-ORG2", "Puesto de Org 2");

        assertThatThrownBy(() -> insertarEmpleado(org1, "EMP-CROSS-POS", "Intento Cruzado", posDeOrg2, site1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_employees_position");
    }

    @Test
    @DisplayName("6. FK compuesta rechaza sede de otra organización")
    void fkCompuestaRechazaSedeDeOtraOrganizacion() {
        String org1 = insertarOrganizacion("ORG-EMP-05A", "Org Emp 05A");
        String pos1 = insertarPuesto(org1, "POS-05A", "Puesto 05A");

        String org2 = insertarOrganizacion("ORG-EMP-05B", "Org Emp 05B");
        String siteDeOrg2 = insertarSede(org2, "SEDE-ORG2", "Sede de Org 2");

        assertThatThrownBy(() -> insertarEmpleado(org1, "EMP-CROSS-SITE", "Intento Cruzado", pos1, siteDeOrg2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_employees_site");
    }

    @Test
    @DisplayName("7. app_users acepta employee_id de empleado de su misma organización tras V14")
    void appUsersAceptaEmployeeIdMismaOrganizacion() {
        String orgId = insertarOrganizacion("ORG-USR-01", "Org Usr 01");
        String siteId = insertarSede(orgId, "SEDE-U1", "Sede U1");
        String posId = insertarPuesto(orgId, "POS-U1", "Puesto U1");
        String empId = insertarEmpleado(orgId, "EMP-U1", "Empleado U1", posId, siteId);

        String userId = insertarUsuario(orgId, "u1@turnoshift.com", "Usuario Vinculado", empId);

        String vinculacion = jdbc.queryForObject(
                "SELECT employee_id FROM app_users WHERE id = ?",
                String.class, userId);
        assertThat(vinculacion).isEqualToIgnoringCase(empId);
    }

    @Test
    @DisplayName("8. app_users rechaza employee_id de empleado de otra organización")
    void appUsersRechazaEmployeeIdDeOtraOrganizacion() {
        String org1 = insertarOrganizacion("ORG-USR-02A", "Org Usr 02A");
        String org2 = insertarOrganizacion("ORG-USR-02B", "Org Usr 02B");
        String site2 = insertarSede(org2, "SEDE-U2", "Sede U2");
        String pos2 = insertarPuesto(org2, "POS-U2", "Puesto U2");
        String empOrg2 = insertarEmpleado(org2, "EMP-U2", "Empleado Org 2", pos2, site2);

        assertThatThrownBy(() -> insertarUsuario(org1, "cross@turnoshift.com", "Usuario Cruzado", empOrg2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_app_users_employee");
    }

    @Test
    @DisplayName("9. app_users rechaza un UUID de employee inexistente")
    void appUsersRechazaEmployeeIdInexistente() {
        String orgId = insertarOrganizacion("ORG-USR-03", "Org Usr 03");
        String nonExistentEmpId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> insertarUsuario(orgId, "none@turnoshift.com", "Usuario Fantasma", nonExistentEmpId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_app_users_employee");
    }

    @Test
    @DisplayName("10. El guard V7 ya no impide employee_id no nulo; la FK V14 es quien controla la integridad")
    void guardV7YaNoExiste() {
        Integer guardCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys.check_constraints
                WHERE name = 'chk_app_users_employee_id_null_guard'
                """, Integer.class);
        assertThat(guardCount).isEqualTo(0);
    }

    @Test
    @DisplayName("11. Upgrade V12 a V14 conserva organizaciones, sedes, puestos y usuarios existentes, sin poblar employee_id")
    void upgradeV12aV14ConservaDatos() {
        // Contenedor y DataSource aislados para validar la ruta de actualización incremental
        try (MSSQLServerContainer isolatedServer =
                     new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                             .acceptLicense()) {
            isolatedServer.start();

            org.springframework.jdbc.datasource.DriverManagerDataSource ds =
                    new org.springframework.jdbc.datasource.DriverManagerDataSource();
            ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            ds.setUrl(isolatedServer.getJdbcUrl());
            ds.setUsername(isolatedServer.getUsername());
            ds.setPassword(isolatedServer.getPassword());

            JdbcTemplate isolatedJdbc = new JdbcTemplate(ds);

            // 1. Migrar hasta Flyway target 12
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("12")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            String orgId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
            String siteId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
            String posId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
            String userId = "dddddddd-dddd-dddd-dddd-dddddddddddd";

            // 2. Insertar datos fijos en V12 antes de la actualización a V13/V14
            isolatedJdbc.update("""
                    INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                        max_active_employees, max_active_sites)
                    VALUES (?, 'ORG-UPG-12', 'Org Upgrade V12', 'ACTIVA', 'America/Bogota', 15, 1)
                    """, orgId);

            isolatedJdbc.update("""
                    INSERT INTO sites (organization_id, id, code, name, is_active)
                    VALUES (?, ?, 'HQ-12', 'Sede V12', 1)
                    """, orgId, siteId);

            isolatedJdbc.update("""
                    INSERT INTO positions (organization_id, id, code, name, is_active)
                    VALUES (?, ?, 'POS-12', 'Puesto V12', 1)
                    """, orgId, posId);

            isolatedJdbc.update("""
                    INSERT INTO app_users (organization_id, id, email, normalized_email, display_name, role_code, employee_id)
                    VALUES (?, ?, 'v12user@turnoshift.com', 'v12user@turnoshift.com', 'Usuario V12', 'ADMINISTRADOR', NULL)
                    """, orgId, userId);

            // 3. Migrar hasta latest (V14)
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("14")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            // 4. Verificar integridad y conservación de datos
            Map<String, Object> orgRow = isolatedJdbc.queryForMap(
                    "SELECT id, code, display_name, max_active_employees FROM organizations WHERE id = ?", orgId);
            assertThat(orgRow.get("id").toString()).isEqualToIgnoringCase(orgId);
            assertThat(orgRow.get("code")).isEqualTo("ORG-UPG-12");
            assertThat(orgRow.get("display_name")).isEqualTo("Org Upgrade V12");

            Map<String, Object> siteRow = isolatedJdbc.queryForMap(
                    "SELECT id, organization_id, code, name FROM sites WHERE id = ?", siteId);
            assertThat(siteRow.get("id").toString()).isEqualToIgnoringCase(siteId);
            assertThat(siteRow.get("organization_id").toString()).isEqualToIgnoringCase(orgId);
            assertThat(siteRow.get("code")).isEqualTo("HQ-12");

            Map<String, Object> posRow = isolatedJdbc.queryForMap(
                    "SELECT id, organization_id, code, name FROM positions WHERE id = ?", posId);
            assertThat(posRow.get("id").toString()).isEqualToIgnoringCase(posId);
            assertThat(posRow.get("organization_id").toString()).isEqualToIgnoringCase(orgId);
            assertThat(posRow.get("code")).isEqualTo("POS-12");

            Map<String, Object> userRow = isolatedJdbc.queryForMap(
                    "SELECT id, organization_id, email, employee_id FROM app_users WHERE id = ?", userId);
            assertThat(userRow.get("id").toString()).isEqualToIgnoringCase(userId);
            assertThat(userRow.get("organization_id").toString()).isEqualToIgnoringCase(orgId);
            assertThat(userRow.get("email")).isEqualTo("v12user@turnoshift.com");
            assertThat(userRow.get("employee_id")).isNull();

            // 5. Verificar que tabla employees y constraint fk_app_users_employee existen tras V14
            Integer employeesTableCount = isolatedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'employees' AND TABLE_TYPE = 'BASE TABLE'",
                    Integer.class);
            assertThat(employeesTableCount).isEqualTo(1);

            Integer fkCount = isolatedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.foreign_keys WHERE name = 'fk_app_users_employee'",
                    Integer.class);
            assertThat(fkCount).isEqualTo(1);
        }
    }
}
