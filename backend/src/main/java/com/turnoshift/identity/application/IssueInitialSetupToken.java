package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.InitialSetupTokenEmission;
import com.turnoshift.identity.domain.PasswordResetTokenRepository;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.SecureTokenGenerator;
import com.turnoshift.identity.domain.UserSetupEligibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso para la emisión segura de tokens de primer acceso (INITIAL_SETUP).
 *
 * <p>Reglas estrictas:
 * <ul>
 *   <li>Solo emite si la organización está {@code ACTIVA}, el usuario está activo y no tiene contraseña configurada.</li>
 *   <li>Revoca todos los tokens no consumidos y no revocados previos de tipo INITIAL_SETUP para ese usuario.</li>
 *   <li>Genera un secreto criptográfico de 256 bits (43 caracteres Base64 URL-safe).</li>
 *   <li>Almacena exclusivamente el hash SHA-256 (32 bytes).</li>
 *   <li>Vigencia estricta de 30 minutos.</li>
 *   <li>Ejecuta la revocación y la inserción dentro de la misma transacción de base de datos.</li>
 * </ul>
 */
@Service
public class IssueInitialSetupToken {

    public static final Duration INITIAL_SETUP_TTL = Duration.ofMinutes(30);

    private final PasswordResetTokenRepository repository;
    private final SecureTokenGenerator tokenGenerator;
    private final Clock clock;

    public IssueInitialSetupToken(
            PasswordResetTokenRepository repository,
            SecureTokenGenerator tokenGenerator,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    @Transactional
    public Optional<InitialSetupTokenEmission> issue(UUID organizationId, UUID userId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(userId, "userId no puede ser nulo");

        Optional<UserSetupEligibility> eligibilityOpt = repository.findEligibilityForEmission(organizationId, userId);
        if (eligibilityOpt.isEmpty() || !eligibilityOpt.get().isEligibleForInitialSetup()) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(INITIAL_SETUP_TTL);

        // Revocar cualquier token previo no consumido y no revocado (incluso vencido)
        repository.revokeUnconsumedTokens(organizationId, userId, ResetTokenPurpose.INITIAL_SETUP, now);

        String rawToken = tokenGenerator.generateRawToken();
        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        UUID tokenId = UUID.randomUUID();

        repository.insertToken(
                tokenId,
                organizationId,
                userId,
                tokenHash,
                ResetTokenPurpose.INITIAL_SETUP,
                expiresAt,
                now
        );

        return Optional.of(new InitialSetupTokenEmission(tokenId, organizationId, userId, rawToken, expiresAt));
    }
}
