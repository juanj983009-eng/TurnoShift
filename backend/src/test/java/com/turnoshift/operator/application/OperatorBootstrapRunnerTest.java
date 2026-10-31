package com.turnoshift.operator.application;

import com.turnoshift.identity.domain.PasswordBlocklist;
import com.turnoshift.identity.domain.PasswordBlocklistUnavailableException;
import com.turnoshift.identity.infrastructure.security.Argon2PasswordService;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.PlatformOperator;
import com.turnoshift.operator.domain.PlatformOperatorRepository;
import com.turnoshift.operator.infrastructure.TransactionalOperatorInserter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Unit: OperatorBootstrapRunner — bootstrap condicional del primer operador de plataforma")
class OperatorBootstrapRunnerTest {

    static final String VALID_PASSWORD = "P@ssw0rdExtr@Long15chars!";
    static final String VALID_EMAIL = "operador@turnoshift.com";
    static final String VALID_NAME = "Operador Principal";

    InMemoryOperatorRepository repo;
    FakeBlocklist blocklist;
    Argon2PasswordService argon2;
    ApplicationArguments noArgs;

    @BeforeEach
    void setUp() {
        repo = new InMemoryOperatorRepository();
        blocklist = new FakeBlocklist(Set.of());
        argon2 = new Argon2PasswordService();
        noArgs = null;
    }

