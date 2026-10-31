package com.turnoshift.identity.infrastructure.persistence;

import com.turnoshift.identity.domain.InitialSetupTokenEmission;
import com.turnoshift.identity.domain.PasswordResetTokenRepository;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.SecureTokenGenerator;
import com.turnoshift.identity.domain.TokenDataAccessException;
import com.turnoshift.identity.domain.TokenInspectionCandidate;
import com.turnoshift.identity.domain.UserSetupEligibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: JdbcPasswordResetTokenRepository — ciclo de vida en SQL Server real")
class JdbcPasswordResetTokenRepositoryIT {

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

    @Autowired
    JdbcPasswordResetTokenRepository repository;

    @Autowired
    com.turnoshift.identity.application.IssueInitialSetupToken issueInitialSetupToken;

    @Autowired
    JdbcTemplate jdbc;

    private final SecureTokenGenerator tokenGenerator = new SecureTokenGenerator();

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

    // =========================================================================
    // 1. Aptitud para emisión (findEligibility)
    // =========================================================================

    @Test
    @DisplayName("findEligibility: usuario activo en organización ACTIVA sin contraseña es apto")
    void findEligibility_usuarioApto_devuelveAptoTrue() {
        UUID orgId = insertarOrganizacion("CORP-APTO", "Corp Apto S.A.", "ACTIVA", "America/Bogota");
        UUID userId = insertarUsuario(orgId, "apto@test.com", "Usuario Apto", "ADMINISTRADOR", true, null);

        Optional<UserSetupEligibility> opt = repository.findEligibility(orgId, userId);

        assertThat(opt).isPresent();
        UserSetupEligibility eligibility = opt.get();
        assertThat(eligibility.organizationId()).isEqualTo(orgId);
        assertThat(eligibility.userId()).isEqualTo(userId);
        assertThat(eligibility.isUserActive()).isTrue();
        assertThat(eligibility.hasPasswordSet()).isFalse();
        assertThat(eligibility.orgServiceStatus()).isEqualTo("ACTIVA");
        assertThat(eligibility.isEligibleForInitialSetup()).isTrue();
    }

    @Test
    @DisplayName("findEligibility: usuario con contraseña existente no es apto")
    void findEligibility_usuarioConPassword_devuelveAptoFalse() {
        UUID orgId = insertarOrganizacion("CORP-CON-PASS", "Corp Con Pass", "ACTIVA", "America/Bogota");
        UUID userId = insertarUsuario(orgId, "conpass@test.com", "Con Pass", "COLABORADOR", true, "$argon2id$v=19$dummy");

        Optional<UserSetupEligibility> opt = repository.findEligibility(orgId, userId);

        assertThat(opt).isPresent();
        assertThat(opt.get().hasPasswordSet()).isTrue();
        assertThat(opt.get().isEligibleForInitialSetup()).isFalse();
    }

    @Test
    @DisplayName("findEligibility: usuario inactivo no es apto")
    void findEligibility_usuarioInactivo_devuelveAptoFalse() {
        UUID orgId = insertarOrganizacion("CORP-USER-INACT", "Corp Inact", "ACTIVA", "America/Bogota");
        UUID userId = insertarUsuario(orgId, "inactivo@test.com", "Inactivo", "COLABORADOR", false, null);

        Optional<UserSetupEligibility> opt = repository.findEligibility(orgId, userId);

        assertThat(opt).isPresent();
        assertThat(opt.get().isUserActive()).isFalse();
        assertThat(opt.get().isEligibleForInitialSetup()).isFalse();
    }

    @Test
    @DisplayName("findEligibility: organización suspendida no es apta")
    void findEligibility_organizacionSuspendida_devuelveAptoFalse() {
        UUID orgId = insertarOrganizacion("CORP-SUSP-ORG", "Corp Susp", "SUSPENDIDA", "America/Bogota");
        UUID userId = insertarUsuario(orgId, "susp@test.com", "Susp", "COLABORADOR", true, null);

        Optional<UserSetupEligibility> opt = repository.findEligibility(orgId, userId);

        assertThat(opt).isPresent();
        assertThat(opt.get().orgServiceStatus()).isEqualTo("SUSPENDIDA");
        assertThat(opt.get().isEligibleForInitialSetup()).isFalse();
    }

