package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.AppUserPasswordRepository;
import com.turnoshift.identity.domain.PasswordResetTokenRepository;
import com.turnoshift.identity.domain.PasswordSetupResult;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.TokenConsumptionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * Componente transaccional que consume el token INITIAL_SETUP, persiste el hash
 * de contraseña e incrementa auth_version dentro de una única transacción JDBC.
 */
@Service
public class TransactionalInitialPasswordSetupApplier {

    private final ConsumeResetToken consumeResetToken;
    private final AppUserPasswordRepository appUserPasswordRepository;
    private final PasswordResetTokenRepository tokenRepository;

    public TransactionalInitialPasswordSetupApplier(
            ConsumeResetToken consumeResetToken,
            AppUserPasswordRepository appUserPasswordRepository,
            PasswordResetTokenRepository tokenRepository
    ) {
        this.consumeResetToken = Objects.requireNonNull(consumeResetToken, "consumeResetToken no puede ser nulo");
        this.appUserPasswordRepository = Objects.requireNonNull(appUserPasswordRepository, "appUserPasswordRepository no puede ser nulo");
        this.tokenRepository = Objects.requireNonNull(tokenRepository, "tokenRepository no puede ser nulo");
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PasswordSetupResult apply(String rawToken, String passwordHash, Instant now) {
        // 1. Consumir el token atómicamente
        TokenConsumptionResult consumptionResult = consumeResetToken.consume(rawToken);
        if (consumptionResult instanceof TokenConsumptionResult.Failure) {
            return new PasswordSetupResult.InvalidToken();
        }

        TokenConsumptionResult.Success success = (TokenConsumptionResult.Success) consumptionResult;

        // 2. Persistir password_hash e incrementar auth_version bajo condiciones atómicas
        boolean updated = appUserPasswordRepository.setInitialPassword(
                success.organizationId(),
                success.userId(),
                passwordHash,
                now
        );

        if (!updated) {
            // Forzar rollback para que el token consumido previamente no quede marcado como consumido
            throw new InitialPasswordSetupConditionFailedException();
        }

        // 3. Revocar cualquier token INITIAL_SETUP restante de ese usuario
        tokenRepository.revokeUnconsumedTokens(
                success.organizationId(),
                success.userId(),
                ResetTokenPurpose.INITIAL_SETUP,
                now
        );

        return new PasswordSetupResult.Success();
    }
}
