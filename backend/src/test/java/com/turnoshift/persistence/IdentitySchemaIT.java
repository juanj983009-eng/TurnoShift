package com.turnoshift.persistence;

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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de integración: verifica que las migraciones V4 (roles), V5 (app_users),
 * V6 (password_reset_tokens), V7 (employee_id guard) y V8 (normalized_email collation)
 * se aplican correctamente y que las restricciones de integridad empresarial funcionan.
 *
 * <p><b>Verificaciones de restricción negativa:</b> cada prueba negativa comprueba
 * tanto el tipo de excepción ({@link DataIntegrityViolationException}) como que el
 * mensaje de error SQL contiene el nombre de la restricción esperada, de modo que
 * un fallo por otra causa sea distinguible y no enmascare el error real.
 *
 * <p><b>Patrón:</b> {@code @SpringBootTest} con {@code @Transactional} + {@code @Rollback}:
 * cada test revierte sus datos; los roles del catálogo (insertados por V4) persisten.
 *
 * <p><b>Datos:</b> exclusivamente ficticios. Ningún correo, hash ni credencial real.
 */
@SpringBootTest
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: Schema identidad empresarial (V4/V5/V6/V7/V8)")
class IdentitySchemaIT {

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

    // =========================================================================
    // Helpers
    // =========================================================================

