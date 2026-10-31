package com.turnoshift.health;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración: verifica que el contexto Spring Boot arranca con
 * Flyway auto-configurado por Spring (no invocado manualmente) y que las
 * migraciones reales de producción se aplican correctamente.
 *
 * <p><b>Qué demuestra esta prueba:</b>
 * <ol>
 *   <li>El bean {@link Flyway} es inyectado por el contexto Spring Boot
 *       (prueba que FlywayAutoConfiguration se activó).</li>
 *   <li>DataSource y Flyway comparten la misma configuración de conexión
 *       (mismo host y puerto que el contenedor efímero).</li>
 *   <li>{@code flyway_schema_history} contiene exactamente las versiones de producción.</li>
 *   <li>Las tablas {@code organizations} y {@code sites} existen.</li>
 *   <li>Una segunda llamada a {@code flyway.migrate()} no aplica migraciones adicionales.</li>
 *   <li>GET /api/health responde HTTP 200.</li>
 * </ol>
 *
 * <p><b>Requisito:</b> Docker disponible; {@code spring-boot-starter-flyway} en el classpath
 * para activar {@code FlywayAutoConfiguration}. Sin él, el contexto arrancaría sin Flyway
 * y esta prueba fallaría al intentar inyectar el bean {@code Flyway}.
 *
 * <p><b>Imagen:</b> SQL Server 2022 Developer Edition — solo desarrollo/pruebas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("IT: Arranque Spring Boot + FlywayAutoConfiguration + SQL Server 2022")
class SpringBootStartupIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    /**
     * Inyecta la URL del contenedor como propiedades del contexto Spring Boot.
     * @DynamicPropertySource tiene mayor prioridad que application.properties.
     * Garantiza que DataSource y Flyway usen exactamente este contenedor.
     */
    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    /** Bean inyectado por FlywayAutoConfiguration — falla si el módulo no está en classpath. */
    @Autowired
    Flyway flyway;

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @LocalServerPort
    int puerto;

    // =========================================================================
    // Test 1: Flyway y DataSource comparten la misma conexión (mismo contenedor)
    // =========================================================================

    @Test
    @DisplayName("Flyway y DataSource apuntan al mismo servidor que el contenedor efímero")
    void flywayYDataSourceMismoServidor() throws Exception {
        int puertoContenedor = SQL_SERVER.getMappedPort(1433);

        // URL de la conexión real del DataSource (sin credenciales)
        String urlDataSource;
        try (Connection conn = dataSource.getConnection()) {
            urlDataSource = conn.getMetaData().getURL();
        }
        assertThat(urlDataSource)
                .as("DataSource debe apuntar al contenedor efímero en localhost:" + puertoContenedor)
                .contains("localhost:" + puertoContenedor);

        // Flyway debe usar el mismo DataSource auto-configurado
        // Se verifica mediante la URL de la conexión interna de Flyway
        String urlFlyway;
        try (Connection conn = flyway.getConfiguration().getDataSource().getConnection()) {
            urlFlyway = conn.getMetaData().getURL();
        }
        assertThat(urlFlyway)
                .as("Flyway debe conectar al mismo servidor que el DataSource")
                .contains("localhost:" + puertoContenedor);
    }

    // =========================================================================
    // Test 2: FlywayAutoConfiguration aplicó las migraciones reales al arrancar
    // =========================================================================

    @Test
    @DisplayName("flyway_schema_history contiene las versiones de producción (V1 a V19)")
    void flywayAutoConfigAplicoMigraciones() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class
        );
        assertThat(versiones)
                .as("Flyway auto-config debe haber aplicado V1 a V19 al arrancar Spring Boot")
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19");
    }

    // =========================================================================
    // Test 3: Las tablas de producción existen tras el arranque
    // =========================================================================

    @Test
    @DisplayName("Las tablas de producción existen tras el arranque")
    void tablasDeProduccionExisten() {
        for (String tabla : List.of(
                "organizations", "sites", "roles", "app_users", "password_reset_tokens",
                "platform_operators", "api_operations", "positions", "employees",
                "planning_policy_versions", "employee_unavailabilities",
                "schedules", "schedule_drafts", "assignments", "assignment_revisions",
                "draft_assignments", "planning_change_sets", "planning_warning_acknowledgements",
                "schedule_publications", "publication_assignments"
        )) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'",
                    Integer.class, tabla);
            assertThat(count).as("Tabla " + tabla + " debe existir").isEqualTo(1);
        }
    }

    // =========================================================================
    // Test 4: Segunda llamada a migrate() es idempotente (0 migraciones nuevas)
    // =========================================================================

    @Test
    @DisplayName("flyway.migrate() invocado de nuevo aplica 0 migraciones adicionales")
    void flywayMigrateEsIdempotente() {
        int nuevas = flyway.migrate().migrationsExecuted;
        assertThat(nuevas)
                .as("Segunda llamada a migrate() no debe aplicar migraciones adicionales")
                .isZero();
    }

    // =========================================================================
    // Test 5: Endpoint de salud (verificación mínima del contexto HTTP)
    // =========================================================================

    @Test
    @DisplayName("GET /api/health responde 200 OK con status UP")
    void healthEndpointRespondeCorrecto() {
        String url = "http://localhost:" + puerto + "/api/health";
        ResponseEntity<String> response = new RestTemplate().getForEntity(url, String.class);

        assertThat(response.getStatusCode())
                .as("El endpoint /api/health debe responder HTTP 200")
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .as("El cuerpo debe contener status:UP")
                .contains("\"status\"")
                .contains("UP");
    }
}
