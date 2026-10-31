package com.turnoshift.operator.infrastructure.web;

/**
 * DTO de solicitud HTTP para la incorporación asistida de una organización.
 *
 * <p>Conforme al esquema {@code IncorporateOrganizationRequest} de OpenAPI 3.1.
 */
public record IncorporateOrganizationRequest(
        OrganizationInput organization,
        SiteInput site,
        FirstAdminInput firstAdmin
) {
    public record OrganizationInput(
            String code,
            String displayName,
            String timeZone
    ) {}

    public record SiteInput(
            String displayName
    ) {}

    public record FirstAdminInput(
            String email,
            String displayName
    ) {}
}
