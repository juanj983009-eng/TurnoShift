package com.turnoshift.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Resultado del consumo atómico y condicional de un token de configuración o restablecimiento.
 *
 * <p>En caso de fallo, {@link Failure} es completamente genérico y no expone identificadores
 * de usuario, organización ni motivo del rechazo.
 */
public sealed interface TokenConsumptionResult permits TokenConsumptionResult.Success, TokenConsumptionResult.Failure {

    record Success(UUID organizationId, UUID userId, ResetTokenPurpose purpose) implements TokenConsumptionResult {
        public Success {
            Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
            Objects.requireNonNull(userId, "userId no puede ser nulo");
            Objects.requireNonNull(purpose, "purpose no puede ser nulo");
        }
    }

    record Failure() implements TokenConsumptionResult {
    }
}
