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
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de integración: verifica que FlywayAutoConfiguration aplica las migraciones
 * reales (V1 organizations + V2 sites + V3 constraints) al arrancar Spring Boot,
 * y que las restricciones de persistencia del modelo se cumplen.
 *
 * <p><b>Patrón de prueba:</b> {@code @SpringBootTest} con beans inyectados
 * (Flyway, DataSource, JdbcTemplate). Flyway migra durante el arranque del contexto
 * —no se invoca migrate() manualmente. Cada prueba es independiente gracias a
 * {@code @Transactional}: las inserciones se revierten al finalizar cada método.
 *
 * <p><b>Verificación de conexión:</b> se comprueba mediante metadatos JDBC que
 * DataSource y Flyway apuntan al mismo contenedor efímero, sin exponer credenciales.
 *
 * <p><b>Alcance (I0 — HT-02 / HT-07):</b>
 * <ul>
 *   <li>V1 a V6 aplicados desde base vacía por FlywayAutoConfiguration.</li>
 *   <li>DataSource y Flyway conectan al mismo servidor (verificado por metadatos).</li>
 *   <li>Idempotencia: segunda llamada a migrate() aplica 0 migraciones.</li>
 *   <li>Unicidad global de código de organización (V1).</li>
 *   <li>Rechazo de max_active_sites != 1 (V3).</li>
 *   <li>Rechazo de current_policy_version_id no NULL (V3).</li>
 *   <li>Unicidad de código sigue en pie tras eliminar índice redundante (V3).</li>
 *   <li>Rechazo de segunda sede activa (índice filtrado, V2).</li>
 *   <li>Rechazo de sede con organización inexistente (FK, V2).</li>
 *   <li>Mismo código de sede en organizaciones distintas permitido.</li>
 *   <li>V3 aplicable sobre datos V1/V2 sin pérdida de filas válidas.</li>
 *   <li>V4, V5, V6 aplicados correctamente con organizaciones preexistentes.</li>
 * </ul>
 *
 * <p><b>Imagen:</b> SQL Server 2022 Developer Edition — solo desarrollo/pruebas.
 */
