package com.turnoshift.policy.infrastructure.persistence;

import com.turnoshift.policy.domain.NewPolicyVersion;
import com.turnoshift.policy.domain.PolicyConcurrentModificationException;
import com.turnoshift.policy.domain.PolicyDataAccessException;
import com.turnoshift.policy.domain.PolicyOperationBusyException;
import com.turnoshift.policy.domain.PolicyReason;
import com.turnoshift.policy.domain.PolicyThresholds;
import com.turnoshift.policy.domain.PolicyVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@DisplayName("IT: JdbcPolicyRepository")
class JdbcPolicyRepositoryIT {

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

    @Autowired
    JdbcPolicyRepository policyRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID insertarOrganizacion(String code) {
        UUID orgId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Org Test', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, code);
        return orgId;
    }

    private UUID insertarUsuario(UUID orgId, String email) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                VALUES (?, ?, ?, ?, 'Admin Test', 'ADMINISTRADOR', NULL)
                """, userId, orgId, email, email.toLowerCase());
        return userId;
    }

    // =========================================================================
    // Pruebas
    // =========================================================================

    @Test
    @DisplayName("Lectura inicial sin política retorna valores vacíos y siguiente versión 1")
    void lecturaInicialSinPolitica() {
        UUID orgId = insertarOrganizacion("ORG-POL-INIT");

        Optional<PolicyVersion> current = policyRepository.findCurrentPolicy(orgId);
        Optional<UUID> currentId = policyRepository.findCurrentPolicyVersionId(orgId);
        List<PolicyVersion> history = policyRepository.findHistory(orgId);
        int nextVersion = policyRepository.findNextVersionNumber(orgId);

        assertThat(current).isEmpty();
        assertThat(currentId).isEmpty();
        assertThat(history).isEmpty();
        assertThat(nextVersion).isEqualTo(1);
    }

    @Test
    @DisplayName("acquirePolicyLock fuera de transacción Spring activa es rechazado con PolicyDataAccessException")
    void lockFueraDeTransaccionRechazado() {
        UUID orgId = insertarOrganizacion("ORG-POL-LOCK-FAIL");

        assertThatThrownBy(() -> policyRepository.acquirePolicyLock(orgId))
                .isInstanceOf(PolicyDataAccessException.class)
                .hasMessageContaining("transacción activa");
    }

    @Test
    @DisplayName("acquirePolicyLock dentro de transacción Spring activa tiene éxito")
    void lockExitosoDentroDeTransaccion() {
        UUID orgId = insertarOrganizacion("ORG-POL-LOCK-OK");

        transactionTemplate.executeWithoutResult(status -> {
            policyRepository.acquirePolicyLock(orgId);
        });
    }

    @Test
    @DisplayName("Contención real: Conexión A conserva el lock, Conexión B intenta el mismo recurso y recibe PolicyOperationBusyException")
    void contencionRealBloqueoOcupado() throws Exception {
        UUID orgId = insertarOrganizacion("ORG-POL-LOCK-BUSY");

        CountDownLatch aAdquirioLock = new CountDownLatch(1);
        CountDownLatch bCompleto = new CountDownLatch(1);
        AtomicReference<Throwable> bException = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Hilo A: adquiere el lock y lo retiene durante la contención de B
        Future<?> futureA = executor.submit(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                policyRepository.acquirePolicyLock(orgId);
                aAdquirioLock.countDown();
                try {
                    // Espera a que B termine su intento (o hasta 10 segundos)
                    boolean awaited = bCompleto.await(10, TimeUnit.SECONDS);
                    if (!awaited) {
                        throw new RuntimeException("Timeout esperando a hilo B");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });

        // Hilo B: espera a que A adquiera el lock, luego intenta adquirir el mismo lock (con timeout de 5s)
        Future<?> futureB = executor.submit(() -> {
            try {
                boolean aListo = aAdquirioLock.await(5, TimeUnit.SECONDS);
                if (!aListo) {
                    throw new RuntimeException("Hilo A no adquirió el lock a tiempo");
                }
                transactionTemplate.executeWithoutResult(status -> {
                    policyRepository.acquirePolicyLock(orgId);
                });
            } catch (Throwable t) {
                bException.set(t);
            } finally {
                bCompleto.countDown();
            }
        });

        futureB.get(15, TimeUnit.SECONDS);
        futureA.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(bException.get())
                .as("Hilo B debe recibir PolicyOperationBusyException por recurso ocupado")
                .isInstanceOf(PolicyOperationBusyException.class);

        assertThat(bException.get().getMessage())
                .isEqualTo(PolicyOperationBusyException.DEFAULT_MESSAGE)
                .doesNotContain("-999", "-1", "sp_getapplock", "res", "SELECT", "TurnoShift");
    }

    @Test
    @DisplayName("Liberación de lock: Transacción A adquiere el lock y termina (commit); Transacción B adquiere el mismo lock inmediatamente sin retención en el pool")
    void liberacionDeLockAlTerminarTransaccionCommit() {
        UUID orgId = insertarOrganizacion("ORG-POL-LOCK-REL-COMMIT");

        // Transacción A adquiere el lock y termina exitosamente (commit)
        transactionTemplate.executeWithoutResult(status -> {
            policyRepository.acquirePolicyLock(orgId);
        });

        // Transacción B sobre el mismo recurso adquiere el lock inmediatamente sin contención
        transactionTemplate.executeWithoutResult(status -> {
            policyRepository.acquirePolicyLock(orgId);
        });
    }

    @Test
    @DisplayName("Liberación de lock: Transacción A adquiere el lock y hace rollback; Transacción B adquiere el mismo lock inmediatamente")
    void liberacionDeLockAlTerminarTransaccionRollback() {
        UUID orgId = insertarOrganizacion("ORG-POL-LOCK-REL-ROLLBACK");

        // Transacción A adquiere el lock y fuerza rollback lanzando IllegalStateException explícito
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            policyRepository.acquirePolicyLock(orgId);
            throw new IllegalStateException("Rollback forzado para verificar liberación del lock");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Rollback forzado para verificar liberación del lock");

        // Transacción B sobre el mismo recurso adquiere el lock inmediatamente sin contención
        transactionTemplate.executeWithoutResult(status -> {
            policyRepository.acquirePolicyLock(orgId);
        });
    }

    @Test
    @DisplayName("insertPolicyVersion devuelve timestamps e id de SQL Server que coinciden con la fila persistida")
    void insertDevuelveTimestampsEIdDeSqlServer() {
        UUID orgId = insertarOrganizacion("ORG-POL-INSERT");
        UUID userId = insertarUsuario(orgId, "admin-insert@turnoshift.com");

        NewPolicyVersion draft = new NewPolicyVersion(
                orgId,
                1,
                new PolicyThresholds(660, 2400),
                userId,
                new PolicyReason("Política inicial de prueba")
        );

        Instant antesDeInsertar = Instant.now().minusSeconds(5);
        PolicyVersion persisted = policyRepository.insertPolicyVersion(draft);
        Instant despuesDeInsertar = Instant.now().plusSeconds(5);

        assertThat(persisted).isNotNull();
        assertThat(persisted.id()).isNotNull();
        assertThat(persisted.organizationId()).isEqualTo(orgId);
        assertThat(persisted.versionNumber()).isEqualTo(1);
        assertThat(persisted.thresholds().minimumRestMinutes()).isEqualTo(660);
        assertThat(persisted.thresholds().weeklyWarningMinutes()).isEqualTo(2400);
        assertThat(persisted.createdByUserId()).isEqualTo(userId);
        assertThat(persisted.changeReason().value()).isEqualTo("Política inicial de prueba");

        // Timestamps autoritativos de BD
        assertThat(persisted.effectiveAt()).isBetween(antesDeInsertar, despuesDeInsertar);
        assertThat(persisted.createdAt()).isBetween(antesDeInsertar, despuesDeInsertar);

        // Verificación directa contra la tabla planning_policy_versions en SQL Server
        jdbc.query(
                "SELECT id, organization_id, version_number, minimum_rest_minutes, weekly_warning_minutes, " +
                "effective_at, created_by_user_id, change_reason, created_at " +
                "FROM planning_policy_versions WHERE id = ?",
                rs -> {
                    assertThat(UUID.fromString(rs.getString("id"))).isEqualTo(persisted.id());
                    assertThat(UUID.fromString(rs.getString("organization_id"))).isEqualTo(orgId);
                    assertThat(rs.getInt("version_number")).isEqualTo(1);
                    assertThat(rs.getInt("minimum_rest_minutes")).isEqualTo(660);
                    assertThat(rs.getInt("weekly_warning_minutes")).isEqualTo(2400);
                    assertThat(UUID.fromString(rs.getString("created_by_user_id"))).isEqualTo(userId);
                    assertThat(rs.getString("change_reason")).isEqualTo("Política inicial de prueba");

                    Instant dbEffectiveAt = rs.getObject("effective_at", OffsetDateTime.class).toInstant();
                    Instant dbCreatedAt = rs.getObject("created_at", OffsetDateTime.class).toInstant();

                    assertThat(persisted.effectiveAt()).isEqualTo(dbEffectiveAt);
                    assertThat(persisted.createdAt()).isEqualTo(dbCreatedAt);
                },
                persisted.id()
        );
    }

    @Test
    @DisplayName("findNextVersionNumber devuelve 1 inicialmente y avanza a 2 tras insertar versión 1")
    void versionSiguienteSecuencial() {
        UUID orgId = insertarOrganizacion("ORG-POL-SEQ");
        UUID userId = insertarUsuario(orgId, "admin-seq@turnoshift.com");

        assertThat(policyRepository.findNextVersionNumber(orgId)).isEqualTo(1);

        NewPolicyVersion v1 = new NewPolicyVersion(
                orgId, 1, new PolicyThresholds(660, 2400), userId, new PolicyReason("v1")
        );
        policyRepository.insertPolicyVersion(v1);

        assertThat(policyRepository.findNextVersionNumber(orgId)).isEqualTo(2);

        NewPolicyVersion v2 = new NewPolicyVersion(
                orgId, 2, new PolicyThresholds(720, 2400), userId, new PolicyReason("v2")
        );
        policyRepository.insertPolicyVersion(v2);

        assertThat(policyRepository.findNextVersionNumber(orgId)).isEqualTo(3);
    }

    @Test
    @DisplayName("findCurrentPolicy, findCurrentPolicyVersionId y findHistory con orden descendente estricto")
    void currentYHistoryDescendente() {
        UUID orgId = insertarOrganizacion("ORG-POL-HIST");
        UUID userId = insertarUsuario(orgId, "admin-hist@turnoshift.com");

        // Versión 1
        NewPolicyVersion draft1 = new NewPolicyVersion(
                orgId, 1, new PolicyThresholds(660, 2400), userId, new PolicyReason("Versión inicial 1")
        );
        PolicyVersion v1 = policyRepository.insertPolicyVersion(draft1);
        policyRepository.updateCurrentPolicyVersion(orgId, v1.id(), null);

        assertThat(policyRepository.findCurrentPolicy(orgId)).contains(v1);
        assertThat(policyRepository.findCurrentPolicyVersionId(orgId)).contains(v1.id());

        // Versión 2
        NewPolicyVersion draft2 = new NewPolicyVersion(
                orgId, 2, new PolicyThresholds(720, 2800), userId, new PolicyReason("Versión actualizada 2")
        );
        PolicyVersion v2 = policyRepository.insertPolicyVersion(draft2);
        policyRepository.updateCurrentPolicyVersion(orgId, v2.id(), v1.id());

        assertThat(policyRepository.findCurrentPolicy(orgId)).contains(v2);
        assertThat(policyRepository.findCurrentPolicyVersionId(orgId)).contains(v2.id());

        // Historial ordenado descendentemente por version_number (v2 primero, luego v1)
        List<PolicyVersion> history = policyRepository.findHistory(orgId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).versionNumber()).isEqualTo(2);
        assertThat(history.get(0).id()).isEqualTo(v2.id());
        assertThat(history.get(1).versionNumber()).isEqualTo(1);
        assertThat(history.get(1).id()).isEqualTo(v1.id());
    }

    @Test
    @DisplayName("Actualización condicional de puntero exitosa y conflicto PolicyConcurrentModificationException por puntero desactualizado")
    void actualizacionCondicionalExitosaYConflicto() {
        UUID orgId = insertarOrganizacion("ORG-POL-UPDATE");
        UUID userId = insertarUsuario(orgId, "admin-update@turnoshift.com");

        NewPolicyVersion draft1 = new NewPolicyVersion(
                orgId, 1, new PolicyThresholds(660, 2400), userId, new PolicyReason("v1")
        );
        PolicyVersion v1 = policyRepository.insertPolicyVersion(draft1);

        // 1. Actualización exitosa desde puntero null a v1.id
        policyRepository.updateCurrentPolicyVersion(orgId, v1.id(), null);

        jdbc.query(
                "SELECT current_policy_version_id, lock_version FROM organizations WHERE id = ?",
                rs -> {
                    assertThat(UUID.fromString(rs.getString("current_policy_version_id"))).isEqualTo(v1.id());
                    assertThat(rs.getLong("lock_version")).isEqualTo(1L);
                },
                orgId
        );

        // 2. Intento de actualización con puntero esperado incorrecto (esperaba null, pero ya es v1.id)
        NewPolicyVersion draft2 = new NewPolicyVersion(
                orgId, 2, new PolicyThresholds(720, 2400), userId, new PolicyReason("v2")
        );
        PolicyVersion v2 = policyRepository.insertPolicyVersion(draft2);

        assertThatThrownBy(() -> policyRepository.updateCurrentPolicyVersion(orgId, v2.id(), null))
                .isInstanceOf(PolicyConcurrentModificationException.class);

        // 3. Intento de actualización con un UUID aleatorio esperado
        assertThatThrownBy(() -> policyRepository.updateCurrentPolicyVersion(orgId, v2.id(), UUID.randomUUID()))
                .isInstanceOf(PolicyConcurrentModificationException.class);

        // 4. Verificar que el puntero sigue siendo v1.id y lock_version sigue siendo 1
        jdbc.query(
                "SELECT current_policy_version_id, lock_version FROM organizations WHERE id = ?",
                rs -> {
                    assertThat(UUID.fromString(rs.getString("current_policy_version_id"))).isEqualTo(v1.id());
                    assertThat(rs.getLong("lock_version")).isEqualTo(1L);
                },
                orgId
        );
    }
}
