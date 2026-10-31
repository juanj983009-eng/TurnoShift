package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.AppUserPasswordRepository;
import com.turnoshift.identity.domain.PasswordBlocklist;
import com.turnoshift.identity.domain.PasswordBlocklistUnavailableException;
import com.turnoshift.identity.domain.PasswordResetTokenRepository;
import com.turnoshift.identity.domain.PasswordSetupDataAccessException;
import com.turnoshift.identity.domain.PasswordSetupResult;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.TokenConsumptionResult;
import com.turnoshift.identity.domain.ViolacionPolitica;
import com.turnoshift.identity.infrastructure.security.Argon2PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Unit: CompleteInitialPasswordSetup — orquestación de primera contraseña e invariantes de seguridad")
class CompleteInitialPasswordSetupTest {

    private PasswordBlocklist blocklist;
    private Argon2PasswordService passwordService;
    private ConsumeResetToken consumeResetToken;
    private AppUserPasswordRepository userPasswordRepository;
    private PasswordResetTokenRepository tokenRepository;
    private TransactionalInitialPasswordSetupApplier transactionApplier;
    private CompleteInitialPasswordSetup useCase;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC);
    private final String validToken = "A".repeat(43);
    private final String validPassword = "MiClaveSeguraValida12345#";
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        blocklist = mock(PasswordBlocklist.class);
        passwordService = mock(Argon2PasswordService.class);
        consumeResetToken = mock(ConsumeResetToken.class);
        userPasswordRepository = mock(AppUserPasswordRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);

        transactionApplier = new TransactionalInitialPasswordSetupApplier(
                consumeResetToken,
                userPasswordRepository,
                tokenRepository
        );

        useCase = new CompleteInitialPasswordSetup(
                blocklist,
                passwordService,
                transactionApplier,
                clock
        );

        when(blocklist.estaBloquada(any())).thenReturn(false);
        when(passwordService.codificar(any())).thenReturn("$argon2id$v=19$dummyhash");
    }

    @Test
    @DisplayName("Token nulo, vacío o con longitud inválida devuelve InvalidToken sin validar contraseña ni tocar DB")
    void tokenInvalido_devuelveInvalidToken_sinValidarContrasenaNiTocarDB() {
        assertThat(useCase.complete(null, validPassword)).isInstanceOf(PasswordSetupResult.InvalidToken.class);
        assertThat(useCase.complete("", validPassword)).isInstanceOf(PasswordSetupResult.InvalidToken.class);
        assertThat(useCase.complete("   ", validPassword)).isInstanceOf(PasswordSetupResult.InvalidToken.class);
        assertThat(useCase.complete("token_corto", validPassword)).isInstanceOf(PasswordSetupResult.InvalidToken.class);
        assertThat(useCase.complete("A".repeat(44), validPassword)).isInstanceOf(PasswordSetupResult.InvalidToken.class);

        verify(blocklist, never()).estaBloquada(any());
        verify(consumeResetToken, never()).consume(any());
        verify(userPasswordRepository, never()).setInitialPassword(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Contraseña vacía o nula devuelve PolicyViolation sin tocar base de datos")
    void contrasenaVacia_devuelvePolicyViolation_sinTocarDB() {
        PasswordSetupResult resultNull = useCase.complete(validToken, null);
        assertThat(resultNull).isInstanceOf(PasswordSetupResult.PolicyViolation.class);
        PasswordSetupResult.PolicyViolation violationNull = (PasswordSetupResult.PolicyViolation) resultNull;
        assertThat(violationNull.violations()).containsExactly(ViolacionPolitica.CONTRASENA_VACIA);

        PasswordSetupResult resultVacia = useCase.complete(validToken, "");
        assertThat(resultVacia).isInstanceOf(PasswordSetupResult.PolicyViolation.class);
        PasswordSetupResult.PolicyViolation violationVacia = (PasswordSetupResult.PolicyViolation) resultVacia;
        assertThat(violationVacia.violations()).containsExactly(ViolacionPolitica.CONTRASENA_VACIA);

        verify(consumeResetToken, never()).consume(any());
        verify(userPasswordRepository, never()).setInitialPassword(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Contraseña con menos de 15 puntos de código devuelve PolicyViolation DEMASIADO_CORTA")
    void contrasenaCorta_devuelvePolicyViolation_sinTocarDB() {
        PasswordSetupResult result = useCase.complete(validToken, "corta123");

        assertThat(result).isInstanceOf(PasswordSetupResult.PolicyViolation.class);
        PasswordSetupResult.PolicyViolation violation = (PasswordSetupResult.PolicyViolation) result;
        assertThat(violation.violations()).containsExactly(ViolacionPolitica.DEMASIADO_CORTA);

        verify(blocklist, never()).estaBloquada(any());
        verify(consumeResetToken, never()).consume(any());
    }

    @Test
    @DisplayName("Contraseña con más de 128 puntos de código devuelve PolicyViolation DEMASIADO_LARGA")
    void contrasenaLarga_devuelvePolicyViolation_sinTocarDB() {
        PasswordSetupResult result = useCase.complete(validToken, "A".repeat(129));

        assertThat(result).isInstanceOf(PasswordSetupResult.PolicyViolation.class);
        PasswordSetupResult.PolicyViolation violation = (PasswordSetupResult.PolicyViolation) result;
        assertThat(violation.violations()).containsExactly(ViolacionPolitica.DEMASIADO_LARGA);

        verify(blocklist, never()).estaBloquada(any());
        verify(consumeResetToken, never()).consume(any());
    }

    @Test
    @DisplayName("Contraseña comprometida en blocklist devuelve PolicyViolation CONTRASENA_BLOQUEADA sin tocar DB")
    void contrasenaBloqueada_devuelvePolicyViolation_sinTocarDB() {
        when(blocklist.estaBloquada(any())).thenReturn(true);

        PasswordSetupResult result = useCase.complete(validToken, validPassword);

        assertThat(result).isInstanceOf(PasswordSetupResult.PolicyViolation.class);
        PasswordSetupResult.PolicyViolation violation = (PasswordSetupResult.PolicyViolation) result;
        assertThat(violation.violations()).containsExactly(ViolacionPolitica.CONTRASENA_BLOQUEADA);

        verify(consumeResetToken, never()).consume(any());
        verify(userPasswordRepository, never()).setInitialPassword(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Blocklist no disponible propaga PasswordBlocklistUnavailableException sin tocar base de datos")
    void blocklistUnavailable_propagaPasswordBlocklistUnavailableException_sinTocarDB() {
        when(blocklist.estaBloquada(any())).thenThrow(new PasswordBlocklistUnavailableException());

        assertThatThrownBy(() -> useCase.complete(validToken, validPassword))
                .isInstanceOf(PasswordBlocklistUnavailableException.class);

        verify(consumeResetToken, never()).consume(any());
        verify(userPasswordRepository, never()).setInitialPassword(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Consumo de token fallido devuelve InvalidToken sin intentar actualizar app_users")
    void consumoTokenFalla_devuelveInvalidToken_sinModificarAppUsers() {
        when(consumeResetToken.consume(validToken)).thenReturn(new TokenConsumptionResult.Failure());

        PasswordSetupResult result = useCase.complete(validToken, validPassword);

        assertThat(result).isInstanceOf(PasswordSetupResult.InvalidToken.class);
        verify(consumeResetToken).consume(validToken);
        verify(userPasswordRepository, never()).setInitialPassword(any(), any(), any(), any());
        verify(tokenRepository, never()).revokeUnconsumedTokens(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Flujo exitoso: codifica con Argon2, persiste hash y auth_version, revoca tokens restantes y devuelve Success")
    void flujoExitoso_codificaArgon2_persiste_revocaTokensRestantes_devuelveSuccess() {
        when(consumeResetToken.consume(validToken)).thenReturn(
                new TokenConsumptionResult.Success(orgId, userId, ResetTokenPurpose.INITIAL_SETUP)
        );
        when(userPasswordRepository.setInitialPassword(eq(orgId), eq(userId), eq("$argon2id$v=19$dummyhash"), any()))
                .thenReturn(true);

        PasswordSetupResult result = useCase.complete(validToken, validPassword);

        assertThat(result).isInstanceOf(PasswordSetupResult.Success.class);
        verify(passwordService).codificar(validPassword);
        verify(consumeResetToken).consume(validToken);
        verify(userPasswordRepository).setInitialPassword(orgId, userId, "$argon2id$v=19$dummyhash", clock.instant());
        verify(tokenRepository).revokeUnconsumedTokens(orgId, userId, ResetTokenPurpose.INITIAL_SETUP, clock.instant());
    }

    @Test
    @DisplayName("Fallo al actualizar app_users fuerza rollback (0 filas afectadas) y el caso de uso devuelve InvalidToken")
    void actualizacionAppUsersFalla_devuelveInvalidToken() {
        when(consumeResetToken.consume(validToken)).thenReturn(
                new TokenConsumptionResult.Success(orgId, userId, ResetTokenPurpose.INITIAL_SETUP)
        );
        when(userPasswordRepository.setInitialPassword(any(), any(), any(), any()))
                .thenReturn(false);

        PasswordSetupResult result = useCase.complete(validToken, validPassword);

        assertThat(result).isInstanceOf(PasswordSetupResult.InvalidToken.class);
        verify(consumeResetToken).consume(validToken);
        verify(userPasswordRepository).setInitialPassword(any(), any(), any(), any());
        // No debe revocar tokens si falló la actualización del usuario
        verify(tokenRepository, never()).revokeUnconsumedTokens(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Error técnico de JDBC propaga PasswordSetupDataAccessException")
    void errorJdbc_propagaPasswordSetupDataAccessException() {
        when(consumeResetToken.consume(validToken)).thenReturn(
                new TokenConsumptionResult.Success(orgId, userId, ResetTokenPurpose.INITIAL_SETUP)
        );
        when(userPasswordRepository.setInitialPassword(any(), any(), any(), any()))
                .thenThrow(new PasswordSetupDataAccessException());

        assertThatThrownBy(() -> useCase.complete(validToken, validPassword))
                .isInstanceOf(PasswordSetupDataAccessException.class)
                .hasMessageContaining("[IDENTITY_PASSWORD_SETUP_DATA_ACCESS_ERROR]");
    }

    @Test
    @DisplayName("Resultados de PasswordSetupResult y excepciones redactan secretos en toString()")
    void toString_noExponeSecretos() {
        PasswordSetupResult success = new PasswordSetupResult.Success();
        PasswordSetupResult invalid = new PasswordSetupResult.InvalidToken();
        PasswordSetupResult policy = new PasswordSetupResult.PolicyViolation(
                java.util.List.of(ViolacionPolitica.CONTRASENA_BLOQUEADA)
        );

        assertThat(success.toString()).isEqualTo("PasswordSetupResult.Success");
        assertThat(invalid.toString()).isEqualTo("PasswordSetupResult.InvalidToken");
        assertThat(policy.toString()).doesNotContain("password", "token", "hash");
    }
}
