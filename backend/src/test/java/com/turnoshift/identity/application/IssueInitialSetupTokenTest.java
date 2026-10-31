package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.InitialSetupTokenEmission;
import com.turnoshift.identity.domain.PasswordResetTokenRepository;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.SecureTokenGenerator;
import com.turnoshift.identity.domain.TokenDataAccessException;
import com.turnoshift.identity.domain.UserSetupEligibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
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

@DisplayName("Unit: IssueInitialSetupToken — emisión y revocación de tokens de primer acceso")
class IssueInitialSetupTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private PasswordResetTokenRepository repository;
    private SecureTokenGenerator tokenGenerator;
    private IssueInitialSetupToken issueInitialSetupToken;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(PasswordResetTokenRepository.class);
        tokenGenerator = new SecureTokenGenerator();
        issueInitialSetupToken = new IssueInitialSetupToken(repository, tokenGenerator, fixedClock);
    }

    @Test
    @DisplayName("Emisión exitosa para usuario apto: revoca previos, guarda hash e issue token de 30m")
    void emisionExitosa_paraUsuarioApto() {
        when(repository.findEligibilityForEmission(orgId, userId))
                .thenReturn(Optional.of(new UserSetupEligibility(
                        orgId, userId, "ACTIVA", true, false
                )));

        Optional<InitialSetupTokenEmission> result = issueInitialSetupToken.issue(orgId, userId);

        assertThat(result).isPresent();
        InitialSetupTokenEmission emission = result.get();
        assertThat(emission.organizationId()).isEqualTo(orgId);
        assertThat(emission.userId()).isEqualTo(userId);
        assertThat(emission.tokenId()).isNotNull();
        assertThat(emission.rawToken()).hasSize(43);
        assertThat(emission.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));

        // toString() nunca expone el secreto ni identificadores sensibles
        assertThat(emission.toString()).doesNotContain(emission.rawToken());
        assertThat(emission.toString()).doesNotContain(emission.tokenId().toString());
        assertThat(emission.toString()).doesNotContain(emission.organizationId().toString());
        assertThat(emission.toString()).doesNotContain(emission.userId().toString());
        assertThat(emission.toString()).contains("[PROTECTED]");

        // Verifica revocación de tokens no consumidos y no revocados previos
        verify(repository).revokeUnconsumedTokens(eq(orgId), eq(userId), eq(ResetTokenPurpose.INITIAL_SETUP), eq(NOW));

        // Verifica inserción de nuevo token con hash SHA-256 de 32 bytes (nunca el rawToken)
        ArgumentCaptor<byte[]> hashCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(repository).insertToken(
                eq(emission.tokenId()),
                eq(orgId),
                eq(userId),
                hashCaptor.capture(),
                eq(ResetTokenPurpose.INITIAL_SETUP),
                eq(NOW.plus(Duration.ofMinutes(30))),
                eq(NOW)
        );

        byte[] savedHash = hashCaptor.getValue();
        assertThat(savedHash).hasSize(32);
        assertThat(savedHash).isEqualTo(tokenGenerator.computeSha256(emission.rawToken()));
    }

    @Test
    @DisplayName("Rechaza emisión si usuario ya tiene contraseña configurada (password_hash no nulo)")
    void rechazaEmision_siUsuarioYaTieneContrasena() {
        when(repository.findEligibilityForEmission(orgId, userId))
                .thenReturn(Optional.of(new UserSetupEligibility(
                        orgId, userId, "ACTIVA", true, true // hasPassword = true
                )));

        Optional<InitialSetupTokenEmission> result = issueInitialSetupToken.issue(orgId, userId);

        assertThat(result).isEmpty();
        verify(repository, never()).revokeUnconsumedTokens(any(), any(), any(), any());
        verify(repository, never()).insertToken(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Rechaza emisión si usuario está inactivo (is_active = false)")
    void rechazaEmision_siUsuarioInactivo() {
        when(repository.findEligibilityForEmission(orgId, userId))
                .thenReturn(Optional.of(new UserSetupEligibility(
                        orgId, userId, "ACTIVA", false, false // isActive = false
                )));

        Optional<InitialSetupTokenEmission> result = issueInitialSetupToken.issue(orgId, userId);

        assertThat(result).isEmpty();
        verify(repository, never()).revokeUnconsumedTokens(any(), any(), any(), any());
        verify(repository, never()).insertToken(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Rechaza emisión si organización no está activa (service_status != 'ACTIVA')")
    void rechazaEmision_siOrganizacionInactiva() {
        when(repository.findEligibilityForEmission(orgId, userId))
                .thenReturn(Optional.of(new UserSetupEligibility(
                        orgId, userId, "SUSPENDIDA", true, false
                )));

        Optional<InitialSetupTokenEmission> result = issueInitialSetupToken.issue(orgId, userId);

        assertThat(result).isEmpty();
        verify(repository, never()).revokeUnconsumedTokens(any(), any(), any(), any());
        verify(repository, never()).insertToken(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Rechaza emisión si usuario u organización no existen en repositorio")
    void rechazaEmision_siUsuarioNoExiste() {
        when(repository.findEligibilityForEmission(orgId, userId))
                .thenReturn(Optional.empty());

        Optional<InitialSetupTokenEmission> result = issueInitialSetupToken.issue(orgId, userId);

        assertThat(result).isEmpty();
        verify(repository, never()).revokeUnconsumedTokens(any(), any(), any(), any());
        verify(repository, never()).insertToken(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Fallo en repositorio propaga TokenDataAccessException sin capturarla como vacío")
    void falloRepositorio_propagaTokenDataAccessException() {
        when(repository.findEligibilityForEmission(orgId, userId))
                .thenThrow(new TokenDataAccessException());

        assertThatThrownBy(() -> issueInitialSetupToken.issue(orgId, userId))
                .isInstanceOf(TokenDataAccessException.class);
    }
}
