package com.turnoshift.operator.domain;

import java.util.UUID;

/**
 * Puerto de persistencia para la creación de organizaciones, sedes iniciales y administradores.
 */
public interface OrganizationOnboardingRepository {

    /**
     * Verifica si un código de organización ya está en uso.
     *
     * @param orgCode código normalizado
     * @return true si ya existe, false si está libre
     */
    boolean existsOrganizationCode(String orgCode);

    /**
     * Inserta la organización, la sede inicial y el administrador inicial en la base de datos.
     */
    void insertOrganizationSiteAndAdmin(
            UUID organizationId,
            String orgCode,
            String orgDisplayName,
            String timeZoneId,
            int maxActiveEmployees,
            UUID siteId,
            String siteCode,
            String siteDisplayName,
            UUID adminId,
            String adminEmail,
            String adminNormalizedEmail,
            String adminDisplayName
    );
}
