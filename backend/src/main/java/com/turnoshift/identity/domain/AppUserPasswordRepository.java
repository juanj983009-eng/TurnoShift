package com.turnoshift.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Puerto de persistencia para la actualización de contraseñas de cuentas de usuario.
 */
public interface AppUserPasswordRepository {

    /**
     * Establece la contraseña inicial de un usuario e incrementa su versión de autenticación
     * de forma atómica bajo las condiciones:
     * <ul>
     *   <li>{@code organization_id} y {@code id} coincidentes.</li>
     *   <li>{@code is_active = 1}.</li>
     *   <li>{@code password_hash IS NULL}.</li>
     *   <li>{@code organizations.service_status = 'ACTIVA'}.</li>
     * </ul>
     *
     * @param organizationId ID de la organización
     * @param userId         ID del usuario
     * @param passwordHash   Hash Argon2id (VARCHAR(512))
     * @param now            Marca de tiempo del cambio
     * @return {@code true} si se actualizó exactamente 1 fila; {@code false} si no hubo coincidencia
     */
    boolean setInitialPassword(
            UUID organizationId,
            UUID userId,
            String passwordHash,
            Instant now
    );
}
