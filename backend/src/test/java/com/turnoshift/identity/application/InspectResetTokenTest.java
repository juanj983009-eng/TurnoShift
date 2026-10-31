package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.PasswordResetTokenRepository;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.SecureTokenGenerator;
import com.turnoshift.identity.domain.TokenDataAccessException;
import com.turnoshift.identity.domain.TokenInspectionCandidate;
import com.turnoshift.identity.domain.TokenInspectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Unit: InspectResetToken — inspección de solo lectura del token de primer acceso")
class InspectResetTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private PasswordResetTokenRepository repository;
    private SecureTokenGenerator tokenGenerator;
    private InspectResetToken inspectResetToken;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();
    private String validRawToken;
    private byte[] validTokenHash;

    @BeforeEach
    void setUp() {
        repository = mock(PasswordResetTokenRepository.class);
        tokenGenerator = new SecureTokenGenerator();
        inspectResetToken = new InspectResetToken(repository, tokenGenerator, fixedClock);

        validRawToken = tokenGenerator.generateRawToken();
        validTokenHash = tokenGenerator.computeSha256(validRawToken);
    }

    private TokenInspectionCandidate createCandidate(
            Instant expiresAt,
            Instant consumedAt,
            Instant revokedAt,
            boolean isUserActive,
            boolean hasPasswordSet,
            String orgStatus,
            ResetTokenPurpose purpose
    ) {
        return new TokenInspectionCandidate(
                tokenId,
                orgId,
                userId,
                purpose,
                expiresAt,
                consumedAt,
                revokedAt,
                isUserActive,
                hasPasswordSet,
                orgStatus
        );
    }

    @Test
    @DisplayName("Token válido y activo devuelve resultado válido con propósito INITIAL_SETUP")
    void tokenValidoYActivo_devuelveValid() {
        when(repository.findCandidateForInspection(validTokenHash))
                .thenReturn(Optional.of(createCandidate(
                        NOW.plus(Duration.ofMinutes(15)), null, null, true, false, "ACTIVA", ResetTokenPurpose.INITIAL_SETUP
                )));

        TokenInspectionResult result = inspectResetToken.inspect(validRawToken);

        assertThat(result).isInstanceOf(TokenInspectionResult.Valid.class);
        TokenInspectionResult.Valid valid = (TokenInspectionResult.Valid) result;
        assertThat(valid.purpose()).isEqualTo(ResetTokenPurpose.INITIAL_SETUP);
        verify(repository).findCandidateForInspection(validTokenHash);
    }

    @Test
    @DisplayName("Token inexistente en repositorio devuelve Invalid")
    void tokenInexistente_devuelveInvalid() {
        when(repository.findCandidateForInspection(validTokenHash))
                .thenReturn(Optional.empty());

        TokenInspectionResult result = inspectResetToken.inspect(validRawToken);

        assertThat(result).isInstanceOf(TokenInspectionResult.Invalid.class);
    }

    @Test
    @DisplayName("Token malformado (null, vacío o tamaño incorrecto) devuelve Invalid sin consultar BD")
    void tokenMalformado_devuelveInvalidSinConsultarBD() {
        assertThat(inspectResetToken.inspect(null)).isInstanceOf(TokenInspectionResult.Invalid.class);
        assertThat(inspectResetToken.inspect("")).isInstanceOf(TokenInspectionResult.Invalid.class);
        assertThat(inspectResetToken.inspect("   ")).isInstanceOf(TokenInspectionResult.Invalid.class);
        assertThat(inspectResetToken.inspect("token-demasiado-corto")).isInstanceOf(TokenInspectionResult.Invalid.class);
        assertThat(inspectResetToken.inspect("a".repeat(42))).isInstanceOf(TokenInspectionResult.Invalid.class);
        assertThat(inspectResetToken.inspect("a".repeat(44))).isInstanceOf(TokenInspectionResult.Invalid.class);
    }

    @Test
    @DisplayName("Token revocado devuelve Invalid")
    void tokenRevocado_devuelveInvalid() {
        when(repository.findCandidateForInspection(validTokenHash))
                .thenReturn(Optional.of(createCandidate(
                        NOW.plus(Duration.ofMinutes(15)), null, NOW.minusSeconds(60), true, false, "ACTIVA", ResetTokenPurpose.INITIAL_SETUP
                )));

        assertThat(inspectResetToken.inspect(validRawToken)).isInstanceOf(TokenInspectionResult.Invalid.class);
    }

    @Test
    @DisplayName("Token consumido devuelve Invalid")
    void tokenConsumido_devuelveInvalid() {
        when(repository.findCandidateForInspection(validTokenHash))
                .thenReturn(Optional.of(createCandidate(
                        NOW.plus(Duration.ofMinutes(15)), NOW.minusSeconds(60), null, true, false, "ACTIVA", ResetTokenPurpose.INITIAL_SETUP
                )));

        assertThat(inspectResetToken.inspect(validRawToken)).isInstanceOf(TokenInspectionResult.Invalid.class);
    }

    @Test
    @DisplayName("Token exactamente en expires_at devuelve Invalid (expirado)")
    void tokenExactamenteEnExpiresAt_devuelveInvalid() {
        when(repository.findCandidateForInspection(validTokenHash))
                .thenReturn(Optional.of(createCandidate(
                        NOW, null, null, true, false, "ACTIVA", ResetTokenPurpose.INITIAL_SETUP
                )));

        assertThat(inspectResetToken.inspect(validRawToken)).isInstanceOf(TokenInspectionResult.Invalid.class);
    }

    @Test
    @DisplayName("Token posterior a expires_at devuelve Invalid (vencido)")
    void tokenPosteriorAExpiresAt_devuelveInvalid() {
        when(repository.findCandidateForInspection(validTokenHash))
                .thenReturn(Optional.of(createCandidate(
                        NOW.minusSeconds(1), null, null, true, false, "ACTIVA", ResetTokenPurpose.INITIAL_SETUP
                )));

        assertThat(inspectResetToken.inspect(validRawToken)).isInstanceOf(TokenInspectionResult.Invalid.class);
    }

    @Test
    @DisplayName("Token de usuario desactivado devuelve Invalid")
    void tokenUsuarioDesactivado_devuelveInvalid() {
        when(repository.findCandidateForInspection(validTokenHash))
                .thenReturn(Optional.of(createCandidate(
                        NOW.plus(Duration.ofMinutes(15)), null, null, false, false, "ACTIVA", ResetTokenPurpose.INITIAL_SETUP
                )));

        assertThat(inspectResetToken.inspect(validRawToken)).isInstanceOf(TokenInspectionResult.Invalid.class);
    }

    @Test
    @DisplayName("Token de organización no activa devuelve Invalid")
    void tokenOrganizacionNoActiva_devuelveInvalid() {
        when(repository.findCandidateForInspection(validTokenHash))
                .thenReturn(Optional.of(createCandidate(
                        NOW.plus(Duration.ofMinutes(15)), null, null, true, false, "SUSPENDIDA", ResetTokenPurpose.INITIAL_SETUP
                )));

        assertThat(inspectResetToken.inspect(validRawToken)).isInstanceOf(TokenInspectionResult.Invalid.class);
    }

    @Test
    @DisplayName("Token cuando el usuario ya configuró contraseña devuelve Invalid")
    void tokenUsuarioConPassword_devuelveInvalid() {
        when(repository.findCandidateForInspection(validTokenHash))
                .thenReturn(Optional.of(createCandidate(
                        NOW.plus(Duration.ofMinutes(15)), null, null, true, true, "ACTIVA", ResetTokenPurpose.INITIAL_SETUP
                )));

        assertThat(inspectResetToken.inspect(validRawToken)).isInstanceOf(TokenInspectionResult.Invalid.class);
    }

    @Test
    @DisplayName("Token con propósito PASSWORD_RESET devuelve Invalid en este subincremento")
    void tokenPasswordReset_devuelveInvalid() {
        when(repository.findCandidateForInspection(validTokenHash))
                .thenReturn(Optional.of(createCandidate(
                        NOW.plus(Duration.ofMinutes(15)), null, null, true, false, "ACTIVA", ResetTokenPurpose.PASSWORD_RESET
                )));

        assertThat(inspectResetToken.inspect(validRawToken)).isInstanceOf(TokenInspectionResult.Invalid.class);
    }

    @Test
    @DisplayName("Fallo en repositorio propaga TokenDataAccessException")
    void falloRepositorio_propagaTokenDataAccessException() {
        when(repository.findCandidateForInspection(any()))
                .thenThrow(new TokenDataAccessException());

        assertThatThrownBy(() -> inspectResetToken.inspect(validRawToken))
                .isInstanceOf(TokenDataAccessException.class);
    }
}
