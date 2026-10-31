package com.turnoshift.schedule.infrastructure.persistence;

import com.turnoshift.schedule.domain.ports.PlanningOperationBusyException;
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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@DisplayName("IT: Candado Operativo de Planificación (JdbcPlanningOperationalLockAdapter)")
class JdbcPlanningOperationalLockAdapterIT {

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
    private JdbcPlanningOperationalLockAdapter lockAdapter;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Adquisición correcta del candado exclusivo dentro de una transacción activa")
    void adquisicionCorrectaDentroDeTransaccion() {
        UUID orgId = UUID.randomUUID();

        transactionTemplate.execute(status -> {
            lockAdapter.acquirePlanningLock(orgId);

            // Verificar que el candado transaccional existe en SQL Server en modo Exclusive
            String resourceName = "TurnoShift_OrgPlanning_" + orgId;
            String mode = jdbc.queryForObject(
                    "SELECT APPLOCK_MODE('public', ?, 'Transaction');",
                    String.class,
                    resourceName
            );
            assertThat(mode).isEqualTo("Exclusive");
            return null;
        });
    }

    @Test
    @DisplayName("Rechazo controlado con IllegalStateException si no existe transacción activa")
    void rechazoSiNoExisteTransaccionActiva() {
        UUID orgId = UUID.randomUUID();

        assertThatThrownBy(() -> lockAdapter.acquirePlanningLock(orgId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transacción activa");
    }

    @Test
    @DisplayName("Timeout de adquisición concurrente: segundo hilo lanza PlanningOperationBusyException")
    void timeoutDeAdquisicionConcurrente() throws Exception {
        UUID orgId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLockLatch = new CountDownLatch(1);
        AtomicBoolean thread1LockHeld = new AtomicBoolean(false);

        // Hilo 1: adquiere el candado y lo mantiene dentro de su transacción
        Future<?> future1 = executor.submit(() -> {
            transactionTemplate.execute(status -> {
                lockAdapter.acquirePlanningLock(orgId);
                thread1LockHeld.set(true);
                lockAcquiredLatch.countDown();
                try {
                    releaseLockLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        // Esperar a que el Hilo 1 tenga efectivamente el candado
        assertThat(lockAcquiredLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(thread1LockHeld.get()).isTrue();

        // Hilo 2: intenta adquirir el MISMO recurso con timeout corto (500 ms)
        Future<?> future2 = executor.submit(() -> {
            transactionTemplate.execute(status -> {
                lockAdapter.acquirePlanningLock(orgId, 500);
                return null;
            });
        });

        // El Hilo 2 debe fallar con PlanningOperationBusyException por expiración de timeout
        assertThatThrownBy(future2::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PlanningOperationBusyException.class);

        // Liberar al Hilo 1
        releaseLockLatch.countDown();
        future1.get(5, TimeUnit.SECONDS);
        executor.shutdown();
    }

    @Test
    @DisplayName("Aislamiento de candado por organización: dos organizaciones adquieren simultáneamente sin conflicto")
    void aislamientoDeCandadoPorOrganizacion() throws Exception {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothAcquiredLatch = new CountDownLatch(2);

        Future<?> futureA = executor.submit(() -> {
            transactionTemplate.execute(status -> {
                lockAdapter.acquirePlanningLock(orgA);
                bothAcquiredLatch.countDown();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        Future<?> futureB = executor.submit(() -> {
            transactionTemplate.execute(status -> {
                lockAdapter.acquirePlanningLock(orgB);
                bothAcquiredLatch.countDown();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        assertThat(bothAcquiredLatch.await(5, TimeUnit.SECONDS)).isTrue();
        futureA.get(5, TimeUnit.SECONDS);
        futureB.get(5, TimeUnit.SECONDS);
        executor.shutdown();
    }
}
