package com.turnoshift.operator.application;

import com.turnoshift.identity.domain.SessionSecurityConstants;
import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.PlatformOperator;
import com.turnoshift.operator.domain.PlatformOperatorIdentity;
import com.turnoshift.operator.domain.PlatformOperatorRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso: revalidación de sesión para operadores de plataforma.
 *
 * <p>Verifica el límite absoluto de 8 horas mediante {@code authenticatedAt}
 * y consulta {@code platform_operators} para validar que el operador exista,
 * permanezca activo y conserve la misma {@code auth_version}.
 */
public class RevalidateOperatorSession {

    private final PlatformOperatorRepository repository;
    private final Clock clock;

    public RevalidateOperatorSession(PlatformOperatorRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    public OperatorRevalidationOutcome execute(PlatformOperatorIdentity currentIdentity, Object authenticatedAtRaw) {
        if (currentIdentity == null) {
            return OperatorRevalidationOutcome.INVALID;
        }

        // 1. Verificación del atributo authenticatedAt y límite absoluto de 8 horas
        Long authenticatedAtMillis = extractAuthenticatedAtMillis(authenticatedAtRaw);
        if (authenticatedAtMillis == null) {
            return OperatorRevalidationOutcome.EXPIRED;
        }

        Instant authenticatedAt = Instant.ofEpochMilli(authenticatedAtMillis);
        Instant now = clock.instant();

        // Tolerancia de reloj hacia el futuro (>60s)
        if (authenticatedAt.isAfter(now.plus(SessionSecurityConstants.CLOCK_SKEW_TOLERANCE))) {
            return OperatorRevalidationOutcome.EXPIRED;
        }

        // Límite absoluto de 8 horas
        if (!now.isBefore(authenticatedAt.plus(SessionSecurityConstants.DEFAULT_MAX_LIFETIME))) {
            return OperatorRevalidationOutcome.EXPIRED;
        }

        // 2. Consulta de estado en base de datos
        Optional<PlatformOperator> operatorOpt;
        try {
            operatorOpt = repository.findById(currentIdentity.id());
        } catch (OperatorDataAccessException e) {
            return OperatorRevalidationOutcome.DATA_ACCESS_ERROR;
        }

        if (operatorOpt.isEmpty()) {
            return OperatorRevalidationOutcome.INVALID;
        }

        PlatformOperator operator = operatorOpt.get();

        // 3. Revalidar operador activo
        if (!operator.isActive()) {
            return OperatorRevalidationOutcome.INVALID;
        }

        // 4. Revalidar auth_version idéntica
        if (operator.authVersion() != currentIdentity.authVersion()) {
            return OperatorRevalidationOutcome.INVALID;
        }

        return OperatorRevalidationOutcome.VALID;
    }

    private Long extractAuthenticatedAtMillis(Object raw) {
        if (raw instanceof Long l) {
            return l;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
