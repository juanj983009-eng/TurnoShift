package com.turnoshift.identity.domain;

/**
 * Resultado de la inspección de solo lectura de un token de configuración o restablecimiento.
 */
public sealed interface TokenInspectionResult permits TokenInspectionResult.Valid, TokenInspectionResult.Invalid {

    record Valid(ResetTokenPurpose purpose) implements TokenInspectionResult {
        public Valid {
            java.util.Objects.requireNonNull(purpose, "purpose no puede ser nulo");
        }
    }

    record Invalid() implements TokenInspectionResult {
    }
}
