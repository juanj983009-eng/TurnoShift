package com.turnoshift.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de actualización: verifica que las migraciones de identidad (V4–V8) pueden
 * aplicarse sobre una base que ya tiene datos válidos de V1/V2/V3, sin perder filas,
 * identificadores ni relaciones existentes antes de la actualización.
 *
 * <p><b>Escenario:</b>
 * <ol>
 *   <li>Base efímera y exclusiva para esta clase.</li>
 *   <li>Se aplican V1, V2 y V3 (target = 3).</li>
 *   <li>Se insertan dos organizaciones y dos sedes con IDs fijos ANTES de aplicar la identidad.</li>
 *   <li>Se aplican las migraciones de identidad V4–V8 (target = latest).</li>
 *   <li>Se verifica que los IDs, relaciones y valores insertados en paso 3 se conservan intactos.</li>
 *   <li>Se verifica que las nuevas tablas y restricciones de identidad están activas.</li>
 * </ol>
 *
 * <p><b>Diferencia con SchemaUpgradeV2ToV3IT:</b> esa prueba verifica la ruta V2→V3 histórica
 * con destino fijo en V3. Esta prueba verifica V3→latest (V4–V8) y demuestra que los datos
 * de organizations/sites existentes ANTES de la actualización de identidad se conservan.
 *
 * <p><b>Patrón:</b> Flyway programático con control de versión explícito. Exclusivo para
 * pruebas de ruta de actualización; las pruebas de producción usan FlywayAutoConfiguration.
 *
 * <p><b>Imagen:</b> SQL Server 2022 Developer Edition — solo desarrollo/pruebas.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT: Actualización V3→latest — datos de organizations/sites preexistentes se conservan")