    @Test
    @DisplayName("findEligibility: aislamiento multi-tenant con organización ajena devuelve Optional.empty()")
    void findEligibility_aislamientoMultiTenant_devuelveEmpty() {
        UUID orgA = insertarOrganizacion("CORP-A", "Corp A", "ACTIVA", "America/Bogota");
        UUID orgB = insertarOrganizacion("CORP-B", "Corp B", "ACTIVA", "America/Bogota");
        UUID userA = insertarUsuario(orgA, "userA@corpA.com", "User A", "ADMINISTRADOR", true, null);

        Optional<UserSetupEligibility> opt = repository.findEligibility(orgB, userA);

        assertThat(opt).isEmpty();
    }

    // =========================================================================
    // 2. Inserción e inspección (insertToken y findCandidateForInspection)
    // =========================================================================

    @Test
    @DisplayName("insertToken y findCandidateForInspection: inserta hash de 32 bytes y recupera candidato completo")
    void insertTokenYFindCandidateForInspection_exitoso() {
        UUID orgId = insertarOrganizacion("CORP-INSPECT", "Inspect S.A.", "ACTIVA", "America/Lima");
        UUID userId = insertarUsuario(orgId, "inspect@test.com", "Inspector", "SUPERVISOR", true, null);

        UUID tokenId = UUID.randomUUID();
        String rawToken = tokenGenerator.generateRawToken();
        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        repository.insertToken(tokenId, orgId, userId, tokenHash, ResetTokenPurpose.INITIAL_SETUP, expiresAt, now);

        Optional<TokenInspectionCandidate> opt = repository.findCandidateForInspection(tokenHash);

        assertThat(opt).isPresent();
        TokenInspectionCandidate candidate = opt.get();
        assertThat(candidate.tokenId()).isEqualTo(tokenId);
        assertThat(candidate.organizationId()).isEqualTo(orgId);
        assertThat(candidate.userId()).isEqualTo(userId);
        assertThat(candidate.purpose()).isEqualTo(ResetTokenPurpose.INITIAL_SETUP);
        assertThat(candidate.consumedAt()).isNull();
        assertThat(candidate.revokedAt()).isNull();
        assertThat(candidate.isUserActive()).isTrue();
        assertThat(candidate.hasPasswordSet()).isFalse();
        assertThat(candidate.orgServiceStatus()).isEqualTo("ACTIVA");
    }

    // =========================================================================
    // 3. Revocación de tokens no consumidos
    // =========================================================================

    @Test
    @DisplayName("revokeUnconsumedTokens: revoca tokens vigentes e impide su consumo")
    void revokeUnconsumedTokens_marcaRevokedAt_eImpideConsumo() {
        UUID orgId = insertarOrganizacion("CORP-REVOKE", "Revoke S.A.", "ACTIVA", "America/Lima");
        UUID userId = insertarUsuario(orgId, "revoke@test.com", "Revocado", "SUPERVISOR", true, null);

        UUID tokenId = UUID.randomUUID();
        String rawToken = tokenGenerator.generateRawToken();
        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        repository.insertToken(tokenId, orgId, userId, tokenHash, ResetTokenPurpose.INITIAL_SETUP, expiresAt, now);

        // Revocar
        Instant revokeTime = now.plusSeconds(60);
        repository.revokeUnconsumedTokens(orgId, userId, ResetTokenPurpose.INITIAL_SETUP, revokeTime);

        // La inspección ahora muestra revoked_at poblado
        Optional<TokenInspectionCandidate> opt = repository.findCandidateForInspection(tokenHash);
        assertThat(opt).isPresent();
        assertThat(opt.get().revokedAt()).isNotNull();

        // El consumo posterior debe fallar
        Optional<PasswordResetTokenRepository.ConsumedTokenInfo> consumed =
                repository.consumeToken(tokenHash, ResetTokenPurpose.INITIAL_SETUP, revokeTime.plusSeconds(10));
        assertThat(consumed).isEmpty();
    }

    // =========================================================================
    // 4. Consumo atómico y condicional (consumeToken)
    // =========================================================================

