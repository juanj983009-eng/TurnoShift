package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.PasswordResetTokenRepository;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.SecureTokenGenerator;
import com.turnoshift.identity.domain.TokenInspectionCandidate;
import com.turnoshift.identity.domain.TokenInspectionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso para la inspección de solo lectura del estado de un token de configuración.
 *
 * <p>Un token es considerado válido únicamente si:
 * <ul>
 *   <li>El formato sintáctico del secreto en texto plano es correcto (43 caracteres Base64 URL-safe).</li>
 *   <li>Existe en base de datos bajo su hash SHA-256.</li>
 *   <li>Su propósito es {@code INITIAL_SETUP} (PASSWORD_RESET no está habilitado en este corte).</li>
 *   <li>No ha sido consumido ni revocado previamente.</li>
 *   <li>No ha expirado (expires_at > instante actual).</li>
 *   <li>El usuario destinatario permanece activo (is_active = 1).</li>
 *   <li>El usuario aún no tiene contraseña configurada (password_hash IS NULL).</li>
 *   <li>La organización permanece en estado ACTIVA.</li>
 * </ul>
 */
@Service
public class InspectResetToken {

    private static final int EXPECTED_TOKEN_LENGTH = 43;

    private final PasswordResetTokenRepository repository;
    private final SecureTokenGenerator tokenGenerator;
    private final Clock clock;

    public InspectResetToken(
            PasswordResetTokenRepository repository,
            SecureTokenGenerator tokenGenerator,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    @Transactional(readOnly = true)
    public TokenInspectionResult inspect(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() != EXPECTED_TOKEN_LENGTH) {
            return new TokenInspectionResult.Invalid();
        }

        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        Optional<TokenInspectionCandidate> candidateOpt = repository.findCandidateForInspection(tokenHash);
        if (candidateOpt.isEmpty()) {
            return new TokenInspectionResult.Invalid();
        }

        TokenInspectionCandidate candidate = candidateOpt.get();
        Instant now = clock.instant();

        // En este subincremento únicamente se admite INITIAL_SETUP
        if (candidate.purpose() != ResetTokenPurpose.INITIAL_SETUP) {
            return new TokenInspectionResult.Invalid();
        }

        if (candidate.consumedAt() != null || candidate.revokedAt() != null) {
            return new TokenInspectionResult.Invalid();
        }

        if (!candidate.expiresAt().isAfter(now)) {
            return new TokenInspectionResult.Invalid();
        }

        if (!candidate.isUserActive() || candidate.hasPasswordSet()) {
            return new TokenInspectionResult.Invalid();
        }

        if (!"ACTIVA".equalsIgnoreCase(candidate.orgServiceStatus())) {
            return new TokenInspectionResult.Invalid();
        }

        return new TokenInspectionResult.Valid(candidate.purpose());
    }
}
