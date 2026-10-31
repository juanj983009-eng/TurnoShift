package com.turnoshift.operator.application;

import com.turnoshift.identity.application.PasswordVerifier;
import com.turnoshift.operator.domain.OperatorAuthenticationOutcome;
import com.turnoshift.operator.domain.OperatorAuthenticationResult;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.PlatformOperator;
import com.turnoshift.operator.domain.PlatformOperatorIdentity;
import com.turnoshift.operator.domain.PlatformOperatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: AuthenticateOperator — caso de uso de autenticacion del operador de plataforma")
class AuthenticateOperatorTest {

    static final String DECOY = "$argon2id$v=19$m=131072,t=3,p=1$decoy$decoyValue";

    StubOperatorRepository repo;
    TrackingVerifier verifier;
    AuthenticateOperator uc;

    @BeforeEach
    void setUp() {
        repo = new StubOperatorRepository();
        verifier = new TrackingVerifier();
        uc = new AuthenticateOperator(repo, verifier, DECOY);
    }

    PlatformOperator buildOperator(String email, String normEmail, String hash, boolean active) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new PlatformOperator(UUID.randomUUID(), email, normEmail,
                "Operador Principal", hash, active, 1L, 0L, now, now);
    }

    // =========================================================================
    // 1. Exito
    // =========================================================================

    @Test
    @DisplayName("Autenticacion valida retorna SUCCESS con PlatformOperatorIdentity correcta")
    void autenticacionValida_retornaSuccess() {
        PlatformOperator op = buildOperator("admin@ts.com", "admin@ts.com", "hash-real", true);
        repo.add("admin@ts.com", op);
        verifier.setMatch("hash-real", "clave-correcta-15chars");

        OperatorAuthenticationOutcome out = uc.execute("admin@ts.com", "clave-correcta-15chars".toCharArray());

        assertThat(out.result()).isEqualTo(OperatorAuthenticationResult.SUCCESS);
        assertThat(out.identity()).isNotNull();
        assertThat(out.identity().getName()).isEqualTo(op.id().toString());
        assertThat(out.identity().displayName()).isEqualTo("Operador Principal");
        assertThat(out.identity().authVersion()).isEqualTo(1L);
    }

    // =========================================================================
    // 2. Correo inexistente
    // =========================================================================

    @Test
    @DisplayName("Correo inexistente: INVALID_CREDENTIALS y hash senuelo consumido")
    void correoInexistente_falloGenericoYSenuelo() {
        OperatorAuthenticationOutcome out = uc.execute("noexiste@ts.com", "cualquier".toCharArray());

        assertThat(out.result()).isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
        assertThat(out.identity()).isNull();
        assertThat(verifier.lastHash).isEqualTo(DECOY);
    }

    // =========================================================================
    // 3. Contrasena erronea
    // =========================================================================

    @Test
    @DisplayName("Contrasena erronea: INVALID_CREDENTIALS y hash real consumido")
    void contrasenaErronea_falloGenericoHashReal() {
        PlatformOperator op = buildOperator("op@ts.com", "op@ts.com", "hash-real", true);
        repo.add("op@ts.com", op);

        OperatorAuthenticationOutcome out = uc.execute("op@ts.com", "equivocada".toCharArray());

        assertThat(out.result()).isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
        assertThat(verifier.lastHash).isEqualTo("hash-real");
    }

    // =========================================================================
    // 4. Cuenta inactiva
    // =========================================================================

    @Test
    @DisplayName("Cuenta inactiva: INVALID_CREDENTIALS y hash real consumido antes de verificar estado")
    void cuentaInactiva_falloGenericoHashReal() {
        PlatformOperator op = buildOperator("inac@ts.com", "inac@ts.com", "hash-inactive", false);
        repo.add("inac@ts.com", op);
        verifier.setMatch("hash-inactive", "clave-correcta-15chars");

        OperatorAuthenticationOutcome out = uc.execute("inac@ts.com", "clave-correcta-15chars".toCharArray());

        assertThat(out.result()).isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
        assertThat(verifier.lastHash).isEqualTo("hash-inactive");
    }

    // =========================================================================
    // 5. Hash corrupto (verifier throws)
    // =========================================================================

    @Test
    @DisplayName("Hash corrupto (verifier lanza excepcion): intenta verificacion con decoyHash antes de INVALID_CREDENTIALS")
    void hashCorrupto_fallaCerrado() {
        PlatformOperator op = buildOperator("corrupt@ts.com", "corrupt@ts.com", "bad-hash", true);
        repo.add("corrupt@ts.com", op);
        verifier.throwOnHash = "bad-hash";

        OperatorAuthenticationOutcome out = uc.execute("corrupt@ts.com", "cualquier".toCharArray());

        assertThat(out.result()).isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
        assertThat(verifier.invokedHashes).containsExactly("bad-hash", DECOY);
        assertThat(verifier.lastHash).isEqualTo(DECOY);
    }

    @Test
    @DisplayName("Cuenta inactiva con hash corrupto: intenta verificacion con decoyHash antes de INVALID_CREDENTIALS")
    void cuentaInactiva_hashCorrupto_consumeSenuelo() {
        PlatformOperator op = buildOperator("inact-corrupt@ts.com", "inact-corrupt@ts.com", "bad-hash", false);
        repo.add("inact-corrupt@ts.com", op);
        verifier.throwOnHash = "bad-hash";

        OperatorAuthenticationOutcome out = uc.execute("inact-corrupt@ts.com", "clave".toCharArray());

        assertThat(out.result()).isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
        assertThat(verifier.invokedHashes).containsExactly("bad-hash", DECOY);
        assertThat(verifier.lastHash).isEqualTo(DECOY);
    }

    // =========================================================================
    // 6. Fallo JDBC: propaga OperatorDataAccessException
    // =========================================================================

    @Test
    @DisplayName("Fallo JDBC: OperatorDataAccessException se propaga — NO es INVALID_CREDENTIALS")
    void falloJDBC_propagaExcepcionTecnica() {
        repo.throwOnFind = new OperatorDataAccessException();

        assertThatThrownBy(() -> uc.execute("x@ts.com", "cualquier".toCharArray()))
                .isInstanceOf(OperatorDataAccessException.class)
                .hasMessageContaining("[OPERATOR_DATA_ACCESS_ERROR]");
    }

    // =========================================================================
    // 7. toString de identity no expone datos sensibles
    // =========================================================================

    @Test
    @DisplayName("toString de PlatformOperatorIdentity no expone id, email ni hash")
    void toString_noExponeCredenciales() {
        UUID id = UUID.randomUUID();
        PlatformOperatorIdentity identity = new PlatformOperatorIdentity(id, "Admin Op", 1L);
        assertThat(identity.toString()).doesNotContain(id.toString());
        assertThat(identity.toString()).doesNotContain("@");
        assertThat(identity.toString()).doesNotContain("hash");
    }

    // =========================================================================
    // 8. Email nulo o en blanco: INVALID_CREDENTIALS sin consultar BD
    // =========================================================================

    @Test
    @DisplayName("Email nulo o en blanco: INVALID_CREDENTIALS sin tocar el repositorio")
    void emailNuloOBlanco_invalidCredentials_sinConsultaBD() {
        OperatorAuthenticationOutcome r1 = uc.execute(null, "p".toCharArray());
        OperatorAuthenticationOutcome r2 = uc.execute("  ", "p".toCharArray());

        assertThat(r1.result()).isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
        assertThat(r2.result()).isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
        assertThat(repo.queryCalled).isFalse();
    }

    // =========================================================================
    // 9. Password nulo o vacio: INVALID_CREDENTIALS
    // =========================================================================

    @Test
    @DisplayName("Password nulo o vacio: INVALID_CREDENTIALS")
    void passwordNuloOVacio_invalidCredentials() {
        assertThat(uc.execute("op@ts.com", null).result())
                .isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
        assertThat(uc.execute("op@ts.com", new char[0]).result())
                .isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
    }

    // =========================================================================
    // 10. Distintas causas de fallo son indistinguibles
    // =========================================================================

    @Test
    @DisplayName("Distintas causas de fallo producen el mismo INVALID_CREDENTIALS sin distinguir")
    void falloGenerico_noDiferenciaLasCausas() {
        // correo inexistente
        OperatorAuthenticationOutcome rA = uc.execute("noexiste@ts.com", "pass".toCharArray());

        // contrasena erronea
        PlatformOperator op = buildOperator("op@ts.com", "op@ts.com", "hash1", true);
        repo.add("op@ts.com", op);
        OperatorAuthenticationOutcome rB = uc.execute("op@ts.com", "wrong".toCharArray());

        // cuenta inactiva
        PlatformOperator inact = buildOperator("inact@ts.com", "inact@ts.com", "hash2", false);
        repo.add("inact@ts.com", inact);
        OperatorAuthenticationOutcome rC = uc.execute("inact@ts.com", "pass".toCharArray());

        assertThat(rA.result()).isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
        assertThat(rB.result()).isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
        assertThat(rC.result()).isEqualTo(OperatorAuthenticationResult.INVALID_CREDENTIALS);
    }

    // =========================================================================
    // Dobles de prueba
    // =========================================================================

    static class StubOperatorRepository implements PlatformOperatorRepository {
        private final Map<String, PlatformOperator> store = new HashMap<>();
        OperatorDataAccessException throwOnFind;
        boolean queryCalled = false;

        void add(String normEmail, PlatformOperator op) {
            store.put(normEmail, op);
        }

        @Override
        public Optional<PlatformOperator> findByNormalizedEmail(String email) {
            queryCalled = true;
            if (throwOnFind != null) throw throwOnFind;
            return Optional.ofNullable(store.get(email));
        }

        @Override public Optional<PlatformOperator> findById(UUID id) { return Optional.empty(); }
        @Override public PlatformOperator save(PlatformOperator op) { return op; }
        @Override public boolean updateAuthVersion(UUID id, long exp, long n) { return true; }
        @Override public boolean updateActiveStatus(UUID id, boolean a) { return true; }
        @Override public long count() { return store.size(); }
    }

    static class TrackingVerifier implements PasswordVerifier {
        final List<String> invokedHashes = new ArrayList<>();
        String lastHash;
        String throwOnHash;
        private final Map<String, String> matches = new HashMap<>();

        void setMatch(String hash, String password) {
            matches.put(hash, password);
        }

        @Override
        public boolean matches(String preparedPassword, String storedHash) {
            invokedHashes.add(storedHash);
            this.lastHash = storedHash;
            if (throwOnHash != null && throwOnHash.equals(storedHash)) {
                throw new IllegalArgumentException("simulated corrupted hash");
            }
            String expected = matches.get(storedHash);
            return expected != null && expected.equals(preparedPassword);
        }
    }
}