    @Test
    @DisplayName("consumeToken: consumo exitoso devuelve datos y segundo intento con el mismo token falla")
    void consumeToken_exitoso_y_segundoConsumoFalla() {
        UUID orgId = insertarOrganizacion("CORP-CONSUME", "Consume S.A.", "ACTIVA", "America/Lima");
        UUID userId = insertarUsuario(orgId, "consume@test.com", "Consumidor", "COLABORADOR", true, null);

        UUID tokenId = UUID.randomUUID();
        String rawToken = tokenGenerator.generateRawToken();
        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        repository.insertToken(tokenId, orgId, userId, tokenHash, ResetTokenPurpose.INITIAL_SETUP, expiresAt, now);

        // Primer consumo exitoso
        Optional<PasswordResetTokenRepository.ConsumedTokenInfo> firstAttempt =
                repository.consumeToken(tokenHash, ResetTokenPurpose.INITIAL_SETUP, now.plusSeconds(30));

        assertThat(firstAttempt).isPresent();
        assertThat(firstAttempt.get().organizationId()).isEqualTo(orgId);
        assertThat(firstAttempt.get().userId()).isEqualTo(userId);
        assertThat(firstAttempt.get().purpose()).isEqualTo(ResetTokenPurpose.INITIAL_SETUP);

        // Segundo consumo falla (ya consumido)
        Optional<PasswordResetTokenRepository.ConsumedTokenInfo> secondAttempt =
                repository.consumeToken(tokenHash, ResetTokenPurpose.INITIAL_SETUP, now.plusSeconds(35));
        assertThat(secondAttempt).isEmpty();
    }

    @Test
    @DisplayName("consumeToken: token vencido devuelve Optional.empty()")
    void consumeToken_vencido_devuelveEmpty() {
        UUID orgId = insertarOrganizacion("CORP-EXPIRED", "Expired S.A.", "ACTIVA", "America/Lima");
        UUID userId = insertarUsuario(orgId, "expired@test.com", "Vencido", "COLABORADOR", true, null);

        UUID tokenId = UUID.randomUUID();
        String rawToken = tokenGenerator.generateRawToken();
        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        Instant now = Instant.now();
        // Creado hace 40 min, vencido hace 10 min
        Instant createdAt = now.minus(Duration.ofMinutes(40));
        Instant expiresAt = now.minus(Duration.ofMinutes(10));

        repository.insertToken(tokenId, orgId, userId, tokenHash, ResetTokenPurpose.INITIAL_SETUP, expiresAt, createdAt);

        // Intentar consumir en 'now' (posterior a expires_at)
        Optional<PasswordResetTokenRepository.ConsumedTokenInfo> attempt =
                repository.consumeToken(tokenHash, ResetTokenPurpose.INITIAL_SETUP, now);

        assertThat(attempt).isEmpty();
    }

    @Test
    @DisplayName("consumeToken: token con propósito distinto devuelve Optional.empty()")
    void consumeToken_propositoDistinto_devuelveEmpty() {
        UUID orgId = insertarOrganizacion("CORP-DIFF-PURP", "Diff Purp S.A.", "ACTIVA", "America/Lima");
        UUID userId = insertarUsuario(orgId, "diff@test.com", "Diff", "COLABORADOR", true, null);

        UUID tokenId = UUID.randomUUID();
        String rawToken = tokenGenerator.generateRawToken();
        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        // Insertar como PASSWORD_RESET
        repository.insertToken(tokenId, orgId, userId, tokenHash, ResetTokenPurpose.PASSWORD_RESET, expiresAt, now);

        // Intentar consumir como INITIAL_SETUP
        Optional<PasswordResetTokenRepository.ConsumedTokenInfo> attempt =
                repository.consumeToken(tokenHash, ResetTokenPurpose.INITIAL_SETUP, now.plusSeconds(10));

        assertThat(attempt).isEmpty();
    }

