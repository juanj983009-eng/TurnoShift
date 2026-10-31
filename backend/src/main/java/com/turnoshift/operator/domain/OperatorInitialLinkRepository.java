package com.turnoshift.operator.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para la inspección y actualización atómica del estado del administrador y organización
 * durante la emisión de enlaces iniciales por el operador de plataforma.
 */
public interface OperatorInitialLinkRepository {

    record AdminLinkTarget(
            UUID organizationId,
            String orgServiceStatus,
            UUID adminUserId,
            String roleCode,
            boolean isUserActive,
            boolean hasPasswordSet,
            long lockVersion
    ) {
        public AdminLinkTarget {
            Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
            Objects.requireNonNull(orgServiceStatus, "orgServiceStatus no puede ser nulo");
            // adminUserId puede ser null si la organización existe pero el usuario no pertenece a ella
        }
    }

    /**
     * Consulta el estado de la organización y el usuario con bloqueo pesimista UPDLOCK, HOLDLOCK.
     * Retorna vacío si la organización no existe.
     * Si la organización existe pero el usuario no pertenece a ella, adminUserId será null.
     */
    Optional<AdminLinkTarget> findAdminLinkTarget(UUID organizationId, UUID adminUserId);

    /**
     * Incrementa atómicamente la versión de bloqueo del usuario si coincide con expectedVersion.
     *
     * @return true si la fila fue actualizada exitosamente; false si hubo discrepancia de versión
     */
    boolean incrementUserLockVersion(UUID organizationId, UUID adminUserId, long expectedVersion, Instant now);
}