    // Crea un runner habilitado que sobreescribe metodos de lectura de env y delegacion transaccional
    OperatorBootstrapRunner runnerEnabled(String email, String name, String pwd, PasswordBlocklist bl) {
        InMemoryOperatorRepository myRepo = this.repo;
        Argon2PasswordService myArgon2 = this.argon2;
        JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
        when(mockJdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        OperatorInserter inserter = new TransactionalOperatorInserter(myRepo, mockJdbc);
        return new OperatorBootstrapRunner(true, myRepo, bl, myArgon2, inserter) {
            @Override String getEnvEmail() { return email; }
            @Override String getEnvDisplayName() { return name; }
            @Override char[] getEnvPassword() { return pwd != null ? pwd.toCharArray() : null; }
        };
    }

    OperatorBootstrapRunner runnerDisabled() {
        return new OperatorBootstrapRunner(false, repo, blocklist, argon2, null);
    }

    // =========================================================================
    // 1. Bootstrap desactivado
    // =========================================================================

    @Test
    @DisplayName("Bootstrap desactivado: no ejecuta ninguna consulta ni insercion")
    void bootstrapDesactivado_noTocaBD() throws Exception {
        runnerDisabled().run(noArgs);
        assertThat(repo.interacted).isFalse();
    }

    // =========================================================================
    // 2. Env vars incompletas: falla sin tocar BD
    // =========================================================================

    @Test
    @DisplayName("Email faltante: BootstrapConfigurationException sin tocar BD")
    void emailFaltante_lanzaBootstrapConfigurationException() {
        OperatorBootstrapRunner runner = new OperatorBootstrapRunner(true, repo, blocklist, argon2, null) {
            @Override String getEnvEmail() { return null; }
            @Override String getEnvDisplayName() { return VALID_NAME; }
            @Override char[] getEnvPassword() { return VALID_PASSWORD.toCharArray(); }
        };
        assertThatThrownBy(() -> runner.run(noArgs))
                .isInstanceOf(BootstrapConfigurationException.class)
                .hasMessageContaining("[OPERATOR_BOOTSTRAP_CONFIG_ERROR]");
        assertThat(repo.interacted).isFalse();
    }

    @Test
    @DisplayName("Nombre faltante: BootstrapConfigurationException sin tocar BD")
    void nombreFaltante_lanzaBootstrapConfigurationException() {
        OperatorBootstrapRunner runner = new OperatorBootstrapRunner(true, repo, blocklist, argon2, null) {
            @Override String getEnvEmail() { return VALID_EMAIL; }
            @Override String getEnvDisplayName() { return "  "; }
            @Override char[] getEnvPassword() { return VALID_PASSWORD.toCharArray(); }
        };
        assertThatThrownBy(() -> runner.run(noArgs))
                .isInstanceOf(BootstrapConfigurationException.class);
        assertThat(repo.interacted).isFalse();
    }

    @Test
    @DisplayName("Password faltante: BootstrapConfigurationException sin tocar BD")
    void passwordFaltante_lanzaBootstrapConfigurationException() {
        OperatorBootstrapRunner runner = new OperatorBootstrapRunner(true, repo, blocklist, argon2, null) {
            @Override String getEnvEmail() { return VALID_EMAIL; }
            @Override String getEnvDisplayName() { return VALID_NAME; }
            @Override char[] getEnvPassword() { return null; }
        };
        assertThatThrownBy(() -> runner.run(noArgs))
                .isInstanceOf(BootstrapConfigurationException.class);
        assertThat(repo.interacted).isFalse();
    }

    // =========================================================================
    // 3. Contrasena demasiado corta
    // =========================================================================

    @Test
    @DisplayName("Contrasena demasiado corta: BootstrapConfigurationException sin insertar")
    void contrasenaDemasiadoCorta_fallaSinInsertar() {
        var runner = runnerEnabled(VALID_EMAIL, VALID_NAME, "corta", blocklist);
        assertThatThrownBy(() -> runner.run(noArgs))
                .isInstanceOf(BootstrapConfigurationException.class);
        assertThat(repo.count()).isZero();
    }

    // =========================================================================
    // 4. Blocklist no disponible: fail-closed
    // =========================================================================

    @Test
    @DisplayName("Blocklist no disponible: BootstrapConfigurationException sin insertar")
    void blocklistNoDisponible_fallaCerradoSinInsertar() {
        FakeBlocklist unavailable = new FakeBlocklist(Set.of()) {
            @Override public boolean estaBloquada(String pw) {
                throw new PasswordBlocklistUnavailableException();
            }
        };
        var runner = runnerEnabled(VALID_EMAIL, VALID_NAME, VALID_PASSWORD, unavailable);
        assertThatThrownBy(() -> runner.run(noArgs))
                .isInstanceOf(BootstrapConfigurationException.class);
        assertThat(repo.count()).isZero();
    }

    // =========================================================================
    // 5. Bootstrap valido: crea exactamente un operador
    // =========================================================================

    @Test
    @DisplayName("Bootstrap valido: crea exactamente un operador con hash Argon2id")
    void bootstrapValido_creaUnOperador() throws Exception {
        var runner = runnerEnabled(VALID_EMAIL, VALID_NAME, VALID_PASSWORD, blocklist);
        runner.run(noArgs);
        assertThat(repo.count()).isEqualTo(1L);
        PlatformOperator op = repo.findByNormalizedEmail(VALID_EMAIL).orElseThrow();
        assertThat(op.displayName()).isEqualTo(VALID_NAME);
        assertThat(op.isActive()).isTrue();
        assertThat(op.authVersion()).isEqualTo(1L);
        // El hash debe ser un hash Argon2id valido
        assertThat(op.passwordHash()).startsWith("$argon2id$");
    }

    // =========================================================================
    // 6. Operador ya existente: no inserta otro
    // =========================================================================

    @Test
    @DisplayName("Operador ya existente: bootstrap no inserta ni altera registros")
    void operadorExistente_noInsertaOtro() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        repo.save(new PlatformOperator(UUID.randomUUID(), "prev@ts.com", "prev@ts.com",
                "Previo", "$argon2id$existing", true, 1L, 0L, now, now));

        var runner = runnerEnabled(VALID_EMAIL, VALID_NAME, VALID_PASSWORD, blocklist);
        runner.run(noArgs);

        assertThat(repo.count()).isEqualTo(1L);
        assertThat(repo.findByNormalizedEmail(VALID_EMAIL)).isEmpty();
    }

