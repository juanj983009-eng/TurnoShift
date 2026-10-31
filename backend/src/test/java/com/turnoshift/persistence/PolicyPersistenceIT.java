package com.turnoshift.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de integración de persistencia y migraciones para I2.3-a:
 * Verifica la creación de la tabla planning_policy_versions (V15), la FK compuesta
 * organizations -> planning_policy_versions (V16), las restricciones relacionales,
 * el aislamiento multi-tenant y la ruta de actualización progresiva V14->V16.
 */
@SpringBootTest
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: Persistencia de versiones de política y clave foránea compuesta (V15-V16)")
class PolicyPersistenceIT {

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

    private String insertarUsuario(String orgId, String email, String displayName) {
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                VALUES (?, ?, ?, ?, ?, 'ADMINISTRADOR', NULL)
                """, userId, orgId, email, email.toLowerCase(), displayName);
        return userId;
    }

    private String insertarPolitica(String orgId, int versionNumber, int minRest, int weeklyWarning, String authorUserId, String reason) {
        String policyId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO planning_policy_versions (
                    id, organization_id, version_number, minimum_rest_minutes, weekly_warning_minutes,
                    created_by_user_id, change_reason
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, policyId, orgId, versionNumber, minRest, weeklyWarning, authorUserId, reason);
        return policyId;
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
    @DisplayName("2. La tabla planning_policy_versions y sus constraints existen con nombres exactos")
    void existenciaDeEstructurasRelacionales() {
        Integer tableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'planning_policy_versions' AND TABLE_TYPE = 'BASE TABLE'",
                Integer.class);
        assertThat(tableCount).isEqualTo(1);

        for (String constraintName : List.of(
                "pk_planning_policy_versions",
                "uq_policy_versions_org_id",
                "uq_policy_versions_org_version",
                "fk_policy_versions_organization",
                "fk_policy_versions_author",
                "fk_organizations_current_policy",
                "chk_policy_version_number_positive",
                "chk_policy_minimum_rest_range",
                "chk_policy_weekly_warning_range",
                "chk_policy_reason_not_empty"
        )) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys.objects
                    WHERE name = ?
                    """, Integer.class, constraintName);
            assertThat(count).as("La restricción " + constraintName + " debe existir").isEqualTo(1);
        }

        Integer guardCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys.check_constraints
                WHERE name = 'chk_organizations_policy_version_null'
                """, Integer.class);
        assertThat(guardCount).as("La guarda temporal chk_organizations_policy_version_null debe haber sido eliminada").isEqualTo(0);
    }

    @Test
    @DisplayName("3. Una versión de política válida se inserta y actualiza current_policy_version_id correctamente")
    void insercionValidaYActualizacionDePoliticaVigente() {
        String orgId = insertarOrganizacion("ORG-POL-01", "Org Política 01");
        String userId = insertarUsuario(orgId, "admin@pol01.com", "Admin Pol 01");

        String policyId = insertarPolitica(orgId, 1, 660, 2400, userId, "Configuración inicial de prueba");

        jdbc.update("UPDATE organizations SET current_policy_version_id = ? WHERE id = ?", policyId, orgId);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT o.id AS org_id, o.current_policy_version_id, p.id AS policy_id, p.version_number,
                       p.minimum_rest_minutes, p.weekly_warning_minutes, p.change_reason, p.created_by_user_id
                FROM organizations o
                INNER JOIN planning_policy_versions p ON o.id = p.organization_id AND o.current_policy_version_id = p.id
                WHERE o.id = ?
                """, orgId);

        assertThat(row.get("org_id").toString()).isEqualToIgnoringCase(orgId);
        assertThat(row.get("current_policy_version_id").toString()).isEqualToIgnoringCase(policyId);
        assertThat(row.get("policy_id").toString()).isEqualToIgnoringCase(policyId);
        assertThat(row.get("version_number")).isEqualTo(1);
        assertThat(row.get("minimum_rest_minutes")).isEqualTo(660);
        assertThat(row.get("weekly_warning_minutes")).isEqualTo(2400);
        assertThat(row.get("change_reason")).isEqualTo("Configuración inicial de prueba");
        assertThat(row.get("created_by_user_id").toString()).isEqualToIgnoringCase(userId);
    }

    @Test
    @DisplayName("4. fk_organizations_current_policy rechaza apuntar a una política de otra organización")
    void rechazoFkCompuestaMultiTenant_PoliticaDeOtraOrganizacion() {
        String orgAId = insertarOrganizacion("ORG-POL-A", "Org A");
        String orgBId = insertarOrganizacion("ORG-POL-B", "Org B");
        String userBId = insertarUsuario(orgBId, "admin@pol-b.com", "Admin B");

        String policyBId = insertarPolitica(orgBId, 1, 660, 2400, userBId, "Política Org B");

        assertThatThrownBy(() ->
                jdbc.update("UPDATE organizations SET current_policy_version_id = ? WHERE id = ?", policyBId, orgAId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_organizations_current_policy");
    }

    @Test
    @DisplayName("5. fk_policy_versions_author rechaza autor perteneciente a otra organización")
    void rechazoAutorDeOtraOrganizacion() {
        String orgAId = insertarOrganizacion("ORG-AUT-A", "Org Aut A");
        String orgBId = insertarOrganizacion("ORG-AUT-B", "Org Aut B");
        String userBId = insertarUsuario(orgBId, "admin@aut-b.com", "Admin Aut B");

        assertThatThrownBy(() ->
                insertarPolitica(orgAId, 1, 660, 2400, userBId, "Política con autor cruzado")
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_policy_versions_author");
    }

    @Test
    @DisplayName("6. uq_policy_versions_org_version rechaza número de versión duplicado para la misma organización")
    void rechazoUnicidadDeVersionDuplicada() {
        String orgId = insertarOrganizacion("ORG-VER-DUP", "Org Ver Dup");
        String userId = insertarUsuario(orgId, "admin@verdup.com", "Admin Ver Dup");

        insertarPolitica(orgId, 1, 660, 2400, userId, "Versión 1");

        assertThatThrownBy(() ->
                insertarPolitica(orgId, 1, 720, 2400, userId, "Versión 1 duplicada")
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_policy_versions_org_version");
    }

    @Test
    @DisplayName("7. chk_policy_minimum_rest_range rechaza valores fuera de 1..1440")
    void rechazoLimitesFueraDeRango_MinimumRest() {
        String orgId = insertarOrganizacion("ORG-RNG-REST", "Org Rng Rest");
        String userId = insertarUsuario(orgId, "admin@rngrest.com", "Admin Rng Rest");

        // Valor 0
        assertThatThrownBy(() -> insertarPolitica(orgId, 1, 0, 2400, userId, "Descanso 0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_policy_minimum_rest_range");

        // Valor negativo
        assertThatThrownBy(() -> insertarPolitica(orgId, 2, -1, 2400, userId, "Descanso -1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_policy_minimum_rest_range");

        // Valor > 1440
        assertThatThrownBy(() -> insertarPolitica(orgId, 3, 1441, 2400, userId, "Descanso 1441"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_policy_minimum_rest_range");
    }

    @Test
    @DisplayName("8. chk_policy_weekly_warning_range rechaza valores fuera de 1..10080")
    void rechazoLimitesFueraDeRango_WeeklyWarning() {
        String orgId = insertarOrganizacion("ORG-RNG-WARN", "Org Rng Warn");
        String userId = insertarUsuario(orgId, "admin@rngwarn.com", "Admin Rng Warn");

        // Valor 0
        assertThatThrownBy(() -> insertarPolitica(orgId, 1, 660, 0, userId, "Umbral 0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_policy_weekly_warning_range");

        // Valor negativo
        assertThatThrownBy(() -> insertarPolitica(orgId, 2, 660, -1, userId, "Umbral -1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_policy_weekly_warning_range");

        // Valor > 10080
        assertThatThrownBy(() -> insertarPolitica(orgId, 3, 660, 10081, userId, "Umbral 10081"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_policy_weekly_warning_range");
    }

    @Test
    @DisplayName("9. chk_policy_reason_not_empty rechaza motivo vacío o solo espacios ASCII")
    void rechazoMotivoVacio() {
        String orgId = insertarOrganizacion("ORG-RSN-EMP", "Org Rsn Emp");
        String userId = insertarUsuario(orgId, "admin@rsnemp.com", "Admin Rsn Emp");

        assertThatThrownBy(() -> insertarPolitica(orgId, 1, 660, 2400, userId, ""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_policy_reason_not_empty");

        assertThatThrownBy(() -> insertarPolitica(orgId, 2, 660, 2400, userId, "    "))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_policy_reason_not_empty");
    }

    @Test
    @DisplayName("10. Upgrade progresivo V14→V16 conserva datos existentes y current_policy_version_id NULL")
    void upgradeProgresivoV14aV16_ConservaDatosYNulls() {
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

            // 1. Migrar hasta Flyway target 14
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("14")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            String orgId = "11111111-1111-1111-1111-111111111111";
            String siteId = "22222222-2222-2222-2222-222222222222";
            String posId = "33333333-3333-3333-3333-333333333333";
            String userId = "44444444-4444-4444-4444-444444444444";

            // 2. Insertar datos en V14 con current_policy_version_id = NULL
            isolatedJdbc.update("""
                    INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                        max_active_employees, max_active_sites, current_policy_version_id)
                    VALUES (?, 'ORG-UPG-14', 'Org Upgrade V14', 'ACTIVA', 'America/Bogota', 15, 1, NULL)
                    """, orgId);

            isolatedJdbc.update("""
                    INSERT INTO sites (id, organization_id, code, name, is_active)
                    VALUES (?, ?, 'HQ-14', 'Sede V14', 1)
                    """, siteId, orgId);

            isolatedJdbc.update("""
                    INSERT INTO positions (id, organization_id, code, name, is_active)
                    VALUES (?, ?, 'POS-14', 'Puesto V14', 1)
                    """, posId, orgId);

            isolatedJdbc.update("""
                    INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                    VALUES (?, ?, 'v14user@turnoshift.com', 'v14user@turnoshift.com', 'Usuario V14', 'ADMINISTRADOR', NULL)
                    """, userId, orgId);

            // 3. Migrar hasta target 16 (aplicando V15 y V16)
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("16")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            // 4. Verificar conservación de datos y preservación de NULL en current_policy_version_id
            Map<String, Object> orgRow = isolatedJdbc.queryForMap(
                    "SELECT id, code, display_name, current_policy_version_id FROM organizations WHERE id = ?", orgId);
            assertThat(orgRow.get("id").toString()).isEqualToIgnoringCase(orgId);
            assertThat(orgRow.get("code")).isEqualTo("ORG-UPG-14");
            assertThat(orgRow.get("display_name")).isEqualTo("Org Upgrade V14");
            assertThat(orgRow.get("current_policy_version_id")).isNull();

            // 5. Verificar que planning_policy_versions y fk_organizations_current_policy existen tras V16
            Integer tableCount = isolatedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'planning_policy_versions' AND TABLE_TYPE = 'BASE TABLE'",
                    Integer.class);
            assertThat(tableCount).isEqualTo(1);

            Integer fkCount = isolatedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.foreign_keys WHERE name = 'fk_organizations_current_policy'",
                    Integer.class);
            assertThat(fkCount).isEqualTo(1);

            Integer guardCount = isolatedJdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys.check_constraints
                    WHERE name = 'chk_organizations_policy_version_null'
                    """, Integer.class);
            assertThat(guardCount).as("La guarda temporal chk_organizations_policy_version_null debe haber sido eliminada").isZero();

            // 6. Verificar que se puede insertar una política y asociar el puntero
            String policyId = UUID.randomUUID().toString();
            isolatedJdbc.update("""
                    INSERT INTO planning_policy_versions (
                        id, organization_id, version_number, minimum_rest_minutes, weekly_warning_minutes,
                        created_by_user_id, change_reason
                    )
                    VALUES (?, ?, 1, 660, 2400, ?, 'Política post upgrade')
                    """, policyId, orgId, userId);

            isolatedJdbc.update("UPDATE organizations SET current_policy_version_id = ? WHERE id = ?", policyId, orgId);

            String updatedPolicyId = isolatedJdbc.queryForObject(
                    "SELECT current_policy_version_id FROM organizations WHERE id = ?", String.class, orgId);
            assertThat(updatedPolicyId).isEqualToIgnoringCase(policyId);
        }
    }

    @Test
    @DisplayName("11. Preflight fail-closed de V16 aborta la migración si existe puntero legado inválido")
    void preflightFailClosedV16_DetectaPunteroInvalido() {
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

            // 1. Migrar hasta Flyway target 15
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("15")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            String orgId = UUID.randomUUID().toString();
            String invalidPolicyId = UUID.randomUUID().toString();

            // 2. Deshabilitar temporalmente el check de V3 en la tabla para simular un estado legado
            //    con puntero huérfano antes de V16.
            isolatedJdbc.execute("ALTER TABLE organizations NOCHECK CONSTRAINT chk_organizations_policy_version_null");

            isolatedJdbc.update("""
                    INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                        max_active_employees, max_active_sites, current_policy_version_id)
                    VALUES (?, 'ORG-FAIL-16', 'Org Fail V16', 'ACTIVA', 'America/Bogota', 10, 1, ?)
                    """, orgId, invalidPolicyId);

            // 3. Ejecutar migración V16 y comprobar que falla cerrado con error 50000
            Flyway flywayV16 = Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("16")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load();

            assertThatThrownBy(flywayV16::migrate)
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("Migración V16 abortada");

            // 4. Comprobar que fk_organizations_current_policy no fue creada
            Integer fkCount = isolatedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.foreign_keys WHERE name = 'fk_organizations_current_policy'",
                    Integer.class);
            assertThat(fkCount).as("La clave foránea fk_organizations_current_policy no debe haber sido creada").isZero();

            // 5. Comprobar que V16 no quedó registrada como migración exitosa en flyway_schema_history
            Integer v16SuccessCount = isolatedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '16' AND success = 1",
                    Integer.class);
            assertThat(v16SuccessCount).as("V16 no debe quedar registrada como exitosa en flyway_schema_history").isZero();
        }
    }
}