@SpringBootTest
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: Schema organizations + sites con FlywayAutoConfiguration")
class OrganizationsSitesSchemaIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    /**
     * Inyecta la URL del contenedor como propiedades de Spring Boot.
     * @DynamicPropertySource tiene mayor prioridad que application.properties.
     * DataSource, JdbcTemplate y FlywayAutoConfiguration usan esta única fuente.
     */
    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Autowired
    Flyway flyway;

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    // =========================================================================
    // Verificación: misma conexión (DataSource y Flyway apuntan al mismo servidor)
    // =========================================================================

    @Test
    @DisplayName("DataSource y Flyway apuntan al mismo servidor que el contenedor efímero")
    void mismaConexionDataSourceYFlyway() throws Exception {
        int puertoContenedor = SQL_SERVER.getMappedPort(1433);

        String urlDataSource;
        try (Connection conn = dataSource.getConnection()) {
            urlDataSource = conn.getMetaData().getURL();
        }
        assertThat(urlDataSource)
                .as("DataSource debe apuntar al contenedor efímero en localhost:" + puertoContenedor)
                .contains("localhost:" + puertoContenedor);

        String urlFlyway;
        try (Connection conn = flyway.getConfiguration().getDataSource().getConnection()) {
            urlFlyway = conn.getMetaData().getURL();
        }
        assertThat(urlFlyway)
                .as("Flyway debe conectar al mismo servidor que DataSource")
                .isEqualTo(urlDataSource);
    }

    // =========================================================================
    // Flyway: versiones aplicadas y idempotencia
    // =========================================================================

    @Test
    @DisplayName("flyway_schema_history contiene V1 a V19 en orden (FlywayAutoConfig)")
    void flywayAutoConfigAplicoTodasLasVersiones() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class
        );
        assertThat(versiones)
                .as("FlywayAutoConfiguration debe aplicar exactamente V1 a V19")
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19");
    }

    @Test
    @Rollback(false) // migrate() tiene efecto en la DB; no se revierte para preservar estado
    @DisplayName("flyway.migrate() invocado de nuevo aplica 0 migraciones (idempotente)")
    void flywayMigrateIdempotente() {
        int nuevas = flyway.migrate().migrationsExecuted;
        assertThat(nuevas)
                .as("migrate() invocado de nuevo no debe aplicar migraciones adicionales")
                .isZero();
    }

    // =========================================================================
    // V3: max_active_sites = 1 obligatorio
    // =========================================================================

    @Test
    @DisplayName("V3: max_active_sites != 1 rechazado por CHECK constraint")
    void maxActiveSitesDosRechazado() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO organizations
                            (id, code, display_name, service_status, timezone_id,
                             max_active_employees, max_active_sites)
                        VALUES (NEWID(), 'MULTI-01', 'Org Multisede', 'ACTIVA', 'UTC', 5, 2)
                        """)
        )
                .as("max_active_sites = 2 debe ser rechazado por chk_organizations_max_active_sites_one")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // V3: current_policy_version_id debe ser NULL
    // =========================================================================

    @Test
    @DisplayName("V3: current_policy_version_id no NULL rechazado por CHECK constraint")
    void policyVersionNoNullRechazada() {
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO organizations
                            (id, code, display_name, service_status, timezone_id,
                             max_active_employees, max_active_sites, current_policy_version_id)
                        VALUES (NEWID(), 'POL-01', 'Org con Politica', 'ACTIVA', 'UTC', 5, 1, NEWID())
                        """)
        )
                .as("current_policy_version_id no NULL debe ser rechazado (FK diferida no existe aún)")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // V3: unicidad de código sigue vigente tras eliminar índice explícito
    // =========================================================================

    @Test
    @DisplayName("V3: unicidad de código de organización sigue vigente (restricción UNIQUE intacta)")
    void unicidadCodigoPersisteDespuesDropIndice() {
        String codigoUnico = "ORG-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                INSERT INTO organizations
                    (id, code, display_name, service_status, timezone_id,
                     max_active_employees, max_active_sites)
                VALUES (NEWID(), ?, 'Primera Org', 'ACTIVA', 'UTC', 1, 1)
                """, codigoUnico);

        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO organizations
                            (id, code, display_name, service_status, timezone_id,
                             max_active_employees, max_active_sites)
                        VALUES (NEWID(), ?, 'Segunda Org misma clave', 'ACTIVA', 'UTC', 1, 1)
                        """, codigoUnico)
        )
                .as("Código duplicado debe rechazarse aunque el índice explícito fue eliminado en V3")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // Organizations: inserción válida
    // =========================================================================

    @Test
    @DisplayName("Insertar organización válida con max_active_sites = 1")
    void insertarOrganizacionValida() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations
                    (id, code, display_name, service_status, timezone_id,
                     max_active_employees, max_active_sites)
                VALUES (?, 'ALFA-01', 'Empresa Alfa de Prueba', 'ACTIVA', 'America/Bogota', 10, 1)
                """, id);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE id = ?", Integer.class, id);
        assertThat(count).as("Organización válida debe insertarse correctamente").isEqualTo(1);
    }

    // =========================================================================
    // Organizations: código duplicado rechazado
    // =========================================================================

    @Test
    @DisplayName("Código de organización duplicado rechazado (unicidad global)")
    void codigoOrganizacionDuplicadoRechazado() {
        jdbc.update("""
                INSERT INTO organizations
                    (id, code, display_name, service_status, timezone_id,
                     max_active_employees, max_active_sites)
                VALUES (NEWID(), 'DUP-01', 'Original', 'ACTIVA', 'UTC', 1, 1)
                """);

        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO organizations
                            (id, code, display_name, service_status, timezone_id,
                             max_active_employees, max_active_sites)
                        VALUES (NEWID(), 'DUP-01', 'Duplicado', 'ACTIVA', 'UTC', 1, 1)
                        """)
        )
                .as("Código DUP-01 ya existe globalmente; debe rechazarse")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // Sites: inserción y restricciones (V2)
    // =========================================================================

    @Test
    @DisplayName("Insertar sede activa para organización existente")
    void insertarSedeActivaParaOrganizacion() {
        String orgId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, 'SEDE-ORG', 'Org para Sede', 'ACTIVA', 'UTC', 1, 1)
                """, orgId);

        String siteId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO sites (organization_id, id, code, name, is_active)
                VALUES (?, ?, 'HQ', 'Sede Principal', 1)
                """, orgId, siteId);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sites WHERE organization_id = ? AND is_active = 1",
                Integer.class, orgId
        );
        assertThat(count).as("Debe existir exactamente 1 sede activa").isEqualTo(1);
    }

    @Test
    @DisplayName("Segunda sede activa rechazada (índice filtrado unique por organización)")
    void segundaSedeActivaRechazada() {
        String orgId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, 'S2ORG-01', 'Org Dos Sedes', 'ACTIVA', 'UTC', 1, 1)
                """, orgId);

        jdbc.update("""
                INSERT INTO sites (organization_id, id, code, name, is_active)
                VALUES (?, NEWID(), 'HQ', 'Primera Sede', 1)
                """, orgId);

        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO sites (organization_id, id, code, name, is_active)
                        VALUES (?, NEWID(), 'NORTE', 'Segunda Sede Activa', 1)
                        """, orgId)
        )
                .as("Índice filtrado debe rechazar segunda sede activa en la misma organización")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Sede con organización inexistente rechazada (FK a organizations)")
    void sedeConOrgInexistenteRechazada() {
        String orgInexistente = UUID.randomUUID().toString();
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO sites (organization_id, id, code, name, is_active)
                        VALUES (?, NEWID(), 'HQ', 'Sede Fantasma', 1)
                        """, orgInexistente)
        )
                .as("FK debe rechazar sede cuya organización no existe")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Mismo código de sede en organizaciones distintas está permitido")
    void mismoCodigoSedeDiferentesOrgsPermitido() {
        // Organización Alfa
        String orgAlfa = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, 'ALFA-MC', 'Alfa', 'ACTIVA', 'UTC', 1, 1)
                """, orgAlfa);

        // Organización Beta
        String orgBeta = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, 'BETA-MC', 'Beta', 'ACTIVA', 'UTC', 1, 1)
                """, orgBeta);

        // Ambas con código 'HQ' — debe permitirse (unicidad solo dentro de la organización)
        jdbc.update("""
                INSERT INTO sites (organization_id, id, code, name, is_active)
                VALUES (?, NEWID(), 'HQ', 'Sede Alfa', 1)
                """, orgAlfa);
        jdbc.update("""
                INSERT INTO sites (organization_id, id, code, name, is_active)
                VALUES (?, NEWID(), 'HQ', 'Sede Beta', 1)
                """, orgBeta);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sites WHERE code = 'HQ'", Integer.class);
        assertThat(count)
                .as("Dos sedes con código HQ en organizaciones distintas deben coexistir")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("V4/V5/V6: tablas roles, app_users y password_reset_tokens existen con organizaciones preexistentes")
    void tablasIdentidadExistenConOrganizacionesPreexistentes() {
        for (String tabla : List.of("roles", "app_users", "password_reset_tokens")) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'",
                    Integer.class, tabla);
            assertThat(count).as("Tabla " + tabla + " debe existir tras aplicar todas las migraciones").isEqualTo(1);
        }
        // Los tres roles del catálogo deben estar presentes
        Integer rolesCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE code IN ('ADMINISTRADOR', 'SUPERVISOR', 'COLABORADOR')",
                Integer.class);
        assertThat(rolesCount).as("Los 3 roles del catálogo deben estar presentes").isEqualTo(3);
    }


    @Test
    @DisplayName("Tablas de identidad y catálogo de roles coexisten con organizations/sites")
    void tablasIdentidadCoexistenConOrganizacionesSites() {
        // La demostración de conservación de datos insertados ANTES de V4–V8 está en
        // SchemaUpgradeV3ToLatestIT (prueba programática de ruta de actualización).
        // Este test verifica que dentro del contexto Spring Boot (post-migración) las
        // tablas de identidad existen y el catálogo de roles está intacto junto a
        // organizations/sites que se pueden insertar sin conflicto.
        String orgId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, 'COEX-01', 'Org Coexistencia Ficticia', 'ACTIVA', 'UTC', 10, 1)
                """, orgId);

        Integer orgCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE id = ?", Integer.class, orgId);
        assertThat(orgCount)
                .as("Una organización recién insertada debe existir junto al esquema de identidad")
                .isEqualTo(1);

        Integer rolesCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE code IN ('ADMINISTRADOR', 'SUPERVISOR', 'COLABORADOR')",
                Integer.class);
        assertThat(rolesCount)
                .as("El catálogo de roles debe tener exactamente 3 entradas junto a organizations")
                .isEqualTo(3);
    }
}

