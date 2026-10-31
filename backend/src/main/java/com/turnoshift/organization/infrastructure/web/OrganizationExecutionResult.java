package com.turnoshift.organization.infrastructure.web;

import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.OrganizationView;

/**
 * Resultado interno sellado para la ejecución determinista de mutaciones de configuración
 * de organización y sede única.
 */
public sealed interface OrganizationExecutionResult {

    record OrganizationSuccess(OrganizationView organization) implements OrganizationExecutionResult {}

    record SiteSuccess(ActiveSiteView site) implements OrganizationExecutionResult {}

    record ResourceNotFound() implements OrganizationExecutionResult {}

    record ConcurrentModification() implements OrganizationExecutionResult {}
}