    private String insertarOrganizacion(String code, String displayName) {
        String orgId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, 'ACTIVA', 'UTC', 10, 1)
                """, orgId, code, displayName);
        return orgId;
    }

    private String insertarUsuario(String orgId, String email, String normalizedEmail,
                                   String roleCode, String passwordHash) {
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO app_users
                    (organization_id, id, email, normalized_email, display_name, password_hash, role_code)
                VALUES (?, ?, ?, ?, 'Usuario Ficticio', ?, ?)
                """, orgId, userId, email, normalizedEmail, passwordHash, roleCode);
        return userId;
    }

    // =========================================================================
    // Test 1 — mismo correo normalizado permitido en empresas distintas
    // =========================================================================

    @Test
    @DisplayName("Mismo correo normalizado permitido en organizaciones diferentes")
    void mismoCorreoNormalizadoPermitidoEnEmpresasDiferentes() {
        String orgA = insertarOrganizacion("ORG-A", "Organizacion A Ficticia");
        insertarUsuario(orgA, "test@ejemplo.com", "test@ejemplo.com", "ADMINISTRADOR", "hash-ficticio");

        String orgB = insertarOrganizacion("ORG-B", "Organizacion B Ficticia");
        insertarUsuario(orgB, "test@ejemplo.com", "test@ejemplo.com", "ADMINISTRADOR", "hash-ficticio");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE normalized_email = 'test@ejemplo.com'",
                Integer.class);
        assertThat(count)
                .as("El mismo correo normalizado debe poder existir en dos organizaciones distintas")
                .isEqualTo(2);
    }

    // =========================================================================
    // Test 2 — correo normalizado duplicado dentro de la misma empresa rechazado
    // =========================================================================

    @Test
    @DisplayName("Correo normalizado duplicado dentro de la misma organización rechazado")
    void duplicadoRechazadoDentroDeEmpresa() {
        String orgA = insertarOrganizacion("ORG-DUP", "Organizacion Duplicado Ficticia");
        insertarUsuario(orgA, "dup@ejemplo.com", "dup@ejemplo.com", "ADMINISTRADOR", "hash-ficticio");

        assertThatThrownBy(() ->
                insertarUsuario(orgA, "DUP@ejemplo.com", "dup@ejemplo.com", "SUPERVISOR", "hash-ficticio")
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_app_users_org_normalized_email");
    }

    // =========================================================================
    // Test 3 — usuario con organización inexistente rechazado
    // =========================================================================

    @Test
    @DisplayName("Usuario con organización inexistente rechazado")
    void usuarioConOrganizacionInexistenteRechazado() {
        String orgInexistente = UUID.randomUUID().toString();
        assertThatThrownBy(() ->
                insertarUsuario(orgInexistente, "test@ej.com", "test@ej.com", "COLABORADOR", "hash-ficticio")
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_app_users_organization");
    }

    // =========================================================================
    // Test 4 — rol inexistente rechazado
    // =========================================================================

    @Test
    @DisplayName("Rol inexistente rechazado")
    void rolInexistenteRechazado() {
        String orgA = insertarOrganizacion("ORG-ROL", "Org Rol Ficticia");
        assertThatThrownBy(() ->
                insertarUsuario(orgA, "test@ej.com", "test@ej.com", "ROL-INEXISTENTE", "hash-ficticio")
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_app_users_role");
    }

    // =========================================================================
    // Test 5 — cuenta pendiente admite password_hash nulo
    // =========================================================================

    @Test
    @DisplayName("Cuenta pendiente de configuración inicial admite password_hash nulo")
    void cuentaPendienteAdmitePasswordHashNulo() {
        String orgA = insertarOrganizacion("ORG-NULO", "Org Nulo Ficticia");
        String userId = insertarUsuario(orgA, "nulo@ej.com", "nulo@ej.com", "COLABORADOR", null);

        String hashLeido = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE id = ?", String.class, userId);
        assertThat(hashLeido)
                .as("Una cuenta pendiente de configuración inicial debe poder tener password_hash NULL")
                .isNull();
    }

    // =========================================================================
    // Test 6 — token de empresa A no puede referenciar usuario de empresa B
    // =========================================================================

    @Test
    @DisplayName("Token de organización A no puede referenciar usuario de organización B")
    void tokenDeEmpresaANoReferenciaUsuarioDeEmpresaB() {
        String orgA = insertarOrganizacion("ORG-TA", "Org A Ficticia");
        insertarUsuario(orgA, "a@ej.com", "a@ej.com", "COLABORADOR", "hash-ficticio");

        String orgB = insertarOrganizacion("ORG-TB", "Org B Ficticia");
        String userB = insertarUsuario(orgB, "b@ej.com", "b@ej.com", "COLABORADOR", "hash-ficticio");

        // Intento: token de orgA apunta a userB (que pertenece a orgB)
        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO password_reset_tokens
                            (organization_id, id, user_id, token_hash, purpose, expires_at)
                        VALUES (?, NEWID(), ?, ?, 'INITIAL_SETUP', DATEADD(day, 1, SYSUTCDATETIME()))
                        """, orgA, userB, new byte[32])
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_prt_app_user");
    }

    // =========================================================================
    // Test 7 — token válido para usuario de su misma empresa permitido
    // =========================================================================

    @Test
    @DisplayName("Token válido para usuario de su misma organización es permitido")
    void tokenValidoParaUsuarioDeSuMismaEmpresaPermitido() {
        String orgA = insertarOrganizacion("ORG-TV", "Org V Ficticia");
        String userA = insertarUsuario(orgA, "v@ej.com", "v@ej.com", "COLABORADOR", "hash-ficticio");

        byte[] hash = new byte[32];
        hash[15] = 7; // distinguible de otros tests

        jdbc.update("""
                INSERT INTO password_reset_tokens
                    (organization_id, id, user_id, token_hash, purpose, expires_at)
                VALUES (?, NEWID(), ?, ?, 'INITIAL_SETUP', DATEADD(day, 1, SYSUTCDATETIME()))
                """, orgA, userA, hash);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?",
                Integer.class, userA);
        assertThat(count)
                .as("Un token con organization_id y user_id de la misma empresa debe insertarse sin error")
                .isEqualTo(1);
    }

    // =========================================================================
    // Test 8 — hash de token duplicado rechazado
    // =========================================================================

    @Test
    @DisplayName("Hash de token duplicado rechazado")
    void hashDeTokenDuplicadoRechazado() {
        String orgA = insertarOrganizacion("ORG-TH", "Org H Ficticia");
        String userA = insertarUsuario(orgA, "h@ej.com", "h@ej.com", "COLABORADOR", "hash-ficticio");

        byte[] hash = new byte[32];
        hash[0] = 8; // distinguible

        jdbc.update("""
                INSERT INTO password_reset_tokens
                    (organization_id, id, user_id, token_hash, purpose, expires_at)
                VALUES (?, NEWID(), ?, ?, 'INITIAL_SETUP', DATEADD(day, 1, SYSUTCDATETIME()))
                """, orgA, userA, hash);

        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO password_reset_tokens
                            (organization_id, id, user_id, token_hash, purpose, expires_at)
                        VALUES (?, NEWID(), ?, ?, 'PASSWORD_RESET', DATEADD(day, 1, SYSUTCDATETIME()))
                        """, orgA, userA, hash)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_password_reset_tokens_token_hash");
    }

    // =========================================================================
    // Test 9 — propósito inválido rechazado (prueba independiente)
    // =========================================================================

    @Test
    @DisplayName("Propósito de token inválido rechazado")
    void propositoInvalidoRechazado() {
        String orgA = insertarOrganizacion("ORG-PRP", "Org Prop Ficticia");
        String userA = insertarUsuario(orgA, "prp@ej.com", "prp@ej.com", "COLABORADOR", "hash-ficticio");
        byte[] hash = new byte[32];
        hash[0] = 9;

        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO password_reset_tokens
                            (organization_id, id, user_id, token_hash, purpose, expires_at)
                        VALUES (?, NEWID(), ?, ?, 'INVALID_PURPOSE', DATEADD(day, 1, SYSUTCDATETIME()))
                        """, orgA, userA, hash)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_prt_purpose");
    }

    // =========================================================================
    // Test 10 — vencimiento no posterior a creación rechazado (prueba independiente)
    // =========================================================================

    @Test
    @DisplayName("Vencimiento de token no posterior a creación rechazado")
    void vencimientoNoPosteriorACreacionRechazado() {
        String orgA = insertarOrganizacion("ORG-VNC", "Org Venc Ficticia");
        String userA = insertarUsuario(orgA, "vnc@ej.com", "vnc@ej.com", "COLABORADOR", "hash-ficticio");
        byte[] hash = new byte[32];
        hash[0] = 10;

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO password_reset_tokens
                            (organization_id, id, user_id, token_hash, purpose, created_at, expires_at)
                        VALUES (?, NEWID(), ?, ?, 'INITIAL_SETUP', ?, ?)
                        """, orgA, userA, hash, now, now)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_prt_expires_after_created");
    }

    // =========================================================================
    // Test 11 — employee_id no nulo rechazado (V7: guard mientras no existe employees)
    // =========================================================================

    @Test
    @DisplayName("employee_id no nulo rechazado mientras employees no existe (V7 guard)")
    void employeeIdNoNuloRechazado() {
        String orgA = insertarOrganizacion("ORG-EMP", "Org Emp Ficticia");
        String empleadoFicticio = UUID.randomUUID().toString();

        assertThatThrownBy(() ->
                jdbc.update("""
                        INSERT INTO app_users
                            (organization_id, id, email, normalized_email, display_name,
                             password_hash, role_code, employee_id)
                        VALUES (?, NEWID(), 'emp@ej.com', 'emp@ej.com', 'Empleado Ficticio',
                                'hash-ficticio', 'COLABORADOR', ?)
                        """, orgA, empleadoFicticio)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_app_users_employee_id_null_guard");
    }

    // =========================================================================
    // Test 12 — collation efectiva de normalized_email es BIN2 (V8)
    // =========================================================================

    @Test
    @DisplayName("normalized_email usa collation Latin1_General_100_BIN2 tras V8")
    void normalizedEmailUsaCollationBin2() {
        // Consultar la collation registrada en sys.columns para la columna
        String collation = jdbc.queryForObject(
                """
                SELECT collation_name
                FROM sys.columns
                WHERE object_id = OBJECT_ID('app_users')
                  AND name = 'normalized_email'
                """,
                String.class);

        assertThat(collation)
                .as("normalized_email debe usar Latin1_General_100_BIN2 para comparación binaria exacta del valor canónico")
                .isEqualTo("Latin1_General_100_BIN2");
    }
}
