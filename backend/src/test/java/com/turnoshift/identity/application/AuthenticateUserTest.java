package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.identity.domain.AuthenticationCandidate;
import com.turnoshift.identity.domain.AuthenticationDataAccessException;
import com.turnoshift.identity.domain.AuthenticationOutcome;
import com.turnoshift.identity.domain.AuthenticationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: AuthenticateUser — caso de uso de autenticación multi-tenant")
class AuthenticateUserTest {

    private static final String DECOY_HASH = "$argon2id$v=19$m=131072,t=3,p=1$decoySalt$decoyHashExpectedValue";

    private StubCandidateRepository repository;
    private TrackingPasswordVerifier verifier;
    private AuthenticateUser useCase;

    @BeforeEach
    void setUp() {
        repository = new StubCandidateRepository();
        verifier = new TrackingPasswordVerifier();
        useCase = new AuthenticateUser(repository, verifier, DECOY_HASH);
    }

    private AuthenticationCandidate crearCandidatoValido(String orgCode, String email, String passwordHash) {
        return new AuthenticationCandidate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                email,
                "Usuario Prueba",
                "ADMINISTRADOR",
                null,
                true,
                1L,
                "Empresa Prueba",
                "America/Lima",
                "ACTIVA",
                passwordHash
        );
    }

    // =========================================================================
    // 1. Éxito
    // =========================================================================

    @Test
    @DisplayName("Éxito: credenciales correctas y empresa activa retornan identidad autenticada completa")
    void exito_retornaIdentidadAutenticada() {
        String orgCode = "CAFE-DEMO";
        String email = "ana@cafedemo.example";
        String password = "clave-correcta-123";
        String hash = "hash-valido-ana";

        AuthenticationCandidate candidato = crearCandidatoValido(orgCode, email, hash);
        repository.registrar(orgCode, email, candidato);
        verifier.registrarCredencial(password, hash);

        AuthenticationOutcome outcome = useCase.execute(" cafe-demo ", " Ana@CafeDemo.Example ", password);

        assertThat(outcome.result()).isEqualTo(AuthenticationResult.SUCCESS);
        assertThat(outcome.identity()).isNotNull();

        AuthenticatedIdentity identity = outcome.identity();
        assertThat(identity.userId()).isEqualTo(candidato.userId());
        assertThat(identity.organizationId()).isEqualTo(candidato.organizationId());
        assertThat(identity.normalizedEmail()).isEqualTo(candidato.normalizedEmail());
        assertThat(identity.displayName()).isEqualTo(candidato.displayName());
        assertThat(identity.role()).isEqualTo("ADMINISTRADOR");
        assertThat(identity.employeeId()).isNull();
        assertThat(identity.authVersion()).isEqualTo(1L);
        assertThat(identity.organizationDisplayName()).isEqualTo("Empresa Prueba");
        assertThat(identity.organizationTimeZone()).isEqualTo("America/Lima");
    }

    // =========================================================================
    // 2. Empresa inexistente
    // =========================================================================

    @Test
    @DisplayName("Empresa inexistente: falla genérico y consume coste con hash señuelo")
    void empresaInexistente_fallaGenericoYConsumeCoste() {
        AuthenticationOutcome outcome = useCase.execute("NO-EXISTE", "user@test.com", "password123");

        assertThat(outcome.result()).isEqualTo(AuthenticationResult.INVALID_CREDENTIALS);
        assertThat(outcome.identity()).isNull();
        assertThat(verifier.lastStoredHash).isEqualTo(DECOY_HASH);
    }

    // =========================================================================
    // 3. Usuario inexistente
    // =========================================================================

    @Test
    @DisplayName("Usuario inexistente: falla genérico y consume coste con hash señuelo")
    void usuarioInexistente_fallaGenericoYConsumeCoste() {
        AuthenticationOutcome outcome = useCase.execute("ORG-EXISTE", "noexiste@test.com", "password123");

        assertThat(outcome.result()).isEqualTo(AuthenticationResult.INVALID_CREDENTIALS);
        assertThat(outcome.identity()).isNull();
        assertThat(verifier.lastStoredHash).isEqualTo(DECOY_HASH);
    }

    // =========================================================================
    // 4. Contraseña incorrecta
    // =========================================================================

    @Test
    @DisplayName("Contraseña incorrecta: falla genérico y consume coste con hash real")
    void contrasenaIncorrecta_fallaGenerico() {
        String orgCode = "ORG-TEST";
        String email = "user@test.com";
        String realHash = "hash-real-del-usuario";

        AuthenticationCandidate candidato = crearCandidatoValido(orgCode, email, realHash);
        repository.registrar(orgCode, email, candidato);

        AuthenticationOutcome outcome = useCase.execute(orgCode, email, "password-equivocada");

        assertThat(outcome.result()).isEqualTo(AuthenticationResult.INVALID_CREDENTIALS);
        assertThat(outcome.identity()).isNull();
        assertThat(verifier.lastStoredHash).isEqualTo(realHash);
    }

    // =========================================================================
    // 5. Hash NULL
    // =========================================================================

    @Test
    @DisplayName("Hash NULL: cuenta con contraseña pendiente falla genérico y consume coste con señuelo")
    void hashNull_fallaGenericoYConsumeCosteConSenuelo() {
        String orgCode = "ORG-TEST";
        String email = "pending@test.com";

        AuthenticationCandidate candidato = crearCandidatoValido(orgCode, email, null);
        repository.registrar(orgCode, email, candidato);

        AuthenticationOutcome outcome = useCase.execute(orgCode, email, "anyPassword123");

        assertThat(outcome.result()).isEqualTo(AuthenticationResult.INVALID_CREDENTIALS);
        assertThat(verifier.lastStoredHash).isEqualTo(DECOY_HASH);
    }

    // =========================================================================
    // 6. Usuario inactivo
    // =========================================================================

    @Test
    @DisplayName("Usuario inactivo: con hash real no omite el coste de verificación y falla genérico")
    void usuarioInactivo_fallaGenericoYConsumeCoste() {
        String orgCode = "ORG-TEST";
        String email = "inactive@test.com";
        String realHash = "hash-inactive-user";
        String password = "correctPassword123";

        AuthenticationCandidate candidato = new AuthenticationCandidate(
                UUID.randomUUID(), UUID.randomUUID(), email, "Usuario Inactivo",
                "COLABORADOR", null, false, 0L, "Org", "UTC", "ACTIVA", realHash
        );
        repository.registrar(orgCode, email, candidato);
        verifier.registrarCredencial(password, realHash);

        AuthenticationOutcome outcome = useCase.execute(orgCode, email, password);

        assertThat(outcome.result()).isEqualTo(AuthenticationResult.INVALID_CREDENTIALS);
        assertThat(verifier.lastStoredHash).isEqualTo(realHash);
    }

    // =========================================================================
    // 7 & 8. Empresa suspendida o cerrada
    // =========================================================================

    @Test
    @DisplayName("Empresa suspendida: credenciales correctas retornan ORGANIZATION_ACCESS_DENIED")
    void empresaSuspendida_retornaAccesoDenegado() {
        String orgCode = "ORG-SUSP";
        String email = "user@susp.com";
        String realHash = "hash-user-susp";
        String password = "passwordCorrecta";

        AuthenticationCandidate candidato = new AuthenticationCandidate(
                UUID.randomUUID(), UUID.randomUUID(), email, "User",
                "ADMINISTRADOR", null, true, 0L, "Org Susp", "UTC", "SUSPENDIDA", realHash
        );
        repository.registrar(orgCode, email, candidato);
        verifier.registrarCredencial(password, realHash);

        AuthenticationOutcome outcome = useCase.execute(orgCode, email, password);

        assertThat(outcome.result()).isEqualTo(AuthenticationResult.ORGANIZATION_ACCESS_DENIED);
        assertThat(outcome.identity()).isNull();
    }

    @Test
    @DisplayName("Empresa cerrada: credenciales correctas retornan ORGANIZATION_ACCESS_DENIED")
    void empresaCerrada_retornaAccesoDenegado() {
        String orgCode = "ORG-CERRADA";
        String email = "user@cerrada.com";
        String realHash = "hash-user-cerrada";
        String password = "passwordCorrecta";

        AuthenticationCandidate candidato = new AuthenticationCandidate(
                UUID.randomUUID(), UUID.randomUUID(), email, "User",
                "SUPERVISOR", null, true, 0L, "Org Cerrada", "UTC", "CERRADA", realHash
        );
        repository.registrar(orgCode, email, candidato);
        verifier.registrarCredencial(password, realHash);

        AuthenticationOutcome outcome = useCase.execute(orgCode, email, password);

        assertThat(outcome.result()).isEqualTo(AuthenticationResult.ORGANIZATION_ACCESS_DENIED);
        assertThat(outcome.identity()).isNull();
    }

    // =========================================================================
    // 9. Rol desconocido / dato corrupto falla cerrado
    // =========================================================================

    @Test
    @DisplayName("Rol desconocido o dato corrupto: falla cerrado con fallo genérico y coste consumido")
    void rolDesconocidoODatoCorrupto_fallaCerrado() {
        String orgCode = "ORG-CORRUPT";
        String email = "corrupt@test.com";
        String hash = "hash-corrupt";

        AuthenticationCandidate candidatoRolInvalido = new AuthenticationCandidate(
                UUID.randomUUID(), UUID.randomUUID(), email, "User",
                "ROL_INVENTADO", null, true, 0L, "Org", "UTC", "ACTIVA", hash
        );
        repository.registrar(orgCode, email, candidatoRolInvalido);

        AuthenticationOutcome outcome = useCase.execute(orgCode, email, "password");
        assertThat(outcome.result()).isEqualTo(AuthenticationResult.INVALID_CREDENTIALS);
        assertThat(verifier.callCount).isGreaterThan(0);

        AuthenticationCandidate candidatoEstadoInvalido = new AuthenticationCandidate(
                UUID.randomUUID(), UUID.randomUUID(), email, "User",
                "ADMINISTRADOR", null, true, 0L, "Org", "UTC", "ESTADO_DESCONOCIDO", hash
        );
        repository.registrar(orgCode, email, candidatoEstadoInvalido);
        verifier.registrarCredencial("password", hash);

        AuthenticationOutcome outcomeEstado = useCase.execute(orgCode, email, "password");
        assertThat(outcomeEstado.result()).isEqualTo(AuthenticationResult.INVALID_CREDENTIALS);
    }

    // =========================================================================
    // 10. Validación de entrada estructural
    // =========================================================================

    @Test
    @DisplayName("Validación de entrada: campos vacíos, inválidos o contraseña > 128 puntos de código")
    void validacionEntrada_rechazaEntradasInvalidas() {
        assertThat(useCase.execute("ORG-A", "a@b.com", "").result())
                .isEqualTo(AuthenticationResult.VALIDATION_FAILED);
        assertThat(useCase.execute("ORG-A", "a@b.com", null).result())
                .isEqualTo(AuthenticationResult.VALIDATION_FAILED);

        String password129 = "a".repeat(129);
        assertThat(useCase.execute("ORG-A", "a@b.com", password129).result())
                .isEqualTo(AuthenticationResult.VALIDATION_FAILED);

        String password128 = "a".repeat(128);
        assertThat(useCase.execute("ORG-A", "a@b.com", password128).result())
                .isNotEqualTo(AuthenticationResult.VALIDATION_FAILED);

        assertThat(useCase.execute("", "a@b.com", "pass").result())
                .isEqualTo(AuthenticationResult.VALIDATION_FAILED);
        assertThat(useCase.execute("A", "a@b.com", "pass").result())
                .isEqualTo(AuthenticationResult.VALIDATION_FAILED);
        assertThat(useCase.execute("A".repeat(33), "a@b.com", "pass").result())
                .isEqualTo(AuthenticationResult.VALIDATION_FAILED);
        assertThat(useCase.execute("ORG INVALID!", "a@b.com", "pass").result())
                .isEqualTo(AuthenticationResult.VALIDATION_FAILED);

        assertThat(useCase.execute("ORG-A", "", "pass").result())
                .isEqualTo(AuthenticationResult.VALIDATION_FAILED);
        assertThat(useCase.execute("ORG-A", "   ", "pass").result())
                .isEqualTo(AuthenticationResult.VALIDATION_FAILED);
    }

    // =========================================================================
    // 11. Contraseña con espacios no se recorta
    // =========================================================================

    @Test
    @DisplayName("Contraseña con espacios no se recorta: se entregan íntegros al verificador")
    void contrasenaConEspacios_noSeRecortan() {
        String orgCode = "ORG-TEST";
        String email = "spaces@test.com";
        String hash = "hash-spaces";
        String passwordConEspacios = "  secreto con espacios exteriores e interiores  ";

        AuthenticationCandidate candidato = crearCandidatoValido(orgCode, email, hash);
        repository.registrar(orgCode, email, candidato);

        useCase.execute(orgCode, email, passwordConEspacios);

        assertThat(verifier.lastPreparedPassword).isEqualTo(passwordConEspacios);
    }

    // =========================================================================
    // 12. Verificación señuelo cuando no hay hash
    // =========================================================================

    @Test
    @DisplayName("Verificación señuelo: se utiliza cuando el usuario no existe o su hash es nulo")
    void verificacionSenuelo_utilizadaCuandoNoHayHash() {
        useCase.execute("ORG-A", "desconocido@test.com", "password123");
        assertThat(verifier.lastStoredHash).isEqualTo(DECOY_HASH);

        String orgCode = "ORG-A";
        String email = "nullhash@test.com";
        repository.registrar(orgCode, email, crearCandidatoValido(orgCode, email, null));

        useCase.execute(orgCode, email, "password123");
        assertThat(verifier.lastStoredHash).isEqualTo(DECOY_HASH);
    }

    // =========================================================================
    // 13. El fallo genérico no diferencia las causas
    // =========================================================================

    @Test
    @DisplayName("Fallo genérico: las distintas causas de fallo producen el mismo resultado sin filtrar causas")
    void falloGenerico_noDiferenciaLasCausas() {
        String orgCode = "ORG-GEN";
        String email = "user@test.com";
        String hash = "hash-1";

        AuthenticationOutcome rA = useCase.execute(orgCode, "noexiste@test.com", "pass");

        repository.registrar(orgCode, email, crearCandidatoValido(orgCode, email, hash));
        AuthenticationOutcome rB = useCase.execute(orgCode, email, "wrongPass");

        repository.registrar(orgCode, "inactive@test.com", new AuthenticationCandidate(
                UUID.randomUUID(), UUID.randomUUID(), "inactive@test.com", "User",
                "ADMINISTRADOR", null, false, 0L, "Org", "UTC", "ACTIVA", hash
        ));
        AuthenticationOutcome rC = useCase.execute(orgCode, "inactive@test.com", "pass");

        assertThat(rA.result()).isEqualTo(AuthenticationResult.INVALID_CREDENTIALS);
        assertThat(rB.result()).isEqualTo(AuthenticationResult.INVALID_CREDENTIALS);
        assertThat(rC.result()).isEqualTo(AuthenticationResult.INVALID_CREDENTIALS);
        assertThat(rA.validationMessage()).isNull();
        assertThat(rB.validationMessage()).isNull();
        assertThat(rC.validationMessage()).isNull();
    }

    // =========================================================================
    // 14. Ningún toString expone contraseña o passwordHash
    // =========================================================================

    @Test
    @DisplayName("Seguridad: ningún toString expone contraseña o hash")
    void ningunToStringExponeContrasenaOPasswordHash() {
        String secretHash = "$argon2id$v=19$m=131072,t=3,p=1$secret$secretHashValue123";
        AuthenticationCandidate candidate = crearCandidatoValido("ORG", "u@test.com", secretHash);

        assertThat(candidate.toString()).doesNotContain("secretHashValue123");
        assertThat(candidate.toString()).contains("[REDACTED]");

        AuthenticationOutcome outcomeSuccess = AuthenticationOutcome.success(new AuthenticatedIdentity(
                UUID.randomUUID(), UUID.randomUUID(), "u@test.com", "User", "ADMINISTRADOR",
                null, 1L, "Org", "UTC"
        ));
        assertThat(outcomeSuccess.toString()).doesNotContain(secretHash);
        assertThat(AuthenticationOutcome.invalidCredentials().toString()).doesNotContain(secretHash);
    }

    // =========================================================================
    // 15. Indisponibilidad de BD: se propaga, no se convierte en INVALID_CREDENTIALS
    // =========================================================================

    @Test
    @DisplayName("Indisponibilidad de BD: AuthenticationDataAccessException se propaga — NO es INVALID_CREDENTIALS")
    void indisponibilidadBD_propagaExcepcionTecnica_noEsInvalidCredentials() {
        repository.configurarParaLanzarExcepcion(
                new AuthenticationDataAccessException(
                        "El servicio de autenticación no está disponible temporalmente",
                        new RuntimeException("simulated DB down")));

        assertThatThrownBy(() -> useCase.execute("ORG-TEST", "user@test.com", "password123"))
                .isInstanceOf(AuthenticationDataAccessException.class)
                .hasMessageNotContaining("user@test.com")
                .hasMessageNotContaining("password")
                .hasMessageNotContaining("hash");
    }

    @Test
    @DisplayName("Indisponibilidad de BD: el mensaje de la excepción no expone credenciales ni SQL")
    void indisponibilidadBD_mensajeSeguro_sinCredencialesNiSQL() {
        String mensajeSeguro = "El servicio de autenticación no está disponible temporalmente";
        repository.configurarParaLanzarExcepcion(
                new AuthenticationDataAccessException(mensajeSeguro, new RuntimeException("simulated")));

        assertThatThrownBy(() -> useCase.execute("ORG-TEST", "user@test.com", "anyPass"))
                .isInstanceOf(AuthenticationDataAccessException.class)
                .hasMessage(mensajeSeguro);
    }

    // =========================================================================
    // Clases auxiliares para tests (dobles rápidos en memoria)
    // =========================================================================

    private static class StubCandidateRepository implements AuthenticationCandidateRepository {
        private final Map<String, AuthenticationCandidate> candidates = new HashMap<>();
        private AuthenticationDataAccessException exceptionToThrow = null;

        void registrar(String orgCode, String email, AuthenticationCandidate candidate) {
            String key = orgCode.toUpperCase() + "::" + email.toLowerCase();
            candidates.put(key, candidate);
        }

        void configurarParaLanzarExcepcion(AuthenticationDataAccessException ex) {
            this.exceptionToThrow = ex;
        }

        @Override
        public Optional<AuthenticationCandidate> findByOrgCodeAndEmail(
                String normalizedOrgCode, String normalizedEmail) {
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            String key = normalizedOrgCode + "::" + normalizedEmail;
            return Optional.ofNullable(candidates.get(key));
        }
    }

    private static class TrackingPasswordVerifier implements PasswordVerifier {
        String lastPreparedPassword;
        String lastStoredHash;
        int callCount = 0;
        private final Map<String, String> credencialesValidas = new HashMap<>();

        void registrarCredencial(String password, String hash) {
            credencialesValidas.put(hash, password);
        }

        @Override
        public boolean matches(String preparedPassword, String storedHash) {
            this.lastPreparedPassword = preparedPassword;
            this.lastStoredHash = storedHash;
            this.callCount++;
            String expectedPass = credencialesValidas.get(storedHash);
            return expectedPass != null && expectedPass.equals(preparedPassword);
        }
    }
}
