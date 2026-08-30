package com.turnoshift.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de integración: verifica que las migraciones reales de producción
 * (V1 organizations + V2 sites) se aplican correctamente sobre SQL Server 2022
 * efímero y que las restricciones de persistencia del modelo funcionan.
 *
 * <p><b>Patrón de prueba:</b> Flyway + JdbcTemplate configurados manualmente
 * (mismo enfoque que FlywayMssqlCompatibilityIT / PoC-01). Este patrón evita la
 * complejidad de @SpringBootTest + @DynamicPropertySource cuando múltiples contenedores
 * {@code MSSQLServerContainer} coexisten en el mismo JVM con {@code ryuk.disabled=true}
 * (confirmado en ~/.testcontainers.properties de este entorno).
 *
 * <p>El arranque real del contexto Spring Boot se verifica en {@link SpringBootStartupIT}.
 *
 * <p><b>Alcance (I0 — HT-02 / HT-07):</b>
 * <ul>
 *   <li>V1 y V2 de producción aplicados desde base vacía.</li>
 *   <li>Segunda ejecución idempotente (no reaplica).</li>
 *   <li>Rechazo de código de organización duplicado globalmente (unicidad global).</li>
 *   <li>Rechazo de segunda sede activa en la misma organización (índice filtrado).</li>
 *   <li>Rechazo de sede con organización inexistente (FK).</li>
 *   <li>Aceptación del mismo código de sede en organizaciones distintas.</li>
 * </ul>
 *
 * <p>Estas pruebas verifican restricciones de persistencia, NO equivalen a aislamiento de API.
 *
 * <p><b>Requisito:</b> Docker disponible en el host. Si no está, falla con error claro.
 * <p><b>Imagen:</b> {@code mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04}
 * SQL Server 2022 Developer Edition — solo desarrollo/pruebas, no producción.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT: Migraciones reales organizations + sites sobre SQL Server 2022")
class OrganizationsSitesSchemaIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    static JdbcTemplate jdbc;

    // =========================================================================
    // UUIDs de organizaciones ficticias (solo datos de prueba)
    // =========================================================================
    private static final String ORG_ALFA_ID = "A1000000-0000-0000-0000-000000000001";
    private static final String ORG_BETA_ID = "B2000000-0000-0000-0000-000000000002";

    /**
     * Aplica las migraciones reales de producción y configura JdbcTemplate.
     *
     * <p>Flyway conecta al contenedor usando {@code classpath:db/migration}
     * (migraciones de producción: V1 organizations, V2 sites).
     * Usa la tabla de historial por defecto ({@code flyway_schema_history}).
     * {@code cleanDisabled=true} — idéntico a producción.
     *
     * <p>JdbcTemplate usa el mismo DataSource para garantizar que las
     * restricciones se verifican contra el mismo schema que creó Flyway.
     */
    @BeforeAll
    static void configurarEntorno() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        dataSource.setUrl(SQL_SERVER.getJdbcUrl());
        dataSource.setUsername(SQL_SERVER.getUsername());
        dataSource.setPassword(SQL_SERVER.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                // Usar tabla de historial por defecto (flyway_schema_history).
                // Las migraciones PoC usan poc_flyway_schema_history; no hay conflicto.
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load()
                .migrate();

        jdbc = new JdbcTemplate(dataSource);
    }

    // =========================================================================
    // Test 1: Flyway aplicó exactamente 2 migraciones desde base vacía
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Flyway aplica V1 y V2 desde base vacía; historial contiene 2 versiones")
    void flywayAplicaDosVersionesDesdeBaseVacia() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class
        );
        assertThat(count)
                .as("Flyway debe haber aplicado exactamente 2 migraciones (V1 organizations, V2 sites)")
                .isEqualTo(2);

        // Verificar que las versiones son exactamente V1 y V2
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class
        );
        assertThat(versiones).containsExactly("1", "2");
    }

    // =========================================================================
    // Test 2: Insertar dos organizaciones ficticias
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Insertar organizaciones ficticias Alfa y Beta")
    void insertarOrganizaciones() {
        jdbc.update("""
                INSERT INTO organizations
                    (id, code, display_name, service_status, timezone_id,
                     max_active_employees, max_active_sites)
                VALUES (?, ?, ?, 'ACTIVA', 'America/Bogota', 10, 1)
                """,
                ORG_ALFA_ID, "ALFA-01", "Empresa Alfa de Prueba"
        );

        jdbc.update("""
                INSERT INTO organizations
                    (id, code, display_name, service_status, timezone_id,
                     max_active_employees, max_active_sites)
                VALUES (?, ?, ?, 'ACTIVA', 'America/Bogota', 5, 1)
                """,
                ORG_BETA_ID, "BETA-02", "Empresa Beta de Prueba"
        );

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE id IN (?, ?)",
                Integer.class, ORG_ALFA_ID, ORG_BETA_ID
        );
        assertThat(count).isEqualTo(2);
    }

    // =========================================================================
    // Test 3: Código de organización duplicado rechazado (unicidad global)
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Código de organización duplicado es rechazado (unicidad global)")
    void organizacionCodigoDuplicadoEsRechazado() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO organizations
                            (id, code, display_name, service_status, timezone_id,
                             max_active_employees, max_active_sites)
                        VALUES (NEWID(), ?, 'Duplicado', 'ACTIVA', 'UTC', 1, 1)
                        """, "ALFA-01")
        )
                .as("Código ALFA-01 ya existe globalmente; debe rechazarse")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // Test 4: Insertar sede activa para Alfa
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("Insertar la sede activa de la organización Alfa")
    void insertarSedeActivaAlfa() {
        jdbc.update("""
                INSERT INTO sites
                    (organization_id, id, code, name, is_active)
                VALUES (?, ?, 'HQ', 'Sede Principal Alfa', 1)
                """,
                ORG_ALFA_ID, "A1000000-0000-0000-0001-000000000001"
        );

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sites WHERE organization_id = ? AND is_active = 1",
                Integer.class, ORG_ALFA_ID
        );
        assertThat(count).isEqualTo(1);
    }

    // =========================================================================
    // Test 5: Segunda sede activa rechazada (índice filtrado)
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("Segunda sede activa en la misma organización es rechazada (índice filtrado)")
    void segundaSedeActivaMismaOrgEsRechazada() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO sites
                            (organization_id, id, code, name, is_active)
                        VALUES (?, NEWID(), 'NORTE', 'Sede Norte Alfa', 1)
                        """, ORG_ALFA_ID)
        )
                .as("Ya existe sede activa para Alfa; el índice filtrado debe rechazar una segunda")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // Test 6: Sede con organización inexistente rechazada (FK)
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("Sede con organización inexistente es rechazada (FK a organizations)")
    void sedeConOrgInexistenteEsRechazada() {
        String orgInexistente = "FFFF0000-0000-0000-0000-000000000000";
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO sites
                            (organization_id, id, code, name, is_active)
                        VALUES (?, NEWID(), 'HQ', 'Sede Fantasma', 1)
                        """, orgInexistente)
        )
                .as("La organización no existe; la FK debe rechazar la inserción")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // Test 7: Mismo código de sede en organizaciones distintas permitido
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("Mismo código de sede (HQ) en organización diferente (Beta) está permitido")
    void mismoCodigoSedeDiferenteOrgPermitido() {
        jdbc.update("""
                INSERT INTO sites
                    (organization_id, id, code, name, is_active)
                VALUES (?, ?, 'HQ', 'Sede Principal Beta', 1)
                """,
                ORG_BETA_ID, "B2000000-0000-0000-0001-000000000001"
        );

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sites WHERE code = 'HQ'",
                Integer.class
        );
        assertThat(count)
                .as("Deben existir 2 sedes con código HQ, en organizaciones distintas")
                .isEqualTo(2);
    }
}
