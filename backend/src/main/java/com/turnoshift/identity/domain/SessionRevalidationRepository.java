package com.turnoshift.identity.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para consultar el estado vigente de una sesión activa.
 */
public interface SessionRevalidationRepository {

    /**
     * Consulta el estado vigente de un usuario y su organización.
     *
     * @param userId Identificador del usuario derivado del SecurityContext
     * @param organizationId Identificador de la organización derivado del SecurityContext
     * @return Candidato con datos vigentes, o {@link Optional#empty()} si no existe o datos corruptos
     * @throws AuthenticationDataAccessException si ocurre un fallo técnico de base de datos
     */
    Optional<SessionRevalidationCandidate> findCandidate(UUID userId, UUID organizationId);
}
