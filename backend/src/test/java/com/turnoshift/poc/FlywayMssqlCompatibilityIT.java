package com.turnoshift.poc;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PoC-01 — Prueba técnica de compatibilidad: Flyway + SQL Server 2022 + Testcontainers.
 *
 * <p>Alcance estricto de I0 (base de HT-02 y HT-07):
 * <ul>
 *   <li>Verifica que un contenedor SQL Server 2022 efímero arranca correctamente.</li>
 *   <li>Verifica que Flyway ejecuta V1 y V2 desde cero contra SQL Server real.</li>
 *   <li>Verifica idempotencia: una segunda ejecución no reaplica las migraciones.</li>
 *   <li>Verifica escritura y lectura mediante JDBC.</li>
 *   <li>Verifica que una transacción revertida no deja filas persistentes.</li>
 * </ul>
 *
 * <p><b>Esta clase NO representa el modelo de dominio de TurnoShift.</b>
 * La tabla {@code poc_probe_entries} es ficticia y solo existe en el entorno de prueba.
 *
 * <p><b>Imagen:</b> {@code mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04}
 * (SQL Server 2022, edición Developer, solo para pruebas).
 * La licencia de SQL Server Developer prohíbe su uso en producción.
 * La aceptación se hace mediante {@link MSSQLServerContainer#acceptLicense()}.
 *
 * <p><b>Requisito de ejecución:</b> Docker debe estar disponible en el host.
 * Si no lo está, la prueba falla con {@code DockerNotAvailableException}
 * (no se omite ni se sustituye por H2).
 *
 * <p><b>Comando reproducible:</b>
 * {@code .\mvnw verify -Denv.JAVA_HOME="C:\Users\juan9\.jdks\temurin-21.0.10"}
 * (o equivalente según la ruta local del JDK 21).
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("PoC-01 — Compatibilidad Flyway + SQL Server 2022 + Testcontainers")
class FlywayMssqlCompatibilityIT {

    /**
     * Imagen concreta de SQL Server 2022.
     * Etiqueta fija; no se usa 'latest' ni '2022-latest'.
     *
     * Edición Developer: autorizada para desarrollo y pruebas (sin costo).
     * NO apta para producción.
     * Condiciones: https://www.microsoft.com/en-us/sql-server/sql-server-downloads
     */
    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate txTemplate;

    @BeforeAll
    static void configurarDataSource() {
        // El contenedor ya está en marcha gracias a @Container + @Testcontainers.
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setUrl(SQL_SERVER.getJdbcUrl());
        ds.setUsername(SQL_SERVER.getUsername());
        ds.setPassword(SQL_SERVER.getPassword());

        dataSource = ds;
        jdbc = new JdbcTemplate(dataSource);
        txTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    // =========================================================================
    // Test 1: Las dos migraciones se ejecutan desde una base vacía
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("V1 y V2 se ejecutan correctamente desde una base vacía")
    void migrationesDesdeBaseVacia() {
        Flyway flyway = construirFlyway();
        MigrateResult resultado = flyway.migrate();

        assertThat(resultado.success)
                .as("Flyway.migrate() debe completarse sin errores")
                .isTrue();
        assertThat(resultado.migrationsExecuted)
                .as("Deben ejecutarse exactamente 2 migraciones (V1 y V2)")
                .isEqualTo(2);

        // Verificar que ambas versiones están registradas en flyway_schema_history
        List<String> versionesAplicadas = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(v -> v == null ? null : v.getVersion())
                .toList();

        assertThat(versionesAplicadas)
                .as("Las versiones V1 y V2 deben estar registradas en el historial de Flyway")
                .containsExactly("1", "2");
    }

    // =========================================================================
    // Test 2: Una segunda ejecución no reaplica las migraciones (idempotencia)
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Segunda ejecución de migrate() no reaplica migraciones ya aplicadas")
    void segundaEjecucionIdempotente() {
        Flyway flyway = construirFlyway();
        MigrateResult resultado = flyway.migrate();

        assertThat(resultado.migrationsExecuted)
                .as("En la segunda ejecución no debe haber migraciones nuevas (idempotencia Flyway)")
                .isEqualTo(0);
    }

    // =========================================================================
    // Test 3: Escritura y lectura mediante JDBC
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Escritura y lectura JDBC funcionan correctamente sobre SQL Server 2022")
    void escrituraYLecturaJdbc() {
        // Inserción
        jdbc.update(
                "INSERT INTO poc_probe_entries (label, numeric_val) VALUES (?, ?)",
                "entrada-poc-lectura", 42
        );

        // Lectura
        Integer valorLeido = jdbc.queryForObject(
                "SELECT numeric_val FROM poc_probe_entries WHERE label = ?",
                Integer.class,
                "entrada-poc-lectura"
        );

        assertThat(valorLeido)
                .as("El valor almacenado debe recuperarse exactamente como fue insertado")
                .isEqualTo(42);
    }

    // =========================================================================
    // Test 4: Transacción revertida no deja filas persistentes
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("Una transacción revertida no persiste la fila insertada")
    void transaccionRevertidaNoDejaFila() {
        final String etiquetaRollback = "entrada-poc-rollback";

        // Ejecutar dentro de una transacción que se revierte
        txTemplate.execute(status -> {
            jdbc.update(
                    "INSERT INTO poc_probe_entries (label, numeric_val) VALUES (?, ?)",
                    etiquetaRollback, 99
            );
            // Verificar que la fila existe dentro de la transacción antes del rollback
            Integer countDentro = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM poc_probe_entries WHERE label = ?",
                    Integer.class,
                    etiquetaRollback
            );
            assertThat(countDentro).as("La fila debe existir dentro de la transacción").isEqualTo(1);

            status.setRollbackOnly(); // forzar rollback
            return null;
        });

        // Verificar que la fila NO existe fuera de la transacción revertida
        Integer countFuera = jdbc.queryForObject(
                "SELECT COUNT(*) FROM poc_probe_entries WHERE label = ?",
                Integer.class,
                etiquetaRollback
        );
        assertThat(countFuera)
                .as("Tras el rollback la fila no debe existir en la base de datos")
                .isEqualTo(0);
    }

    // =========================================================================
    // Test 5: La restricción CHECK de V2 rechaza valores negativos
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("El constraint CHECK de V2 rechaza numeric_val negativo")
    void constraintCheckRechazaValorNegativo() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO poc_probe_entries (label, numeric_val) VALUES (?, ?)",
                        "entrada-poc-negativa", -1
                )
        )
                .as("SQL Server debe rechazar un valor negativo por el CHECK constraint de V2")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // Utilidades
    // =========================================================================

    /**
     * Construye una instancia de Flyway apuntando exclusivamente a las migraciones
     * de prueba en {@code classpath:db/poc/migration/}.
     *
     * <p>Usa una tabla de historial propia ({@code poc_flyway_schema_history}) para
     * aislar completamente el estado de versiones PoC del historial de producción
     * ({@code flyway_schema_history}). Esto es necesario porque con
     * {@code ryuk.disabled=true} en ~/.testcontainers.properties, el contenedor
     * PoC puede seguir vivo cuando inicia el test de Spring Boot, y Testcontainers
     * puede reutilizarlo. La tabla de historial separada evita que Flyway de producción
     * confunda las versiones PoC (V1 = poc_probe_entries) con las de producción
     * (V1 = organizations).
     *
     * <p>Separación explícita de las migraciones de prueba frente a las de producción
     * (que residen en {@code classpath:db/migration/}).
     * Hibernate {@code ddl-auto} no está configurado; Flyway es el único mecanismo
     * que crea el esquema.
     */
    private static Flyway construirFlyway() {
        return Flyway.configure()
                .dataSource(
                        SQL_SERVER.getJdbcUrl(),
                        SQL_SERVER.getUsername(),
                        SQL_SERVER.getPassword()
                )
                .locations("classpath:db/poc/migration")
                // Tabla de historial propia: aísla versiones PoC de las de producción.
                .table("poc_flyway_schema_history")
                .load();
    }
}
