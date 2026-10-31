package com.turnoshift.identity.application;

import com.turnoshift.identity.domain.AuthenticationCandidate;
import com.turnoshift.identity.domain.AuthenticationDataAccessException;

import java.util.Optional;

/**
 * Puerto de persistencia para buscar candidatos de autenticación.
 *
 * <p>La búsqueda es siempre por código de organización normalizado
 * + correo electrónico normalizado. Nunca por correo solo.
 *
 * <p>Responsabilidades del implementador:
 * <ul>
 *   <li>Usar SQL parametrizado, nunca concatenar valores.</li>
 *   <li>Respetar aislamiento multi-tenant: código de empresa A
 *       no debe recuperar usuarios de empresa B.</li>
 *   <li>No aceptar organizationId suministrado por el cliente.</li>
 *   <li>Tratar datos corruptos en fila (UUID malformado) como dato ausente —
 *       retornar vacío o lanzar esta excepción según la gravedad.</li>
 *   <li>Propagar {@link AuthenticationDataAccessException} cuando la consulta
 *       no pueda ejecutarse (base de datos caída, timeout, error de red).
 *       Eso es distinto de "usuario no existe", que devuelve empty.</li>
 * </ul>
 */
public interface AuthenticationCandidateRepository {

    /**
     * Busca un candidato de autenticación por código de empresa
     * normalizado y correo normalizado.
     *
     * @param normalizedOrgCode código de empresa normalizado (trim, upper, Locale.ROOT)
     * @param normalizedEmail   correo normalizado (NFC, trim Unicode, lower Locale.ROOT)
     * @return candidato si existe; vacío si organización o usuario no existen
     * @throws AuthenticationDataAccessException si el repositorio no puede ejecutar
     *         la consulta (caída de base de datos, timeout, error de red)
     */
    Optional<AuthenticationCandidate> findByOrgCodeAndEmail(
            String normalizedOrgCode, String normalizedEmail);
}
