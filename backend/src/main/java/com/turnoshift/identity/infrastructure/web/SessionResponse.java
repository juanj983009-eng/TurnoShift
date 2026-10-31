package com.turnoshift.identity.infrastructure.web;

import java.util.UUID;

/**
 * Respuesta de sesión exitosa conforme al contrato openapi.yaml (SessionResponse).
 */
public record SessionResponse(
        SessionUserDto user,
        SessionOrganizationDto organization,
        String landingPage
) {
    public record SessionUserDto(
            UUID id,
            String displayName,
            String role,
            UUID employeeId
    ) {}

    public record SessionOrganizationDto(
            UUID id,
            String displayName,
            String timeZone
    ) {}

    public static SessionResponse from(com.turnoshift.identity.domain.AuthenticatedIdentity identity) {
        String landingPage = "COLABORADOR".equals(identity.role()) ? "/app/mi-horario" : "/app/inicio";
        return new SessionResponse(
                new SessionUserDto(
                        identity.userId(),
                        identity.displayName(),
                        identity.role(),
                        identity.employeeId()
                ),
                new SessionOrganizationDto(
                        identity.organizationId(),
                        identity.organizationDisplayName(),
                        identity.organizationTimeZone()
                ),
                landingPage
        );
    }
}
