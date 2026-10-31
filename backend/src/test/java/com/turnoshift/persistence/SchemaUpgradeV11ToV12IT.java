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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de actualización: verifica que la migración V12 (positions) puede
 * aplicarse sobre una base que ya tiene datos válidos de I1 (V1–V11), sin perder filas,
 * identificadores ni relaciones preexistentes, dejando positions disponible.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT: Actualización progresiva V11→V12 — datos de I1 se conservan y positions queda operativa")
class SchemaUpgradeV11ToV12IT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    static JdbcTemplate jdbc;

    static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    static final String SITE_ID = "22222222-2222-2222-2222-222222222222";
    static final String USER_ID = "33333333-3333-3333-3333-333333333333";
    static final String TOKEN_ID = "44444444-4444-4444-4444-444444444444";
    static final String OPERATOR_ID = "55555555-5555-5555-5555-555555555555";
    static final String OPERATION_ID = "66666666-6666-6666-6666-666666666666";

    @BeforeAll
    static void prepararYMigrar() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setUrl(SQL_SERVER.getJdbcUrl());
        ds.setUsername(SQL_SERVER.getUsername());
        ds.setPassword(SQL_SERVER.getPassword());

        // Paso 1: aplicar migraciones de I1 (V1 hasta V11)
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("11")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load()
                .migrate();

        jdbc = new JdbcTemplate(ds);

        // Paso 2: insertar datos representativos de I1
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, 'ORG-UPG', 'Empresa Upgrade I1', 'ACTIVA', 'America/Bogota', 10, 1)
                """, ORG_ID);

        jdbc.update("""
                INSERT INTO sites (organization_id, id, code, name, is_active)
                VALUES (?, ?, 'HQ', 'Sede Principal Upgrade', 1)
                """, ORG_ID, SITE_ID);

        jdbc.update("""
                INSERT INTO app_users (organization_id, id, email, normalized_email, display_name,
                    password_hash, role_code)
                VALUES (?, ?, 'admin@upg.com', 'admin@upg.com', 'Admin Upgrade', 'argon2-hash-ficticio', 'ADMINISTRADOR')
                """, ORG_ID, USER_ID);

        byte[] tokenHash = new byte[32];
        tokenHash[0] = 42;
        jdbc.update("""
                INSERT INTO password_reset_tokens (organization_id, id, user_id, token_hash, purpose, expires_at)
                VALUES (?, ?, ?, ?, 'INITIAL_SETUP', DATEADD(day, 1, SYSUTCDATETIME()))
                """, ORG_ID, TOKEN_ID, USER_ID, tokenHash);

        jdbc.update("""
                INSERT INTO platform_operators (id, email, normalized_email, display_name, password_hash, is_active)
                VALUES (?, 'op@turnoshift.local', 'op@turnoshift.local', 'Operador Upgrade', 'argon2-hash-ficticio', 1)
                """, OPERATOR_ID);

        byte[] fingerprint = new byte[32];
        fingerprint[0] = 99;
        UUID idempotencyKey = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(24);
        jdbc.update("""
                INSERT INTO api_operations (
                    id, actor_operator_id, idempotency_key, request_method, request_path,
                    request_fingerprint, status, outcome, http_status, expires_at, completed_at
                )
                VALUES (?, ?, ?, 'POST', '/api/v1/operator/test', ?, 'COMPLETED', 'SUCCEEDED', 201, ?, SYSUTCDATETIME())
                """, OPERATION_ID, OPERATOR_ID, idempotencyKey, fingerprint, expiresAt);

        // Paso 3: aplicar V12 (target = 12)
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("12")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    // =========================================================================
    // Test 1: historial contiene V1 a V12 en orden
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Historial contiene V1 a V12 tras upgrade incremental V11→V12")
    void historialContieneV1aV12() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(versiones)
                .as("La actualización progresiva debe registrar V1 a V12")
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
    }

    // =========================================================================
    // Test 2: datos preexistentes de I1 se conservan intactos
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Datos de I1 en organizaciones, sedes, usuarios, tokens, operadores y operaciones se conservan")
    void datosDeI1Conservados() {
        // organizations
        String orgCode = jdbc.queryForObject(
                "SELECT code FROM organizations WHERE id = ?", String.class, ORG_ID);
        assertThat(orgCode).isEqualTo("ORG-UPG");

        // sites
        String siteCode = jdbc.queryForObject(
                "SELECT code FROM sites WHERE id = ?", String.class, SITE_ID);
        assertThat(siteCode).isEqualTo("HQ");

        // app_users
        String userEmail = jdbc.queryForObject(
                "SELECT email FROM app_users WHERE id = ?", String.class, USER_ID);
        assertThat(userEmail).isEqualTo("admin@upg.com");

        // password_reset_tokens
        String purpose = jdbc.queryForObject(
                "SELECT purpose FROM password_reset_tokens WHERE id = ?", String.class, TOKEN_ID);
        assertThat(purpose).isEqualTo("INITIAL_SETUP");

        // platform_operators
        String opEmail = jdbc.queryForObject(
                "SELECT email FROM platform_operators WHERE id = ?", String.class, OPERATOR_ID);
        assertThat(opEmail).isEqualTo("op@turnoshift.local");

        // api_operations
        String reqPath = jdbc.queryForObject(
                "SELECT request_path FROM api_operations WHERE id = ?", String.class, OPERATION_ID);
        assertThat(reqPath).isEqualTo("/api/v1/operator/test");
    }

    // =========================================================================
    // Test 3: positions queda operativa sobre organizaciones existentes
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("positions queda disponible e interactúa con organizaciones preexistentes")
    void positionsDisponibleSobreDatosExistentes() {
        String posId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO positions (id, organization_id, code, name)
                VALUES (?, ?, 'MED-ESP', 'Médico Especialista')
                """, posId, ORG_ID);

        Map<String, Object> puesto = jdbc.queryForMap(
                "SELECT code, name, is_active, lock_version FROM positions WHERE id = ?", posId);

        assertThat(puesto.get("code")).isEqualTo("MED-ESP");
        assertThat(puesto.get("name")).isEqualTo("Médico Especialista");
        assertThat(puesto.get("is_active")).isEqualTo(true);
        assertThat(((Number) puesto.get("lock_version")).longValue()).isEqualTo(0L);
    }
}