    // =========================================================================
    // 7. BootstrapConfigurationException mensaje seguro y fijo
    // =========================================================================

    @Test
    @DisplayName("BootstrapConfigurationException tiene mensaje seguro y fijo")
    void bootstrapConfigurationException_mensajeSeguro() {
        BootstrapConfigurationException ex = new BootstrapConfigurationException();
        assertThat(ex.getMessage()).contains("[OPERATOR_BOOTSTRAP_CONFIG_ERROR]");
        assertThat(ex.getMessage()).doesNotContain("@");
        assertThat(ex.getMessage()).doesNotContain("password");
        assertThat(ex.getMessage()).doesNotContain("hash");
    }

    // =========================================================================
    // 8. Fallos de persistencia en count y save
    // =========================================================================

    @Test
    @DisplayName("Fallo en repository.count(): runner termina con BootstrapConfigurationException sin causa ni detalles internos")
    void countFalla_runnerTerminaConBootstrapConfigurationExceptionSinCausa() {
        repo.throwOnCount = true;
        var runner = runnerEnabled(VALID_EMAIL, VALID_NAME, VALID_PASSWORD, blocklist);

        assertThatThrownBy(() -> runner.run(noArgs))
                .isInstanceOf(BootstrapConfigurationException.class)
                .hasMessage("[OPERATOR_BOOTSTRAP_CONFIG_ERROR] El bootstrap del operador de plataforma falló. Revisa la configuración de las variables de entorno requeridas.")
                .hasNoCause();
    }

    @Test
    @DisplayName("Fallo en repository.save(): runner termina con BootstrapConfigurationException sin causa ni detalles internos")
    void saveFalla_runnerTerminaConBootstrapConfigurationExceptionSinCausa() {
        repo.throwOnSave = true;
        var runner = runnerEnabled(VALID_EMAIL, VALID_NAME, VALID_PASSWORD, blocklist);

        assertThatThrownBy(() -> runner.run(noArgs))
                .isInstanceOf(BootstrapConfigurationException.class)
                .hasMessage("[OPERATOR_BOOTSTRAP_CONFIG_ERROR] El bootstrap del operador de plataforma falló. Revisa la configuración de las variables de entorno requeridas.")
                .hasNoCause();
    }

    // =========================================================================
    // Dobles de prueba
    // =========================================================================

    static class InMemoryOperatorRepository implements PlatformOperatorRepository {
        private final Map<String, PlatformOperator> store = new HashMap<>();
        boolean interacted = false;
        boolean throwOnCount = false;
        boolean throwOnSave = false;

        @Override
        public Optional<PlatformOperator> findByNormalizedEmail(String email) {
            interacted = true;
            return Optional.ofNullable(store.get(email));
        }
        @Override public Optional<PlatformOperator> findById(UUID id) {
            interacted = true;
            return store.values().stream().filter(op -> op.id().equals(id)).findFirst();
        }
        @Override public PlatformOperator save(PlatformOperator op) {
            interacted = true;
            if (throwOnSave) {
                throw new OperatorDataAccessException(new RuntimeException("SQL syntax error inserting platform_operators"));
            }
            store.put(op.normalizedEmail(), op);
            return op;
        }
        @Override public boolean updateAuthVersion(UUID id, long exp, long n) { return true; }
        @Override public boolean updateActiveStatus(UUID id, boolean a) { return true; }
        @Override public long count() {
            interacted = true;
            if (throwOnCount) {
                throw new OperatorDataAccessException(new RuntimeException("SQL connection timeout reading platform_operators"));
            }
            return store.size();
        }
    }

    static class FakeBlocklist implements PasswordBlocklist {
        private final Set<String> blocked;
        FakeBlocklist(Set<String> blocked) { this.blocked = Set.copyOf(blocked); }
        @Override public boolean estaBloquada(String pw) { return blocked.contains(pw); }
    }
}
