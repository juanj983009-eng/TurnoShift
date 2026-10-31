package com.turnoshift.organization.domain;

/**
 * Reglas de validación y normalización para la configuración de organizaciones y sedes.
 */
public final class OrganizationValidation {

    private static final int MAX_NAME_LENGTH = 150;

    private OrganizationValidation() {
        // Clase utilitaria sin instanciación
    }

    public static String validateAndNormalizeDisplayName(String rawDisplayName) {
        if (rawDisplayName == null) {
            throw new InvalidOrganizationDataException("El nombre de la organización no puede ser nulo.");
        }
        String trimmed = rawDisplayName.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidOrganizationDataException("El nombre de la organización no puede estar vacío.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidOrganizationDataException("El nombre de la organización no puede exceder 150 caracteres.");
        }
        return trimmed;
    }

    public static String validateAndNormalizeSiteName(String rawSiteName) {
        if (rawSiteName == null) {
            throw new InvalidOrganizationDataException("El nombre de la sede no puede ser nulo.");
        }
        String trimmed = rawSiteName.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidOrganizationDataException("El nombre de la sede no puede estar vacío.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidOrganizationDataException("El nombre de la sede no puede exceder 150 caracteres.");
        }
        return trimmed;
    }

    public static void validateExpectedLockVersion(long expectedLockVersion) {
        if (expectedLockVersion < 0) {
            throw new InvalidOrganizationDataException("La versión de bloqueo esperada (expectedLockVersion) debe ser mayor o igual a 0.");
        }
    }
}