class SchemaUpgradeV3ToLatestIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    static JdbcTemplate jdbc;

    // UUIDs fijos para verificar que los identificadores se conservan tras V4–V8
    static final String ORG_ALFA_ID  = "A3A30000-0000-0000-0000-000000000001";
    static final String ORG_BETA_ID  = "B4B40000-0000-0000-0000-000000000002";
    static final String SITE_ALFA_ID = "A3A30000-0000-0000-0001-000000000001";
    static final String SITE_BETA_ID = "B4B40000-0000-0000-0001-000000000002";

    /**
     * Aplica V1+V2+V3, inserta datos de organizations/sites ANTES de la identidad,
     * luego aplica V4–V8 (latest).
     */
    @BeforeAll
    static void prepararYMigrar() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setUrl(SQL_SERVER.getJdbcUrl());
        ds.setUsername(SQL_SERVER.getUsername());
        ds.setPassword(SQL_SERVER.getPassword());

        // Paso 1: aplicar solo hasta V3 (organizations + sites + constraints)
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("3")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load()
                .migrate();

        jdbc = new JdbcTemplate(ds);

        // Paso 2: insertar dos organizaciones y dos sedes ANTES de aplicar identidad.
        //         Estos datos deben sobrevivir intactos a V4–V8.
        jdbc.update("""
                INSERT INTO organizations
                    (id, code, display_name, service_status, timezone_id,
                     max_active_employees, max_active_sites)
                VALUES (?, 'ALFA-ID', 'Empresa Alfa Identidad', 'ACTIVA', 'America/Bogota', 10, 1)
                """, ORG_ALFA_ID);

        jdbc.update("""
                INSERT INTO organizations
                    (id, code, display_name, service_status, timezone_id,
                     max_active_employees, max_active_sites)
                VALUES (?, 'BETA-ID', 'Empresa Beta Identidad', 'ACTIVA', 'Europe/Madrid', 5, 1)
                """, ORG_BETA_ID);

        jdbc.update("""
                INSERT INTO sites (organization_id, id, code, name, is_active)
                VALUES (?, ?, 'HQ', 'Sede Alfa Principal', 1)
                """, ORG_ALFA_ID, SITE_ALFA_ID);

        jdbc.update("""
                INSERT INTO sites (organization_id, id, code, name, is_active)
                VALUES (?, ?, 'HQ', 'Sede Beta Principal', 1)
                """, ORG_BETA_ID, SITE_BETA_ID);

        // Paso 3: aplicar V4–V8 (migraciones de identidad + correcciones)
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    // =========================================================================
    // Test 1: historial contiene V1–V8
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Historial contiene V1 a V19 tras actualización progresiva V3→latest")
    void historialContieneV1aV11() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(versiones)
                .as("La actualización progresiva V3→latest debe registrar V1 a V19")
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19");
    }

    // =========================================================================
    // Test 2: organizaciones preexistentes conservan IDs y datos
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Organizaciones insertadas antes de V4–V8 conservan sus IDs y campos")
    void organizacionesPreexistentesConservadas() {
        List<Map<String, Object>> orgs = jdbc.queryForList(
                "SELECT id, code, display_name, timezone_id, max_active_sites " +
                "FROM organizations WHERE id IN (?, ?) ORDER BY code",
                ORG_ALFA_ID, ORG_BETA_ID);

        assertThat(orgs).as("Deben conservarse exactamente 2 organizaciones").hasSize(2);

        Map<String, Object> alfa = orgs.get(0); // ALFA-ID < BETA-ID
        assertThat(alfa.get("id").toString()).isEqualToIgnoringCase(ORG_ALFA_ID);
        assertThat(alfa.get("code")).isEqualTo("ALFA-ID");
        assertThat(alfa.get("timezone_id")).isEqualTo("America/Bogota");
        assertThat(alfa.get("max_active_sites")).isEqualTo(1);

        Map<String, Object> beta = orgs.get(1);
        assertThat(beta.get("id").toString()).isEqualToIgnoringCase(ORG_BETA_ID);
        assertThat(beta.get("code")).isEqualTo("BETA-ID");
        assertThat(beta.get("timezone_id")).isEqualTo("Europe/Madrid");
    }

    // =========================================================================
    // Test 3: sedes preexistentes conservan IDs, relaciones y valores
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Sedes insertadas antes de V4–V8 conservan IDs, FK y nombres")
    void sedesPreexistentesConservadas() {
        List<Map<String, Object>> sites = jdbc.queryForList(
                "SELECT id, organization_id, code, name FROM sites WHERE id IN (?, ?)",
                SITE_ALFA_ID, SITE_BETA_ID);

        assertThat(sites).as("Deben conservarse exactamente 2 sedes").hasSize(2);

        Map<String, Object> siteAlfa = sites.stream()
                .filter(s -> s.get("id").toString().equalsIgnoreCase(SITE_ALFA_ID))
                .findFirst().orElseThrow();
        assertThat(siteAlfa.get("organization_id").toString()).isEqualToIgnoringCase(ORG_ALFA_ID);
        assertThat(siteAlfa.get("code")).isEqualTo("HQ");
        assertThat(siteAlfa.get("name")).isEqualTo("Sede Alfa Principal");

        Map<String, Object> siteBeta = sites.stream()
                .filter(s -> s.get("id").toString().equalsIgnoreCase(SITE_BETA_ID))
                .findFirst().orElseThrow();
        assertThat(siteBeta.get("organization_id").toString()).isEqualToIgnoringCase(ORG_BETA_ID);
        assertThat(siteBeta.get("code")).isEqualTo("HQ");
        assertThat(siteBeta.get("name")).isEqualTo("Sede Beta Principal");
    }

    // =========================================================================
    // Test 4: catálogo de roles creado por V4 está presente
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("V4: catálogo de roles ADMINISTRADOR/SUPERVISOR/COLABORADOR presente tras actualización")
    void catalogoRolesPresente() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE code IN ('ADMINISTRADOR', 'SUPERVISOR', 'COLABORADOR')",
                Integer.class);
        assertThat(count)
                .as("Los 3 roles del catálogo deben estar presentes tras aplicar V4")
                .isEqualTo(3);
    }

    // =========================================================================
    // Test 5: tablas de identidad existen tras la actualización
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("Tablas roles, app_users y password_reset_tokens existen tras V4–V8")
    void tablasIdentidadExisten() {
        for (String tabla : java.util.List.of("roles", "app_users", "password_reset_tokens")) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                    "WHERE TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'",
                    Integer.class, tabla);
            assertThat(count)
                    .as("Tabla " + tabla + " debe existir tras la actualización de identidad")
                    .isEqualTo(1);
        }
    }

    // =========================================================================
    // Test 6: V14 activo — employee_id no nulo sin empleado existente rechazado por FK
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("V14 activo: employee_id inexistente rechazado por fk_app_users_employee")
    void v14FkEmployeeIdActivo() {
        assertThatExceptionType(
                () -> jdbc.update("""
                        INSERT INTO app_users
                            (organization_id, id, email, normalized_email, display_name,
                             password_hash, role_code, employee_id)
                        VALUES (?, NEWID(), 'x@ej.com', 'x@ej.com', 'X',
                                'hash', 'COLABORADOR', NEWID())
                        """, ORG_ALFA_ID),
                DataIntegrityViolationException.class,
                "fk_app_users_employee");
    }

    // =========================================================================
    // Test 7: V8 — collation de normalized_email es Latin1_General_100_BIN2
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("V8: normalized_email tiene collation Latin1_General_100_BIN2")
    void v8CollationNormalizedEmailBin2() {
        String collation = jdbc.queryForObject(
                """
                SELECT collation_name
                FROM sys.columns
                WHERE object_id = OBJECT_ID('app_users')
                  AND name = 'normalized_email'
                """,
                String.class);
        assertThat(collation)
                .as("normalized_email debe tener collation BIN2 para comparación binaria exacta del valor canónico")
                .isEqualTo("Latin1_General_100_BIN2");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void assertThatExceptionType(Runnable action,
                                                 Class<? extends Exception> type,
                                                 String constraintName) {
        try {
            action.run();
            throw new AssertionError(
                    "Se esperaba " + type.getSimpleName() + " con restricción '" +
                    constraintName + "', pero no se lanzó ninguna excepción");
        } catch (Exception ex) {
            assertThat(ex)
                    .isInstanceOf(type)
                    .hasMessageContaining(constraintName);
        }
    }
}
