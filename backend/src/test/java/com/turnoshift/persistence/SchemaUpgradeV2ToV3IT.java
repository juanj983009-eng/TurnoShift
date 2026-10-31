package com.turnoshift.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Prueba de actualización: verifica que la migración V3 puede aplicarse sobre
 * una base que ya tiene datos válidos de V1/V2, sin perder filas ni romper
 * relaciones existentes.
 *
 * <p><b>Escenario:</b>
 * <ol>
 *   <li>Base efímera y exclusiva para esta clase.</li>
 *   <li>Se aplican únicamente V1 y V2 (target = 2).</li>
 *   <li>Se insertan dos organizaciones y dos sedes con datos válidos para V3.</li>
 *   <li>Se aplica V3.</li>
 *   <li>Se verifica que filas, relaciones e identificadores se conservan.</li>
 *   <li>Se verifican las restricciones nuevas de V3 y la eliminación del índice.</li>
 * </ol>
 *
 * <p><b>Patrón:</b> Flyway programático con control de versión explícito.
 * Este patrón se permite únicamente para esta prueba de ruta de actualización;
 * el resto de las pruebas de producción usa FlywayAutoConfiguration.
 *
 * <p><b>No limpia ni repara migraciones aplicadas. No usa bases persistentes del usuario.</b>
 *
 * <p><b>Imagen:</b> SQL Server 2022 Developer Edition — solo desarrollo/pruebas.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT: Actualización V2→V3 — datos preexistentes se conservan")
