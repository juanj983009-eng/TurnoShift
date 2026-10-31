package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.PasswordResetTokenRepository;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.SecureTokenGenerator;
import com.turnoshift.identity.domain.TokenConsumptionResult;
import com.turnoshift.identity.domain.TokenDataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Unit: ConsumeResetToken — consumo atómico y condicional de token")
class ConsumeResetTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private PasswordResetTokenRepository repository;
    private SecureTokenGenerator tokenGenerator;
    private ConsumeResetToken consumeResetToken;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private String validRawToken;
    private byte[] validTokenHash;

    @BeforeEach
    void setUp() {
        repository = mock(PasswordResetTokenRepository.class);
        tokenGenerator = new SecureTokenGenerator();
        consumeResetToken = new ConsumeResetToken(repository, tokenGenerator, fixedClock);

        validRawToken = tokenGenerator.generateRawToken();
        validTokenHash = tokenGenerator.computeSha256(validRawToken);
    }

    @Test
    @DisplayName("Consumo exitoso atómico devuelve Success con identificadores de usuario y empresa")
    void consumoExitoso_devuelveSuccess() {
        when(repository.consumeToken(validTokenHash, ResetTokenPurpose.INITIAL_SETUP, NOW))
                .thenReturn(Optional.of(new PasswordResetTokenRepository.ConsumedTokenInfo(orgId, userId, ResetTokenPurpose.INITIAL_SETUP)));

        TokenConsumptionResult result = consumeResetToken.consume(validRawToken);

        assertThat(result).isInstanceOf(TokenConsumptionResult.Success.class);
        TokenConsumptionResult.Success success = (TokenConsumptionResult.Success) result;
        assertThat(success.organizationId()).isEqualTo(orgId);
        assertThat(success.userId()).isEqualTo(userId);
        assertThat(success.purpose()).isEqualTo(ResetTokenPurpose.INITIAL_SETUP);

        verify(repository).consumeToken(eq(validTokenHash), eq(ResetTokenPurpose.INITIAL_SETUP), eq(NOW));
    }

    @Test
    @DisplayName("Consumo fallido (ya consumido, revocado, vencido o inexistente) devuelve Failure genérico sin datos")
    void consumoFallido_devuelveFailureSinDatos() {
        when(repository.consumeToken(validTokenHash, ResetTokenPurpose.INITIAL_SETUP, NOW))
                .thenReturn(Optional.empty());

        TokenConsumptionResult result = consumeResetToken.consume(validRawToken);

        assertThat(result).isInstanceOf(TokenConsumptionResult.Failure.class);
        // Verifica que Failure no tiene getters ni datos sensibles
        assertThat(result.getClass().getDeclaredFields()).isEmpty();
    }

    @Test
    @DisplayName("Token malformado devuelve Failure sin consultar el repositorio")
    void tokenMalformado_devuelveFailureSinConsultarBD() {
        assertThat(consumeResetToken.consume(null)).isInstanceOf(TokenConsumptionResult.Failure.class);
        assertThat(consumeResetToken.consume("")).isInstanceOf(TokenConsumptionResult.Failure.class);
        assertThat(consumeResetToken.consume("   ")).isInstanceOf(TokenConsumptionResult.Failure.class);
        assertThat(consumeResetToken.consume("corto")).isInstanceOf(TokenConsumptionResult.Failure.class);
        assertThat(consumeResetToken.consume("a".repeat(44))).isInstanceOf(TokenConsumptionResult.Failure.class);

        verify(repository, never()).consumeToken(any(), any(), any());
    }

    @Test
    @DisplayName("Fallo en repositorio propaga TokenDataAccessException")
    void falloRepositorio_propagaTokenDataAccessException() {
        when(repository.consumeToken(any(), any(), any()))
                .thenThrow(new TokenDataAccessException());

        assertThatThrownBy(() -> consumeResetToken.consume(validRawToken))
                .isInstanceOf(TokenDataAccessException.class);
    }
}
