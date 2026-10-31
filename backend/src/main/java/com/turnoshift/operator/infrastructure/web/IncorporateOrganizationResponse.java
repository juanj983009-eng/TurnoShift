package com.turnoshift.operator.infrastructure.web;

import java.util.List;
import java.util.UUID;

/**
 * DTO de respuesta para la incorporación asistida de una organización.
 *
 * <p>Conforme al esquema {@code IncorporateOrganizationResponse} de OpenAPI 3.1.
 * Contiene únicamente datos públicos y seguros; jamás contiene contraseñas, tokens ni secretos.
 */
public record IncorporateOrganizationResponse(
        OrganizationSummary organization,
        SiteSummary site,
        UserSummary firstAdmin,
        List<String> pendingSetup
) {
    public static final List<String> DEFAULT_PENDING_SETUP = List.of(
            "POLICY_NOT_CONFIGURED",
            "NO_POSITIONS",
            "NO_EMPLOYEES",
            "INITIAL_PASSWORD_NOT_SET"
    );

    public record OrganizationSummary(
            UUID id,
            String code,
            String displayName,
            String timeZone
    ) {}

    public record SiteSummary(
            UUID id,
            String displayName
    ) {}

    public record UserSummary(
            UUID id,
            String displayName,
            String email,
            String role
    ) {}
}
