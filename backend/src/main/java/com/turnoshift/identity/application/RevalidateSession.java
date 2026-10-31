package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.identity.domain.AuthenticationDataAccessException;
import com.turnoshift.identity.domain.SessionRevalidationCandidate;
import com.turnoshift.identity.domain.SessionRevalidationRepository;
import com.turnoshift.identity.domain.SessionSecurityConstants;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso: Revalidación del límite absoluto de sesión (8h) y estado vigente de usuario/organización.
 */
public class RevalidateSession {

    private final SessionRevalidationRepository repository;
    private final Clock clock;

    public RevalidateSession(SessionRevalidationRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    public RevalidationOutcome execute(AuthenticatedIdentity currentIdentity, Object authenticatedAtRaw) {
        if (currentIdentity == null) {
            return RevalidationOutcome.invalid();
        }

        // 1. Verificación del atributo authenticatedAt y límite absoluto de 8 horas
        Long authenticatedAtMillis = extractAuthenticatedAtMillis(authenticatedAtRaw);
        if (authenticatedAtMillis == null) {
            return RevalidationOutcome.expired();
        }

        Instant authenticatedAt = Instant.ofEpochMilli(authenticatedAtMillis);
        Instant now = clock.instant();

        // Si está en el futuro más allá de la tolerancia aceptable
        if (authenticatedAt.isAfter(now.plus(SessionSecurityConstants.CLOCK_SKEW_TOLERANCE))) {
            return RevalidationOutcome.expired();
        }

        // Si transcurrieron 8 horas o más desde la autenticación inicial
        if (!now.isBefore(authenticatedAt.plus(SessionSecurityConstants.DEFAULT_MAX_LIFETIME))) {
            return RevalidationOutcome.expired();
        }

        // 2. Consulta de estado vigente en base de datos
        Optional<SessionRevalidationCandidate> candidateOpt;
        try {
            candidateOpt = repository.findCandidate(currentIdentity.userId(), currentIdentity.organizationId());
        } catch (AuthenticationDataAccessException e) {
            return RevalidationOutcome.dataAccessError();
        }

        if (candidateOpt.isEmpty()) {
            return RevalidationOutcome.invalid();
        }

        SessionRevalidationCandidate candidate = candidateOpt.get();

        // 3. Revalidar usuario activo
        if (!candidate.isActive()) {
            return RevalidationOutcome.invalid();
        }

        // 4. Revalidar auth_version idéntica
        if (candidate.authVersion() != currentIdentity.authVersion()) {
            return RevalidationOutcome.invalid();
        }

        // 5. Revalidar estado de la organización
        if (!"ACTIVA".equalsIgnoreCase(candidate.organizationStatus())) {
            return RevalidationOutcome.organizationAccessDenied();
        }

        // 6. Revalidar rol y datos actualizados
        if (!candidate.roleCode().equals(currentIdentity.role())
                || !candidate.displayName().equals(currentIdentity.displayName())
                || !Objects.equals(candidate.employeeId(), currentIdentity.employeeId())
                || !candidate.organizationDisplayName().equals(currentIdentity.organizationDisplayName())
                || !candidate.organizationTimeZone().equals(currentIdentity.organizationTimeZone())) {

            AuthenticatedIdentity updated = new AuthenticatedIdentity(
                    currentIdentity.userId(),
                    currentIdentity.organizationId(),
                    currentIdentity.normalizedEmail(),
                    candidate.displayName(),
                    candidate.roleCode(),
                    candidate.employeeId(),
                    candidate.authVersion(),
                    candidate.organizationDisplayName(),
                    candidate.organizationTimeZone()
            );
            return RevalidationOutcome.roleChanged(updated);
        }

        return RevalidationOutcome.valid(currentIdentity);
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
