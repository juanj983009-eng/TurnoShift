package com.turnoshift.identity.domain;

import java.util.UUID;

/**
 * Candidato de autenticación recuperado por el repositorio.
 *
 * <p>Contiene los datos necesarios para verificar credenciales y construir
 * la identidad autenticada. Este objeto es transitorio: solo vive durante
 * el caso de uso de autenticación y no se expone fuera de él.
 *
 * <p><b>passwordHash</b> se almacena en un campo opaco cuyo {@link #toString()}
 * nunca lo expone, evitando filtración en logs o mensajes de error.
 *
 * <p>Sin dependencias de Spring, JDBC ni HTTP. Dominio puro.
 */
public final class AuthenticationCandidate {

    private final UUID userId;
    private final UUID organizationId;
    private final String normalizedEmail;
    private final String displayName;
    private final String roleCode;
    private final UUID employeeId; // nullable
    private final boolean active;
    private final long authVersion;
    private final String organizationDisplayName;
    private final String organizationTimeZone;
    private final String organizationStatus;
    private final String passwordHash; // nullable — NEVER exposed in toString

    public AuthenticationCandidate(UUID userId,
                                   UUID organizationId,
                                   String normalizedEmail,
                                   String displayName,
                                   String roleCode,
                                   UUID employeeId,
                                   boolean active,
                                   long authVersion,
                                   String organizationDisplayName,
                                   String organizationTimeZone,
                                   String organizationStatus,
                                   String passwordHash) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.normalizedEmail = normalizedEmail;
        this.displayName = displayName;
        this.roleCode = roleCode;
        this.employeeId = employeeId;
        this.active = active;
        this.authVersion = authVersion;
        this.organizationDisplayName = organizationDisplayName;
        this.organizationTimeZone = organizationTimeZone;
        this.organizationStatus = organizationStatus;
        this.passwordHash = passwordHash;
    }

    public UUID userId() { return userId; }
    public UUID organizationId() { return organizationId; }
    public String normalizedEmail() { return normalizedEmail; }
    public String displayName() { return displayName; }
    public String roleCode() { return roleCode; }
    public UUID employeeId() { return employeeId; }
    public boolean isActive() { return active; }
    public long authVersion() { return authVersion; }
    public String organizationDisplayName() { return organizationDisplayName; }
    public String organizationTimeZone() { return organizationTimeZone; }
    public String organizationStatus() { return organizationStatus; }
    public String passwordHash() { return passwordHash; }

    /**
     * Nunca expone passwordHash para evitar filtración en logs o excepciones.
     */
    @Override
    public String toString() {
        return "AuthenticationCandidate{userId=" + userId
                + ", organizationId=" + organizationId
                + ", normalizedEmail='[REDACTED]'"
                + ", passwordHash='[REDACTED]'"
                + "}";
    }
}
