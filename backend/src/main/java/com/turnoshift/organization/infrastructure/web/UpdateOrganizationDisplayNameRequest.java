package com.turnoshift.organization.infrastructure.web;

/**
 * DTO para la petición de actualización del nombre visible de la organización.
 */
public record UpdateOrganizationDisplayNameRequest(
        String displayName,
        Long expectedLockVersion
) {}
