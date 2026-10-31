package com.turnoshift.identity.domain;

import java.util.List;
import java.util.Objects;

/**
 * Resultado del caso de uso de establecimiento de primera contraseña.
 *
 * <p>Por principios estrictos de seguridad, ninguna variante expone contraseñas,
 * tokens en texto plano, hashes ni identificadores de usuario u organización.
 */
public sealed interface PasswordSetupResult {

    /**
     * Operación completada exitosamente. Contraseña persistida, auth_version
     * incrementada y token consumido.
     */
    record Success() implements PasswordSetupResult {
        @Override
        public String toString() {
            return "PasswordSetupResult.Success";
        }
    }

    /**
     * Token inválido, expirado, revocado, consumido o condiciones de usuario/empresa no vigentes.
     */
    record InvalidToken() implements PasswordSetupResult {
        @Override
        public String toString() {
            return "PasswordSetupResult.InvalidToken";
        }
    }

    /**
     * Violación de la política de contraseñas (longitud o presencia en lista de comprometidas).
     */
    record PolicyViolation(List<ViolacionPolitica> violations) implements PasswordSetupResult {
        public PolicyViolation {
            Objects.requireNonNull(violations, "violations no puede ser nulo");
            violations = List.copyOf(violations);
        }

        @Override
        public String toString() {
            return "PasswordSetupResult.PolicyViolation" + violations;
        }
    }
}
