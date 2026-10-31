package com.turnoshift.organization.application;

import com.turnoshift.organization.domain.ActiveSiteNotFoundException;
import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.organization.domain.OrganizationValidation;
import com.turnoshift.organization.domain.SiteOptimisticLockException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para actualizar el nombre visible de la sede única activa de una organización
 * con control de concurrencia optimista.
 * <p>
 * <b>Inmutabilidad de Campos:</b>
 * Este caso de uso solo permite actualizar el campo {@code name}, incrementa {@code lock_version}
 * y actualiza {@code updated_at}.
 * Los campos {@code code} e {@code is_active} son estrictamente inmutables en I2.1.
 */
@Service
public class UpdateActiveSiteNameUseCase {

    private final OrganizationConfigurationRepository repository;

    public UpdateActiveSiteNameUseCase(OrganizationConfigurationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
    }

    public ActiveSiteView execute(UUID organizationId, String rawSiteName, long expectedLockVersion) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        String normalizedSiteName = OrganizationValidation.validateAndNormalizeSiteName(rawSiteName);
        OrganizationValidation.validateExpectedLockVersion(expectedLockVersion);

        ActiveSiteView existing = repository.findActiveSiteByOrganizationId(organizationId)
                .orElseThrow(() -> new ActiveSiteNotFoundException(organizationId));

        if (existing.lockVersion() != expectedLockVersion) {
            throw new SiteOptimisticLockException(existing.id(), expectedLockVersion);
        }

        return repository.updateActiveSiteName(organizationId, normalizedSiteName, expectedLockVersion)
                .orElseThrow(() -> new SiteOptimisticLockException(existing.id(), expectedLockVersion));
    }
}