    @Test
    @DisplayName("consumeToken: usuario desactivado hace que el consumo falle y consumed_at permanezca NULL")
    void consumeToken_usuarioDesactivado_fallaYConsumedAtPermaneceNull() {
        UUID orgId = insertarOrganizacion("CORP-CONS-INACT", "Cons Inact S.A.", "ACTIVA", "America/Lima");
        UUID userId = insertarUsuario(orgId, "inact-user@test.com", "User Inactivo", "COLABORADOR", true, null);

        UUID tokenId = UUID.randomUUID();
        String rawToken = tokenGenerator.generateRawToken();
        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        repository.insertToken(tokenId, orgId, userId, tokenHash, ResetTokenPurpose.INITIAL_SETUP, expiresAt, now);

        // Desactivar usuario antes del consumo
        jdbc.update("UPDATE app_users SET is_active = 0 WHERE id = ?", userId.toString());

        Optional<PasswordResetTokenRepository.ConsumedTokenInfo> result =
                repository.consumeToken(tokenHash, ResetTokenPurpose.INITIAL_SETUP, now.plusSeconds(10));

        assertThat(result).isEmpty();

        // Verificar que consumed_at sigue siendo NULL en la base de datos
        List<OffsetDateTime> consumedAtList = jdbc.query(
                "SELECT consumed_at FROM password_reset_tokens WHERE id = ?",
                (rs, rowNum) -> rs.getObject("consumed_at", OffsetDateTime.class),
                tokenId.toString()
        );
        assertThat(consumedAtList).hasSize(1);
        assertThat(consumedAtList.get(0)).isNull();
    }

    @Test
    @DisplayName("consumeToken: organización suspendida hace que el consumo falle y consumed_at permanezca NULL")
    void consumeToken_organizacionNoActiva_fallaYConsumedAtPermaneceNull() {
        UUID orgId = insertarOrganizacion("CORP-CONS-SUSP", "Cons Susp S.A.", "ACTIVA", "America/Lima");
        UUID userId = insertarUsuario(orgId, "susp-user@test.com", "User Susp", "COLABORADOR", true, null);

        UUID tokenId = UUID.randomUUID();
        String rawToken = tokenGenerator.generateRawToken();
        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        repository.insertToken(tokenId, orgId, userId, tokenHash, ResetTokenPurpose.INITIAL_SETUP, expiresAt, now);

        // Suspender organización antes del consumo
        jdbc.update("UPDATE organizations SET service_status = 'SUSPENDIDA' WHERE id = ?", orgId.toString());

        Optional<PasswordResetTokenRepository.ConsumedTokenInfo> result =
                repository.consumeToken(tokenHash, ResetTokenPurpose.INITIAL_SETUP, now.plusSeconds(10));

        assertThat(result).isEmpty();

        List<OffsetDateTime> consumedAtList = jdbc.query(
                "SELECT consumed_at FROM password_reset_tokens WHERE id = ?",
                (rs, rowNum) -> rs.getObject("consumed_at", OffsetDateTime.class),
                tokenId.toString()
        );
        assertThat(consumedAtList).hasSize(1);
        assertThat(consumedAtList.get(0)).isNull();
    }

    @Test
    @DisplayName("consumeToken: password_hash ya configurado hace que el consumo falle y consumed_at permanezca NULL")
    void consumeToken_passwordHashNoNulo_fallaYConsumedAtPermaneceNull() {
        UUID orgId = insertarOrganizacion("CORP-CONS-PASS", "Cons Pass S.A.", "ACTIVA", "America/Lima");
        UUID userId = insertarUsuario(orgId, "pass-user@test.com", "User Pass", "COLABORADOR", true, null);

        UUID tokenId = UUID.randomUUID();
        String rawToken = tokenGenerator.generateRawToken();
        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        repository.insertToken(tokenId, orgId, userId, tokenHash, ResetTokenPurpose.INITIAL_SETUP, expiresAt, now);

        // Configurar contraseña previamente (por ejemplo, por otro flujo o concurrencia)
        jdbc.update("UPDATE app_users SET password_hash = '$argon2id$v=19$dummy' WHERE id = ?", userId.toString());

        Optional<PasswordResetTokenRepository.ConsumedTokenInfo> result =
                repository.consumeToken(tokenHash, ResetTokenPurpose.INITIAL_SETUP, now.plusSeconds(10));

        assertThat(result).isEmpty();

        List<OffsetDateTime> consumedAtList = jdbc.query(
                "SELECT consumed_at FROM password_reset_tokens WHERE id = ?",
                (rs, rowNum) -> rs.getObject("consumed_at", OffsetDateTime.class),
                tokenId.toString()
        );
        assertThat(consumedAtList).hasSize(1);
        assertThat(consumedAtList.get(0)).isNull();
    }

    // =========================================================================
    // 5. Restricción de clave foránea compuesta (organization_id, user_id)
    // =========================================================================

