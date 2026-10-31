package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.PasswordBlocklist;
import com.turnoshift.identity.domain.PasswordPolicy;
import com.turnoshift.identity.domain.PasswordSetupResult;
import com.turnoshift.identity.domain.ViolacionPolitica;
import com.turnoshift.identity.infrastructure.security.Argon2PasswordService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Caso de uso orquestador para completar la configuración de la primera contraseña.
 *
 * <p><b>Separación de responsabilidades y conexión a base de datos:</b>
 * La validación estructural y la consulta al proveedor de blocklist ocurren ANTES
 * de abrir la transacción de base de datos para no retener conexiones ni bloqueos SQL
 * durante operaciones de red (HIBP) o computación intensiva de CPU (Argon2id).
 */
@Service
public class CompleteInitialPasswordSetup {

    private static final int EXPECTED_TOKEN_LENGTH = 43;

    private final PasswordBlocklist passwordBlocklist;
    private final Argon2PasswordService passwordService;
    private final TransactionalInitialPasswordSetupApplier transactionApplier;
    private final Clock clock;

    public CompleteInitialPasswordSetup(
            PasswordBlocklist passwordBlocklist,
            Argon2PasswordService passwordService,
            TransactionalInitialPasswordSetupApplier transactionApplier,
            Clock clock
    ) {
        this.passwordBlocklist = Objects.requireNonNull(passwordBlocklist, "passwordBlocklist no puede ser nulo");
        this.passwordService = Objects.requireNonNull(passwordService, "passwordService no puede ser nulo");
        this.transactionApplier = Objects.requireNonNull(transactionApplier, "transactionApplier no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Valida y establece la contraseña inicial del usuario referenciado por el token.
     *
     * @param rawToken    secreto del enlace en texto plano
     * @param newPassword nueva contraseña propuesta en texto plano
     * @return {@link PasswordSetupResult} indicando éxito, token inválido o violación de política
     */
    public PasswordSetupResult complete(String rawToken, String newPassword) {
        // 1. Descarte rápido de formato de token (sin tocar política ni base de datos)
        if (rawToken == null || rawToken.isBlank() || rawToken.length() != EXPECTED_TOKEN_LENGTH) {
            return new PasswordSetupResult.InvalidToken();
        }

        // 2. Validación de política de contraseña ANTES de abrir transacción de base de datos
        // (incluye consulta a HIBP; si falla o no está disponible, propaga PasswordBlocklistUnavailableException)
        List<ViolacionPolitica> violations = PasswordPolicy.validar(newPassword, passwordBlocklist);
        if (!violations.isEmpty()) {
            return new PasswordSetupResult.PolicyViolation(violations);
        }

        // 3. Normalización NFC y cómputo intensivo de Argon2id ANTES de abrir la transacción JDBC
        String preparedPassword = PasswordPolicy.prepararParaCodificacion(newPassword);
        String passwordHash = passwordService.codificar(preparedPassword);
        Instant now = clock.instant();

        // 4. Ejecución de la transacción de base de datos (consumo de token, persistencia y revocación)
        try {
            return transactionApplier.apply(rawToken, passwordHash, now);
        } catch (InitialPasswordSetupConditionFailedException e) {
            // La transacción hizo rollback porque las condiciones de usuario/organización no se cumplieron
            return new PasswordSetupResult.InvalidToken();
        }
    }
}
