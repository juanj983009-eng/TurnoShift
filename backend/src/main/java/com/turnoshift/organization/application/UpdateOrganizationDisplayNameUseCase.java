package com.turnoshift.organization.application;

import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.organization.domain.OrganizationNotFoundException;
import com.turnoshift.organization.domain.OrganizationOptimisticLockException;
import com.turnoshift.organization.domain.OrganizationValidation;
import com.turnoshift.organization.domain.OrganizationView;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para actualizar el nombre visible (displayName) de una organización con control de concurrencia optimista.
 * <p>
 * <b>Inmutabilidad de Campos:</b>
 * Este caso de uso solo actualiza {@code display_name}, incrementa {@code lock_version} y actualiza {@code updated_at}.
 * Los campos {@code code}, {@code service_status}, {@code timezone_id}, {@code max_active_employees},
 * {@code max_active_sites} y {@code current_policy_version_id} son estrictamente inmutables en esta operación.
 * <p>
 * <b>Limitación de Zona Horaria:</b>
 * No existe método para modificar {@code timezone_id}; cualquier cambio de huso horario requerirá
 * la infraestructura de cálculo de turnos y validación de coberturas de la iteración I3.
 */
@Service
public class UpdateOrganizationDisplayNameUseCase {

    private final OrganizationConfigurationRepository repository;

    public UpdateOrganizationDisplayNameUseCase(OrganizationConfigurationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
    }

    public OrganizationView execute(UUID organizationId, String rawDisplayName, long expectedLockVersion) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        String normalizedDisplayName = OrganizationValidation.validateAndNormalizeDisplayName(rawDisplayName);
        OrganizationValidation.validateExpectedLockVersion(expectedLockVersion);

        OrganizationView existing = repository.findOrganizationById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));

        if (existing.lockVersion() != expectedLockVersion) {
            throw new OrganizationOptimisticLockException(organizationId, expectedLockVersion);
        }

        return repository.updateOrganizationDisplayName(organizationId, normalizedDisplayName, expectedLockVersion)
                .orElseThrow(() -> new OrganizationOptimisticLockException(organizationId, expectedLockVersion));
    }
}
