package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.AuthenticatedIdentity;

/**
 * Resultado estructurado de la revalidación de una sesión activa.
 */
public record RevalidationOutcome(
        Status status,
        AuthenticatedIdentity updatedIdentity
) {
    public enum Status {
        /** La sesión y los datos de identidad siguen siendo válidos e idénticos. */
        VALID,
        /** El usuario sigue activo y permitido, pero su rol o datos de visualización han cambiado. */
        ROLE_CHANGED,
        /** La sesión alcanzó o superó el límite absoluto de 8 horas, o authenticatedAt es inválido/futuro. */
        EXPIRED,
        /** Usuario no encontrado, inactivo, auth_version cambiado o rol no válido. */
        INVALID,
        /** La organización no está activa (por ejemplo, SUSPENDIDA o CERRADA). */
        ORGANIZATION_ACCESS_DENIED,
        /** Fallo técnico transitorio al consultar la base de datos (503). */
        DATA_ACCESS_ERROR
    }

    public static RevalidationOutcome valid(AuthenticatedIdentity identity) {
        return new RevalidationOutcome(Status.VALID, identity);
    }

    public static RevalidationOutcome roleChanged(AuthenticatedIdentity newIdentity) {
        return new RevalidationOutcome(Status.ROLE_CHANGED, newIdentity);
    }

    public static RevalidationOutcome expired() {
        return new RevalidationOutcome(Status.EXPIRED, null);
    }

    public static RevalidationOutcome invalid() {
        return new RevalidationOutcome(Status.INVALID, null);
    }

    public static RevalidationOutcome organizationAccessDenied() {
        return new RevalidationOutcome(Status.ORGANIZATION_ACCESS_DENIED, null);
    }

    public static RevalidationOutcome dataAccessError() {
        return new RevalidationOutcome(Status.DATA_ACCESS_ERROR, null);
    }
}
