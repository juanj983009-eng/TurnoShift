package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.PasswordResetTokenRepository;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.SecureTokenGenerator;
import com.turnoshift.identity.domain.TokenConsumptionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso para el consumo atómico y condicional de tokens de configuración.
 *
 * <p>Aplica consumo en una sola operación atómica con las guardas:
 * <ul>
 *   <li>{@code token_hash} coincidente.</li>
 *   <li>{@code purpose = 'INITIAL_SETUP'}.</li>
 *   <li>{@code consumed_at IS NULL}.</li>
 *   <li>{@code revoked_at IS NULL}.</li>
 *   <li>{@code expires_at > now}.</li>
 *   <li>{@code app_users.is_active = 1}.</li>
 *   <li>{@code app_users.password_hash IS NULL}.</li>
 *   <li>{@code organizations.service_status = 'ACTIVA'}.</li>
 * </ul>
 *
 * <p><b>Comportamiento transaccional:</b> Al estar anotado con {@link Transactional}, este método
 * participa en cualquier transacción externa activa existente (propagación predeterminada REQUIRED).
 * El futuro caso de uso de completado o cambio de contraseña deberá envolver la secuencia
 * «actualizar password_hash + consumir token + invalidar sesiones existentes» dentro de una
 * única transacción atómica de base de datos para asegurar consistencia estricta.
 *
 * <p>En caso de fallo (token inexistente, expirado, revocado, condiciones de usuario/empresa no vigentes,
 * o ya consumido por una petición concurrente), retorna un resultado {@link TokenConsumptionResult.Failure}
 * completamente genérico y sin fugas de datos.
 */
@Service
public class ConsumeResetToken {

    private static final int EXPECTED_TOKEN_LENGTH = 43;

    private final PasswordResetTokenRepository repository;
    private final SecureTokenGenerator tokenGenerator;
    private final Clock clock;

    public ConsumeResetToken(
            PasswordResetTokenRepository repository,
            SecureTokenGenerator tokenGenerator,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    @Transactional
    public TokenConsumptionResult consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() != EXPECTED_TOKEN_LENGTH) {
            return new TokenConsumptionResult.Failure();
        }

        byte[] tokenHash = tokenGenerator.computeSha256(rawToken);
        Instant now = clock.instant();

        Optional<PasswordResetTokenRepository.ConsumedTokenInfo> consumedOpt =
                repository.consumeToken(tokenHash, ResetTokenPurpose.INITIAL_SETUP, now);

        if (consumedOpt.isEmpty()) {
            return new TokenConsumptionResult.Failure();
        }

        PasswordResetTokenRepository.ConsumedTokenInfo info = consumedOpt.get();
        return new TokenConsumptionResult.Success(
                info.organizationId(),
                info.userId(),
                info.purpose()
        );
    }
}
