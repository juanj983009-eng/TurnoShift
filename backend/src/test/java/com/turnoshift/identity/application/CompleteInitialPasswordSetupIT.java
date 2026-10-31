package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.AppUserPasswordRepository;
import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.identity.domain.InitialSetupTokenEmission;
import com.turnoshift.identity.domain.PasswordBlocklist;
import com.turnoshift.identity.domain.PasswordBlocklistUnavailableException;
import com.turnoshift.identity.domain.PasswordSetupDataAccessException;
import com.turnoshift.identity.domain.PasswordSetupResult;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.SessionSecurityConstants;
import com.turnoshift.identity.domain.ViolacionPolitica;
import com.turnoshift.identity.infrastructure.persistence.JdbcAppUserPasswordRepository;
import com.turnoshift.identity.infrastructure.security.Argon2PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: CompleteInitialPasswordSetup — establecimiento transaccional en SQL Server real")
class CompleteInitialPasswordSetupIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense()
                    .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @TestConfiguration
    static class TestBlocklistConfig {
        @Bean
        @Primary
        PasswordBlocklist testPasswordBlocklist() {
            return Mockito.mock(PasswordBlocklist.class);
        }
    }

    @Autowired
    CompleteInitialPasswordSetup completeInitialPasswordSetup;

    @Autowired
    IssueInitialSetupToken issueInitialSetupToken;

    @Autowired
    RevalidateSession revalidateSession;

    @Autowired
    Argon2PasswordService passwordService;

    @Autowired
    PasswordBlocklist mockBlocklist;

    @Autowired
    JdbcTemplate jdbc;

    private UUID insertarOrganizacion(String code, String displayName, String status, String timezone) {
        UUID orgId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, ?, ?, 10, 1)
                """, orgId.toString(), code, displayName, status, timezone);
        return orgId;
    }

    private UUID insertarUsuario(UUID orgId, String email, String displayName, String roleCode,
                                 boolean isActive, String passwordHash) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, userId.toString(), orgId.toString(), email, email.toLowerCase(), displayName,
                passwordHash, roleCode, isActive ? 1 : 0);
        return userId;
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(mockBlocklist);
        when(mockBlocklist.estaBloquada(any())).thenReturn(false);
    }

    @Test
    @DisplayName("Flujo exitoso: token INITIAL_SETUP válido + contraseña válida persiste hash, incrementa auth_version y consume token")
    void flujoExitoso_tokenValido_persisteHashArgon2_incrementaAuthVersion_yConsumeToken() {

        UUID orgId = insertarOrganizacion("CORP-SETUP-OK", "Setup Corp", "ACTIVA", "America/Bogota");
        UUID userId = insertarUsuario(orgId, "setup.ok@test.com", "Admin Setup", "ADMINISTRADOR", true, null);

        // Emitir dos tokens consecutivos: el segundo revoca al primero
        Optional<InitialSetupTokenEmission> emision1 = issueInitialSetupToken.issue(orgId, userId);
        Optional<InitialSetupTokenEmission> emision2 = issueInitialSetupToken.issue(orgId, userId);

        assertThat(emision1).isPresent();
        assertThat(emision2).isPresent();

        String rawTokenValido = emision2.get().rawToken();
        String nuevaContrasena = "ClaveSuperSegura2026#!";

        // Ejecutar establecimiento de contraseña
        PasswordSetupResult result = completeInitialPasswordSetup.complete(rawTokenValido, nuevaContrasena);

        assertThat(result).isInstanceOf(PasswordSetupResult.Success.class);

        // 1. Verificar app_users
        List<String> hashes = jdbc.query(
                "SELECT password_hash FROM app_users WHERE id = ?",
                (rs, rowNum) -> rs.getString("password_hash"),
                userId.toString()
        );
        assertThat(hashes).hasSize(1);
        String storedHash = hashes.get(0);
        assertThat(storedHash).isNotNull().startsWith("$argon2id$");
        assertThat(passwordService.verificar(nuevaContrasena, storedHash)).isTrue();

        Long authVersion = jdbc.queryForObject(
                "SELECT auth_version FROM app_users WHERE id = ?",
                Long.class,
                userId.toString()
        );
        assertThat(authVersion).isEqualTo(1L);

        OffsetDateTime passwordChangedAt = jdbc.queryForObject(
                "SELECT password_changed_at FROM app_users WHERE id = ?",
                OffsetDateTime.class,
                userId.toString()
        );
        assertThat(passwordChangedAt).isNotNull();

        // 2. Verificar password_reset_tokens
        OffsetDateTime consumedAt = jdbc.queryForObject(
                "SELECT consumed_at FROM password_reset_tokens WHERE id = ?",
                OffsetDateTime.class,
                emision2.get().tokenId().toString()
        );
        assertThat(consumedAt).isNotNull();

        // El token anterior emitido debe estar revocado
        OffsetDateTime revokedAt1 = jdbc.queryForObject(
                "SELECT revoked_at FROM password_reset_tokens WHERE id = ?",
                OffsetDateTime.class,
                emision1.get().tokenId().toString()
        );
        assertThat(revokedAt1).isNotNull();
    }

    @Test
    @DisplayName("Segundo intento con el mismo token devuelve InvalidToken y no altera hash ni auth_version")
    void segundoConsumoMismoToken_devuelveInvalidToken_yNoAlteraDatos() {
        when(mockBlocklist.estaBloquada(any())).thenReturn(false);

        UUID orgId = insertarOrganizacion("CORP-REPLAY", "Replay Corp", "ACTIVA", "UTC");
        UUID userId = insertarUsuario(orgId, "replay@test.com", "Admin Replay", "ADMINISTRADOR", true, null);

        Optional<InitialSetupTokenEmission> emision = issueInitialSetupToken.issue(orgId, userId);
        assertThat(emision).isPresent();
        String rawToken = emision.get().rawToken();

        // Primer intento exitoso
        PasswordSetupResult primerIntento = completeInitialPasswordSetup.complete(rawToken, "ClaveSegura12345678#");
        assertThat(primerIntento).isInstanceOf(PasswordSetupResult.Success.class);

        // Segundo intento con el mismo token
        PasswordSetupResult segundoIntento = completeInitialPasswordSetup.complete(rawToken, "OtraClaveSegura87654321#");
        assertThat(segundoIntento).isInstanceOf(PasswordSetupResult.InvalidToken.class);

        // auth_version sigue en 1 y no se incrementó dos veces
        Long authVersion = jdbc.queryForObject(
                "SELECT auth_version FROM app_users WHERE id = ?",
                Long.class,
                userId.toString()
        );
        assertThat(authVersion).isEqualTo(1L);
    }

    @Test
    @DisplayName("Token expirado devuelve InvalidToken y no modifica app_users")
    void tokenExpirado_devuelveInvalidToken_sinModificarAppUsers() {
        when(mockBlocklist.estaBloquada(any())).thenReturn(false);

        UUID orgId = insertarOrganizacion("CORP-EXPIRED-SETUP", "Expired Corp", "ACTIVA", "UTC");
        UUID userId = insertarUsuario(orgId, "exp@test.com", "Admin Exp", "ADMINISTRADOR", true, null);

        Optional<InitialSetupTokenEmission> emision = issueInitialSetupToken.issue(orgId, userId);
        assertThat(emision).isPresent();

        // Vencer token en base de datos respetando chk_prt_expires_after_created
        jdbc.update("""
                UPDATE password_reset_tokens
                SET created_at = DATEADD(minute, -40, SYSUTCDATETIME()),
                    expires_at = DATEADD(minute, -10, SYSUTCDATETIME())
                WHERE id = ?
                """, emision.get().tokenId().toString());

        PasswordSetupResult result = completeInitialPasswordSetup.complete(emision.get().rawToken(), "ClaveSegura12345678#");

        assertThat(result).isInstanceOf(PasswordSetupResult.InvalidToken.class);

        String currentHash = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE id = ?",
                String.class,
                userId.toString()
        );
        assertThat(currentHash).isNull();
    }

    @Test
    @DisplayName("Contraseña que viola política no consume token ni altera base de datos")
    void contrasenaInvalida_noConsumeToken_niModificaBaseDeDatos() {
        when(mockBlocklist.estaBloquada(any())).thenReturn(false);

        UUID orgId = insertarOrganizacion("CORP-POL-FAIL", "Pol Fail Corp", "ACTIVA", "UTC");
        UUID userId = insertarUsuario(orgId, "pol@test.com", "Admin Pol", "ADMINISTRADOR", true, null);

        Optional<InitialSetupTokenEmission> emision = issueInitialSetupToken.issue(orgId, userId);
        assertThat(emision).isPresent();

        // Contraseña corta (<15)
        PasswordSetupResult result = completeInitialPasswordSetup.complete(emision.get().rawToken(), "corta");

        assertThat(result).isInstanceOf(PasswordSetupResult.PolicyViolation.class);
        PasswordSetupResult.PolicyViolation violation = (PasswordSetupResult.PolicyViolation) result;
        assertThat(violation.violations()).containsExactly(ViolacionPolitica.DEMASIADO_CORTA);

        // Token sigue intacto sin consumir
        OffsetDateTime consumedAt = jdbc.queryForObject(
                "SELECT consumed_at FROM password_reset_tokens WHERE id = ?",
                OffsetDateTime.class,
                emision.get().tokenId().toString()
        );
        assertThat(consumedAt).isNull();

        String currentHash = jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE id = ?",
                String.class,
                userId.toString()
        );
        assertThat(currentHash).isNull();
    }

    @Test
    @DisplayName("Contraseña bloqueada devuelve PolicyViolation y no consume token")
    void contrasenaBloqueada_devuelvePolicyViolation_sinConsumirToken() {
        when(mockBlocklist.estaBloquada(any())).thenReturn(true);

        UUID orgId = insertarOrganizacion("CORP-BLOCKED", "Blocked Corp", "ACTIVA", "UTC");
        UUID userId = insertarUsuario(orgId, "blocked@test.com", "Admin Blocked", "ADMINISTRADOR", true, null);

        Optional<InitialSetupTokenEmission> emision = issueInitialSetupToken.issue(orgId, userId);
        assertThat(emision).isPresent();

        PasswordSetupResult result = completeInitialPasswordSetup.complete(emision.get().rawToken(), "ClaveComprometida12345#");

        assertThat(result).isInstanceOf(PasswordSetupResult.PolicyViolation.class);
        PasswordSetupResult.PolicyViolation violation = (PasswordSetupResult.PolicyViolation) result;
        assertThat(violation.violations()).containsExactly(ViolacionPolitica.CONTRASENA_BLOQUEADA);

        OffsetDateTime consumedAt = jdbc.queryForObject(
                "SELECT consumed_at FROM password_reset_tokens WHERE id = ?",
                OffsetDateTime.class,
                emision.get().tokenId().toString()
        );
        assertThat(consumedAt).isNull();
    }

    @Test
    @DisplayName("Blocklist no disponible propaga PasswordBlocklistUnavailableException y no consume token")
    void blocklistUnavailable_propagaExcepcion_sinConsumirTokenNiModificarDB() {
        when(mockBlocklist.estaBloquada(any())).thenThrow(new PasswordBlocklistUnavailableException());

        UUID orgId = insertarOrganizacion("CORP-UNAVAIL", "Unavail Corp", "ACTIVA", "UTC");
        UUID userId = insertarUsuario(orgId, "unavail@test.com", "Admin Unavail", "ADMINISTRADOR", true, null);

        Optional<InitialSetupTokenEmission> emision = issueInitialSetupToken.issue(orgId, userId);
        assertThat(emision).isPresent();

        assertThatThrownBy(() -> completeInitialPasswordSetup.complete(emision.get().rawToken(), "ClaveSegura12345678#"))
                .isInstanceOf(PasswordBlocklistUnavailableException.class);

        OffsetDateTime consumedAt = jdbc.queryForObject(
                "SELECT consumed_at FROM password_reset_tokens WHERE id = ?",
                OffsetDateTime.class,
                emision.get().tokenId().toString()
        );
        assertThat(consumedAt).isNull();
    }

    @Test
    @DisplayName("Rollback transaccional: si el usuario es desactivado, falla genéricamente y consumed_at permanece NULL")
    void rollbackTransaccional_usuarioDesactivado_consumedAtPermaneceNull() {
        when(mockBlocklist.estaBloquada(any())).thenReturn(false);

        UUID orgId = insertarOrganizacion("CORP-ROLL-USER", "Roll User Corp", "ACTIVA", "UTC");
        UUID userId = insertarUsuario(orgId, "roll.user@test.com", "Admin Roll", "ADMINISTRADOR", true, null);

        Optional<InitialSetupTokenEmission> emision = issueInitialSetupToken.issue(orgId, userId);
        assertThat(emision).isPresent();

        // Desactivar usuario antes de completar
        jdbc.update("UPDATE app_users SET is_active = 0 WHERE id = ?", userId.toString());

        PasswordSetupResult result = completeInitialPasswordSetup.complete(emision.get().rawToken(), "ClaveSegura12345678#");

        assertThat(result).isInstanceOf(PasswordSetupResult.InvalidToken.class);

        OffsetDateTime consumedAt = jdbc.queryForObject(
                "SELECT consumed_at FROM password_reset_tokens WHERE id = ?",
                OffsetDateTime.class,
                emision.get().tokenId().toString()
        );
        assertThat(consumedAt).as("Rollback atómico: consumed_at debe continuar siendo NULL").isNull();
    }

    @Test
    @DisplayName("Rollback transaccional: si la organización es suspendida, falla genéricamente y consumed_at permanece NULL")
    void rollbackTransaccional_organizacionSuspendida_consumedAtPermaneceNull() {
        when(mockBlocklist.estaBloquada(any())).thenReturn(false);

        UUID orgId = insertarOrganizacion("CORP-ROLL-ORG", "Roll Org Corp", "ACTIVA", "UTC");
        UUID userId = insertarUsuario(orgId, "roll.org@test.com", "Admin Roll Org", "ADMINISTRADOR", true, null);

        Optional<InitialSetupTokenEmission> emision = issueInitialSetupToken.issue(orgId, userId);
        assertThat(emision).isPresent();

        // Suspender organización antes de completar
        jdbc.update("UPDATE organizations SET service_status = 'SUSPENDIDA' WHERE id = ?", orgId.toString());

        PasswordSetupResult result = completeInitialPasswordSetup.complete(emision.get().rawToken(), "ClaveSegura12345678#");

        assertThat(result).isInstanceOf(PasswordSetupResult.InvalidToken.class);

        OffsetDateTime consumedAt = jdbc.queryForObject(
                "SELECT consumed_at FROM password_reset_tokens WHERE id = ?",
                OffsetDateTime.class,
                emision.get().tokenId().toString()
        );
        assertThat(consumedAt).as("Rollback atómico: consumed_at debe continuar siendo NULL").isNull();
    }

    @Test
    @DisplayName("Rollback transaccional: si password_hash ya existe, falla genéricamente y consumed_at permanece NULL")
    void rollbackTransaccional_passwordHashYaExistente_consumedAtPermaneceNull() {
        when(mockBlocklist.estaBloquada(any())).thenReturn(false);

        UUID orgId = insertarOrganizacion("CORP-ROLL-PASS", "Roll Pass Corp", "ACTIVA", "UTC");
        UUID userId = insertarUsuario(orgId, "roll.pass@test.com", "Admin Roll Pass", "ADMINISTRADOR", true, null);

        Optional<InitialSetupTokenEmission> emision = issueInitialSetupToken.issue(orgId, userId);
        assertThat(emision).isPresent();

        // Configurar contraseña previamente
        jdbc.update("UPDATE app_users SET password_hash = '$argon2id$v=19$yaConfigurada' WHERE id = ?", userId.toString());

        PasswordSetupResult result = completeInitialPasswordSetup.complete(emision.get().rawToken(), "ClaveSegura12345678#");

        assertThat(result).isInstanceOf(PasswordSetupResult.InvalidToken.class);

        OffsetDateTime consumedAt = jdbc.queryForObject(
                "SELECT consumed_at FROM password_reset_tokens WHERE id = ?",
                OffsetDateTime.class,
                emision.get().tokenId().toString()
        );
        assertThat(consumedAt).as("Rollback atómico: consumed_at debe continuar siendo NULL").isNull();
    }

    @Test
    @DisplayName("El incremento de auth_version invalida sesiones previas en RevalidateSession")
    void revalidarSesion_sesionPreviaInvalidaPorIncrementoAuthVersion() {
        when(mockBlocklist.estaBloquada(any())).thenReturn(false);

        UUID orgId = insertarOrganizacion("CORP-SESS-INV", "Sess Inv Corp", "ACTIVA", "UTC");
        UUID userId = insertarUsuario(orgId, "sess.inv@test.com", "Admin Sess Inv", "ADMINISTRADOR", true, null);

        // Simular identidad de sesión previa antes del cambio (authVersion = 0)
        AuthenticatedIdentity identityPrevia = new AuthenticatedIdentity(
                userId,
                orgId,
                "sess.inv@test.com",
                "Admin Sess Inv",
                "ADMINISTRADOR",
                null,
                0L,
                "Sess Inv Corp",
                "UTC"
        );

        Long authAt = System.currentTimeMillis();

        // Antes de establecer contraseña, la sesión con authVersion=0 es válida
        RevalidationOutcome outcomePrevio = revalidateSession.execute(identityPrevia, authAt);
        assertThat(outcomePrevio.status()).isEqualTo(RevalidationOutcome.Status.VALID);

        // Establecer contraseña (incrementa auth_version a 1 en app_users)
        Optional<InitialSetupTokenEmission> emision = issueInitialSetupToken.issue(orgId, userId);
        assertThat(emision).isPresent();
        PasswordSetupResult result = completeInitialPasswordSetup.complete(emision.get().rawToken(), "ClaveSegura12345678#");
        assertThat(result).isInstanceOf(PasswordSetupResult.Success.class);

        // La revalidación de la sesión previa con authVersion=0 ahora debe fallar como INVALID
        RevalidationOutcome outcomePosterior = revalidateSession.execute(identityPrevia, authAt);
        assertThat(outcomePosterior.status()).isEqualTo(RevalidationOutcome.Status.INVALID);
    }

    @Test
    @DisplayName("Fallo de conexión SQL Server propaga PasswordSetupDataAccessException")
    void falloConexion_propagaPasswordSetupDataAccessException() {
        DriverManagerDataSource badDataSource = new DriverManagerDataSource();
        badDataSource.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        badDataSource.setUrl("jdbc:sqlserver://127.0.0.1:54321;databaseName=turnoshift_dev;loginTimeout=1");
        badDataSource.setUsername("sa");
        badDataSource.setPassword("wrong-password");

        JdbcAppUserPasswordRepository badRepo =
                new JdbcAppUserPasswordRepository(new JdbcTemplate(badDataSource));

        assertThatThrownBy(() -> badRepo.setInitialPassword(
                UUID.randomUUID(), UUID.randomUUID(), "$argon2id$dummy", Instant.now()
        ))
                .isInstanceOf(PasswordSetupDataAccessException.class)
                .hasMessageContaining("[IDENTITY_PASSWORD_SETUP_DATA_ACCESS_ERROR]");
    }
}