    @Test
    @DisplayName("Integridad referencial: token con (orgA, userB) falla por violación de FK compuesta")
    void claveForaneaCompuesta_orgAConUserB_lanzaTokenDataAccessException() {
        UUID orgA = insertarOrganizacion("CORP-FK-A", "FK A", "ACTIVA", "UTC");
        UUID orgB = insertarOrganizacion("CORP-FK-B", "FK B", "ACTIVA", "UTC");
        UUID userB = insertarUsuario(orgB, "userB@corpB.com", "User B", "ADMINISTRADOR", true, null);

        UUID tokenId = UUID.randomUUID();
        byte[] tokenHash = tokenGenerator.computeSha256(tokenGenerator.generateRawToken());
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        // orgA con userB que pertenece a orgB
        assertThatThrownBy(() -> repository.insertToken(
                tokenId, orgA, userB, tokenHash, ResetTokenPurpose.INITIAL_SETUP, expiresAt, now
        )).isInstanceOf(TokenDataAccessException.class);
    }

    // =========================================================================
    // 6. Concurrencia real: un solo ganador ante consumo simultáneo
    // =========================================================================

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrencia real: múltiples hilos compiten por consumir el mismo token y exactamente uno gana")
    void concurrencia_unSoloGanador() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String rawToken = tokenGenerator.generateRawToken();
        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(30));

        try {
            // Setup directo fuera de transacción del test
            jdbc.update("""
                    INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                        max_active_employees, max_active_sites)
                    VALUES (?, 'CONC-ORG', 'Conc Org', 'ACTIVA', 'UTC', 10, 1)
                    """, orgId.toString());

            jdbc.update("""
                    INSERT INTO app_users
                        (id, organization_id, email, normalized_email, display_name, password_hash,
                         role_code, is_active, auth_version)
                    VALUES (?, ?, 'conc@test.com', 'conc@test.com', 'Conc User', null, 'ADMINISTRADOR', 1, 0)
                    """, userId.toString(), orgId.toString());

            repository.insertToken(tokenId, orgId, userId, tokenHash, ResetTokenPurpose.INITIAL_SETUP, expiresAt, now);

            // 10 hilos intentan consumir el token concurrentemente
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch endGate = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            List<Future<Optional<PasswordResetTokenRepository.ConsumedTokenInfo>>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        startGate.await();
                        Optional<PasswordResetTokenRepository.ConsumedTokenInfo> res =
                                repository.consumeToken(tokenHash, ResetTokenPurpose.INITIAL_SETUP, now.plusSeconds(5));
                        if (res.isPresent()) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                        return res;
                    } finally {
                        endGate.countDown();
                    }
                }));
            }

            // Disparar simultáneamente
            startGate.countDown();
            boolean completed = endGate.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).isTrue();
            assertThat(successCount.get()).as("Debe haber exactamente un ganador").isEqualTo(1);
            assertThat(failureCount.get()).as("Los demás intentos deben ser rechazados").isEqualTo(threadCount - 1);
        } finally {
            // Limpieza
            jdbc.update("DELETE FROM password_reset_tokens WHERE id = ?", tokenId.toString());
            jdbc.update("DELETE FROM app_users WHERE id = ?", userId.toString());
            jdbc.update("DELETE FROM organizations WHERE id = ?", orgId.toString());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrencia real: 10 emisiones paralelas para el mismo usuario resultan en exactamente un token activo")
    void concurrencia_emisionesParalelasMismoUsuario_exactamenteUnTokenActivo() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            jdbc.update("""
                    INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                        max_active_employees, max_active_sites)
                    VALUES (?, 'CONC-EMIT-ORG', 'Conc Emit Org', 'ACTIVA', 'UTC', 10, 1)
                    """, orgId.toString());

            jdbc.update("""
                    INSERT INTO app_users
                        (id, organization_id, email, normalized_email, display_name, password_hash,
                         role_code, is_active, auth_version)
                    VALUES (?, ?, 'conc.emit@test.com', 'conc.emit@test.com', 'Conc User', null, 'ADMINISTRADOR', 1, 0)
                    """, userId.toString(), orgId.toString());

            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch endGate = new CountDownLatch(threadCount);

            List<Future<Optional<InitialSetupTokenEmission>>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        startGate.await();
                        return issueInitialSetupToken.issue(orgId, userId);
                    } finally {
                        endGate.countDown();
                    }
                }));
            }

            startGate.countDown();
            boolean completed = endGate.await(15, TimeUnit.SECONDS);
            assertThat(completed).as("Todas las tareas deben completarse dentro del tiempo límite").isTrue();

            // 1. Recorrer cada Future con timeout razonable
            // 2. Cada Future debe completarse sin excepción y devolver Optional presente
            // 3. Debe verificarse que las 10 solicitudes emitieron un token
            List<UUID> emittedTokenIds = new ArrayList<>();
            for (Future<Optional<InitialSetupTokenEmission>> future : futures) {
                Optional<InitialSetupTokenEmission> opt = future.get(5, TimeUnit.SECONDS);
                assertThat(opt).as("Cada emisión concurrente debe retornar un resultado presente").isPresent();
                emittedTokenIds.add(opt.get().tokenId());
            }

            assertThat(emittedTokenIds)
                    .as("Las 10 solicitudes deben haber emitido un token con identificador único")
                    .hasSize(threadCount)
                    .doesNotHaveDuplicates();

            // 4. Verificaciones en SQL Server:
            // a) Existen exactamente 10 registros INITIAL_SETUP para ese usuario
            Integer totalTokens = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM password_reset_tokens
                    WHERE organization_id = ?
                      AND user_id = ?
                      AND purpose = 'INITIAL_SETUP'
                    """, Integer.class, orgId.toString(), userId.toString());
            assertThat(totalTokens).as("Deben existir exactamente 10 registros INITIAL_SETUP para el usuario").isEqualTo(threadCount);

            // b) Exactamente 1 tiene consumed_at IS NULL y revoked_at IS NULL
            Integer activeTokens = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM password_reset_tokens
                    WHERE organization_id = ?
                      AND user_id = ?
                      AND purpose = 'INITIAL_SETUP'
                      AND consumed_at IS NULL
                      AND revoked_at IS NULL
                    """, Integer.class, orgId.toString(), userId.toString());
            assertThat(activeTokens).as("Debe haber exactamente un token activo (no consumido y no revocado)").isEqualTo(1);

            // c) Exactamente 9 tienen revoked_at IS NOT NULL
            Integer revokedTokens = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM password_reset_tokens
                    WHERE organization_id = ?
                      AND user_id = ?
                      AND purpose = 'INITIAL_SETUP'
                      AND revoked_at IS NOT NULL
                    """, Integer.class, orgId.toString(), userId.toString());
            assertThat(revokedTokens).as("Exactamente 9 tokens deben tener revoked_at poblado").isEqualTo(threadCount - 1);

            // d) Ningún token está consumido
            Integer consumedTokens = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM password_reset_tokens
                    WHERE organization_id = ?
                      AND user_id = ?
                      AND purpose = 'INITIAL_SETUP'
                      AND consumed_at IS NOT NULL
                    """, Integer.class, orgId.toString(), userId.toString());
            assertThat(consumedTokens).as("Ningún token debe estar consumido").isZero();

        } finally {
            // 5. Limpieza robusta en finally, incluido el cierre del ExecutorService
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            jdbc.update("DELETE FROM password_reset_tokens WHERE organization_id = ?", orgId.toString());
            jdbc.update("DELETE FROM app_users WHERE organization_id = ?", orgId.toString());
            jdbc.update("DELETE FROM organizations WHERE id = ?", orgId.toString());
        }
    }

    // =========================================================================
    // 7. Indisponibilidad de conexión propaga TokenDataAccessException
    // =========================================================================

    @Test
    @DisplayName("Fallo de conexión SQL Server: propaga TokenDataAccessException con mensaje seguro")
    void falloConexion_propagaTokenDataAccessException() {
        DriverManagerDataSource badDataSource = new DriverManagerDataSource();
        badDataSource.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        badDataSource.setUrl("jdbc:sqlserver://127.0.0.1:54321;databaseName=turnoshift_dev;loginTimeout=1");
        badDataSource.setUsername("sa");
        badDataSource.setPassword("wrong-password");

        JdbcPasswordResetTokenRepository badRepo =
                new JdbcPasswordResetTokenRepository(new JdbcTemplate(badDataSource));

        assertThatThrownBy(() -> badRepo.findEligibility(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(TokenDataAccessException.class)
                .hasMessageContaining("[IDENTITY_TOKEN_DATA_ACCESS_ERROR]");
    }
}
