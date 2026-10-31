package com.turnoshift.organization.infrastructure.web;

/**
 * DTO para la petición de actualización del nombre de la sede activa.
 */
public record UpdateActiveSiteNameRequest(
        String name,
        Long expectedLockVersion
) {}
