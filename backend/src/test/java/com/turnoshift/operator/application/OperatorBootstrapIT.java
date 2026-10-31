package com.turnoshift.operator.application;

import com.turnoshift.identity.domain.PasswordBlocklist;
import com.turnoshift.identity.domain.PasswordBlocklistUnavailableException;
import com.turnoshift.identity.domain.PasswordPolicy;
import com.turnoshift.identity.infrastructure.security.Argon2PasswordService;
import com.turnoshift.operator.domain.PlatformOperator;
import com.turnoshift.operator.domain.PlatformOperatorRepository;
import com.turnoshift.operator.infrastructure.TransactionalOperatorInserter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.time.Duration;
import java.util.Set;
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
@DisplayName("IT: OperatorBootstrapRunner — persistencia y concurrencia en SQL Server real")
class OperatorBootstrapIT {

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
    PlatformOperatorRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Argon2PasswordService argon2;

    @Autowired
    TransactionalOperatorInserter inserter;

    static final String VALID_PASSWORD = "P@ssw0rdExtr@Long15chars!";
    static final String VALID_EMAIL = "testoperator@turnoshift.com";
    static final String VALID_NAME = "Test Operator";

    @BeforeEach
    void cleanOperators() {
        jdbcTemplate.update("DELETE FROM platform_operators");
    }

    PasswordBlocklist fakeBlocklist() {
        return new FakeBlocklist(Set.of());
    }

    OperatorBootstrapRunner runnerEnabled(String email, String name, String pwd, PasswordBlocklist bl) {
        return new OperatorBootstrapRunner(true, repository, bl, argon2, inserter) {
            @Override String getEnvEmail() { return email; }
            @Override String getEnvDisplayName() { return name; }
            @Override char[] getEnvPassword() { return pwd != null ? pwd.toCharArray() : null; }
        };
    }

    // =========================================================================
    // 1. Bootstrap desactivado
    // =========================================================================

    @Test
    @DisplayName("Bootstrap desactivado: no modifica la base de datos")
    void bootstrapDesactivado_noModificaBD() throws Exception {
        long countBefore = repository.count();
        new OperatorBootstrapRunner(false, repository, fakeBlocklist(), argon2, inserter).run(null);
        assertThat(repository.count()).isEqualTo(countBefore);
    }

    // =========================================================================
    // 2. Bootstrap valido crea operador con hash Argon2id verificado
    // =========================================================================

    @Test
    @DisplayName("Bootstrap valido: crea exactamente un operador con hash Argon2id verificado")
    void bootstrapValido_creaUnOperadorConHashArgon2() throws Exception {
        var runner = runnerEnabled(VALID_EMAIL, VALID_NAME, VALID_PASSWORD, fakeBlocklist());
        runner.run(null);

        assertThat(repository.count()).isEqualTo(1L);
        PlatformOperator op = repository.findByNormalizedEmail(VALID_EMAIL).orElseThrow();
        assertThat(op.displayName()).isEqualTo(VALID_NAME);
        assertThat(op.isActive()).isTrue();
        assertThat(op.authVersion()).isEqualTo(1L);
        // Verificar que el hash es Argon2id real
        assertThat(op.passwordHash()).startsWith("$argon2id$");
        // Verificar que la clave original verifica contra el hash persistido
        assertThat(argon2.verificar(
                PasswordPolicy.prepararParaCodificacion(VALID_PASSWORD), op.passwordHash())).isTrue();
    }

    // =========================================================================
    // 3. Bootstrap con operador existente no altera
    // =========================================================================

    @Test
    @DisplayName("Bootstrap con operador existente: no crea ni altera registros")
    void bootstrapConOperadorExistente_noInserta() throws Exception {
        // Crear primer operador
        runnerEnabled(VALID_EMAIL, VALID_NAME, VALID_PASSWORD, fakeBlocklist()).run(null);
        assertThat(repository.count()).isEqualTo(1L);
        String hashBefore = repository.findByNormalizedEmail(VALID_EMAIL).orElseThrow().passwordHash();

        // Segundo intento con datos diferentes
        runnerEnabled("otro@ts.com", "Otro Op", VALID_PASSWORD, fakeBlocklist()).run(null);
        assertThat(repository.count()).isEqualTo(1L);
        // El operador original no fue alterado
        assertThat(repository.findByNormalizedEmail(VALID_EMAIL).orElseThrow().passwordHash())
                .isEqualTo(hashBefore);
    }

    // =========================================================================
    // 4. Contrasena invalida no inserta
    // =========================================================================

    @Test
    @DisplayName("Contrasena invalida (corta): no inserta operador")
    void contrasenaInvalida_noInserta() {
        var runner = runnerEnabled(VALID_EMAIL, VALID_NAME, "corta", fakeBlocklist());
        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(BootstrapConfigurationException.class);
        assertThat(repository.count()).isZero();
    }

    // =========================================================================
    // 5. Blocklist no disponible falla cerrado
    // =========================================================================

    @Test
    @DisplayName("Blocklist no disponible: falla cerrado, no inserta")
    void blocklistNoDisponible_noInserta() {
        var runner = runnerEnabled(VALID_EMAIL, VALID_NAME, VALID_PASSWORD,
                pw -> { throw new PasswordBlocklistUnavailableException(); });
        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(BootstrapConfigurationException.class);
        assertThat(repository.count()).isZero();
    }

    // =========================================================================
    // 6. Concurrencia real con sp_getapplock
    // =========================================================================

    @Test
    @DisplayName("Dos bootstraps concurrentes: exactamente un operador creado via sp_getapplock")
    void dosConcurrentes_exactamenteUnOperador() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);

            Callable<Void> task = () -> {
                readyLatch.countDown();
                boolean started = startLatch.await(10, TimeUnit.SECONDS);
                if (!started) {
                    throw new IllegalStateException("Timeout esperando disparo de concurrencia");
                }
                runnerEnabled(VALID_EMAIL, VALID_NAME, VALID_PASSWORD, fakeBlocklist()).run(null);
                return null;
            };

            Future<Void> future1 = executor.submit(task);
            Future<Void> future2 = executor.submit(task);

            boolean ready = readyLatch.await(10, TimeUnit.SECONDS);
            assertThat(ready).as("Ambos hilos de bootstrap deben estar listos para ejecutar").isTrue();

            startLatch.countDown();

            // Cualquier excepción de bootstrap en cualquiera de los hilos hace fallar la prueba
            future1.get(30, TimeUnit.SECONDS);
            future2.get(30, TimeUnit.SECONDS);

            // Evidencia SQL: exactamente un platform_operator al final
            assertThat(repository.count()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // =========================================================================
    // 7. Usa blocklist inyectada (no llama HIBP real)
    // =========================================================================

    @Test
    @DisplayName("Bootstrap usa blocklist inyectada: ninguna prueba llama a HIBP real")
    void bootstrapUsaBlocklistInyectada_noLlamaHibpReal() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        PasswordBlocklist countingBlocklist = pw -> {
            callCount.incrementAndGet();
            return false;
        };
        runnerEnabled(VALID_EMAIL, VALID_NAME, VALID_PASSWORD, countingBlocklist).run(null);
        assertThat(callCount.get()).isGreaterThan(0);
        assertThat(repository.count()).isEqualTo(1L);
    }

    static class FakeBlocklist implements PasswordBlocklist {
        private final Set<String> blocked;
        FakeBlocklist(Set<String> blocked) { this.blocked = Set.copyOf(blocked); }
        @Override public boolean estaBloquada(String pw) { return blocked.contains(pw); }
    }
}
