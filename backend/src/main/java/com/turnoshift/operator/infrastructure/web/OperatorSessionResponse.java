package com.turnoshift.operator.infrastructure.web;

import com.turnoshift.operator.domain.PlatformOperatorIdentity;

import java.util.Objects;
import java.util.UUID;

/**
 * Respuesta de sesión activa del operador de plataforma (GET /api/v1/operator/auth/session
 * y POST /api/v1/operator/auth/login).
 *
 * <p>Contiene exclusivamente la información pública segura del operador y su landingPage.
 * Nunca expone datos empresariales (organizaciones, sedes ni roles de app_users).
 */
public record OperatorSessionResponse(
        OperatorData operator,
        String landingPage
) {
    public static final String DEFAULT_OPERATOR_LANDING_PAGE = "/operator/inicio";

    public OperatorSessionResponse {
        Objects.requireNonNull(operator, "operator no puede ser nulo");
        Objects.requireNonNull(landingPage, "landingPage no puede ser nulo");
    }

    public static OperatorSessionResponse from(PlatformOperatorIdentity identity) {
        Objects.requireNonNull(identity, "identity no puede ser nulo");
        return new OperatorSessionResponse(
                new OperatorData(identity.id(), identity.displayName()),
                DEFAULT_OPERATOR_LANDING_PAGE
        );
    }

    public record OperatorData(
            UUID id,
            String displayName
    ) {
        public OperatorData {
            Objects.requireNonNull(id, "id no puede ser nulo");
            Objects.requireNonNull(displayName, "displayName no puede ser nulo");
        }
    }
}