class SchemaUpgradeV2ToV3IT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    static JdbcTemplate jdbc;

    // UUIDs fijos para verificar que los identificadores se conservan tras V3
    static final String ORG_ALFA_ID = "A1A10000-0000-0000-0000-000000000001";
    static final String ORG_BETA_ID = "B2B20000-0000-0000-0000-000000000002";
    static final String SITE_ALFA_ID = "A1A10000-0000-0000-0001-000000000001";
    static final String SITE_BETA_ID = "B2B20000-0000-0000-0001-000000000002";

    /**
     * Aplica solo V1+V2, inserta datos y luego aplica V3.
     * La migración progresiva simula una base de datos real que ya existe en producción.
     */
    @BeforeAll
    static void prepararYMigrar() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setUrl(SQL_SERVER.getJdbcUrl());
        ds.setUsername(SQL_SERVER.getUsername());
        ds.setPassword(SQL_SERVER.getPassword());

        // Paso 1: aplicar únicamente hasta V2
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("2")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load()
                .migrate();

        jdbc = new JdbcTemplate(ds);

        // Paso 2: insertar datos válidos (max_active_sites=1, policy_version=NULL)
        // que deben sobrevivir a V3 sin modificación.
        jdbc.update("""
                INSERT INTO organizations
                    (id, code, display_name, service_status, timezone_id,
                     max_active_employees, max_active_sites)
                VALUES (?, 'ALFA-UPG', 'Empresa Alfa Upgrade', 'ACTIVA', 'America/Bogota', 10, 1)
                """, ORG_ALFA_ID);

        jdbc.update("""
                INSERT INTO organizations
                    (id, code, display_name, service_status, timezone_id,
                     max_active_employees, max_active_sites)
                VALUES (?, 'BETA-UPG', 'Empresa Beta Upgrade', 'ACTIVA', 'Europe/Madrid', 5, 1)
                """, ORG_BETA_ID);

        jdbc.update("""
                INSERT INTO sites (organization_id, id, code, name, is_active)
                VALUES (?, ?, 'HQ', 'Sede Alfa Principal', 1)
                """, ORG_ALFA_ID, SITE_ALFA_ID);

        jdbc.update("""
                INSERT INTO sites (organization_id, id, code, name, is_active)
                VALUES (?, ?, 'HQ', 'Sede Beta Principal', 1)
                """, ORG_BETA_ID, SITE_BETA_ID);

        // Paso 3: aplicar V3
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("3")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    // =========================================================================
    // Test 1: Historial contiene exactamente V1, V2, V3
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Historial contiene V1, V2 y V3 tras migración progresiva")
    void historialContieneV1V2V3() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class
        );
        assertThat(versiones)
                .as("Migración progresiva V1→V2→V3 debe registrar las tres versiones")
                .containsExactly("1", "2", "3");
    }

    // =========================================================================
    // Test 2: Organizaciones preexistentes conservan identificadores y datos
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Organizaciones preexistentes conservan sus IDs y todos sus campos")
    void organizacionesPreexistentesConservadas() {
        List<Map<String, Object>> orgs = jdbc.queryForList(
                "SELECT id, code, display_name, timezone_id, max_active_sites, current_policy_version_id " +
                "FROM organizations WHERE id IN (?, ?) ORDER BY code",
                ORG_ALFA_ID, ORG_BETA_ID
        );

        assertThat(orgs).as("Deben existir exactamente 2 organizaciones").hasSize(2);

        Map<String, Object> alfa = orgs.get(0); // ALFA-UPG < BETA-UPG
        assertThat(alfa.get("id").toString()).isEqualToIgnoringCase(ORG_ALFA_ID);
        assertThat(alfa.get("code")).isEqualTo("ALFA-UPG");
        assertThat(alfa.get("timezone_id")).isEqualTo("America/Bogota");
        assertThat(alfa.get("max_active_sites")).isEqualTo(1);
        assertThat(alfa.get("current_policy_version_id")).isNull();

        Map<String, Object> beta = orgs.get(1);
        assertThat(beta.get("id").toString()).isEqualToIgnoringCase(ORG_BETA_ID);
        assertThat(beta.get("code")).isEqualTo("BETA-UPG");
        assertThat(beta.get("timezone_id")).isEqualTo("Europe/Madrid");
    }

    // =========================================================================
    // Test 3: Sedes preexistentes conservan IDs y relaciones FK
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Sedes preexistentes conservan IDs, códigos y FK a sus organizaciones")
    void sedesPreexistentesConservadas() {
        List<Map<String, Object>> sites = jdbc.queryForList(
                "SELECT id, organization_id, code, name FROM sites WHERE id IN (?, ?) ORDER BY id",
                SITE_ALFA_ID, SITE_BETA_ID
        );

        assertThat(sites).as("Deben existir exactamente 2 sedes").hasSize(2);

        Map<String, Object> siteAlfa = sites.stream()
                .filter(s -> s.get("id").toString().equalsIgnoreCase(SITE_ALFA_ID))
                .findFirst().orElseThrow();
        assertThat(siteAlfa.get("id").toString()).isEqualToIgnoringCase(SITE_ALFA_ID);
        assertThat(siteAlfa.get("organization_id").toString()).isEqualToIgnoringCase(ORG_ALFA_ID);
        assertThat(siteAlfa.get("code")).isEqualTo("HQ");

        Map<String, Object> siteBeta = sites.stream()
                .filter(s -> s.get("id").toString().equalsIgnoreCase(SITE_BETA_ID))
                .findFirst().orElseThrow();
        assertThat(siteBeta.get("id").toString()).isEqualToIgnoringCase(SITE_BETA_ID);
        assertThat(siteBeta.get("organization_id").toString()).isEqualToIgnoringCase(ORG_BETA_ID);
    }

    // =========================================================================
    // Test 4: V3 — max_active_sites != 1 rechazado tras migración
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("V3 activo: max_active_sites = 2 rechazado tras migración")
    void v3MaxActiveSitesRechazado() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO organizations
                            (id, code, display_name, service_status, timezone_id,
                             max_active_employees, max_active_sites)
                        VALUES (NEWID(), 'MULTI-V3', 'Org Multisede', 'ACTIVA', 'UTC', 1, 2)
                        """)
        )
                .as("max_active_sites = 2 debe rechazarse tras aplicar V3")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // Test 5: V3 — current_policy_version_id no NULL rechazado
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("V3 activo: current_policy_version_id no NULL rechazado")
    void v3PolicyVersionNoNullRechazado() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO organizations
                            (id, code, display_name, service_status, timezone_id,
                             max_active_employees, max_active_sites, current_policy_version_id)
                        VALUES (NEWID(), 'POL-V3', 'Org con Politica', 'ACTIVA', 'UTC', 1, 1, NEWID())
                        """)
        )
                .as("current_policy_version_id no NULL debe rechazarse tras aplicar V3")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // Test 6: V3 — unicidad de código sigue vigente (índice explícito eliminado)
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("V3 activo: unicidad de código de org sigue vigente (restricción UNIQUE intacta)")
    void v3UnicidadCodigoIntacta() {
        // El índice uix_organizations_code fue eliminado en V3.
        // La restricción uq_organizations_code sigue vigente.
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO organizations
                            (id, code, display_name, service_status, timezone_id,
                             max_active_employees, max_active_sites)
                        VALUES (NEWID(), 'ALFA-UPG', 'Duplicado de Alfa', 'ACTIVA', 'UTC', 1, 1)
                        """)
        )
                .as("Código ALFA-UPG ya existe; uq_organizations_code debe rechazarlo aunque uix fue eliminado")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // Test 7: Índice explícito redundante ya no existe en sys.indexes
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("V3: índice uix_organizations_code eliminado de sys.indexes")
    void v3IndiceRedundanteEliminado() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys.indexes " +
                "WHERE name = 'uix_organizations_code' AND object_id = OBJECT_ID('organizations')",
                Integer.class
        );
        assertThat(count)
                .as("uix_organizations_code debe haber sido eliminado por V3")
                .isZero();
    }

    // =========================================================================
    // Test 8: Restricción UNIQUE uq_organizations_code sigue en sys.key_constraints
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("V3: restricción uq_organizations_code sigue presente tras drop del índice")
    void v3RestriccionUniqueConservada() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys.key_constraints " +
                "WHERE name = 'uq_organizations_code' AND parent_object_id = OBJECT_ID('organizations')",
                Integer.class
        );
        assertThat(count)
                .as("La restricción uq_organizations_code debe persistir tras eliminar el índice explícito")
                .isEqualTo(1);
    }
}
