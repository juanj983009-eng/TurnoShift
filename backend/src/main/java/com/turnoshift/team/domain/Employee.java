package com.turnoshift.team.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Employee(
        UUID id,
        UUID organizationId,
        String code,
        String displayName,
        UUID positionId,
        UUID siteId,
        boolean isActive,
        long lockVersion,
        Instant createdAt,
        Instant updatedAt
) {

    public static final int CODE_MAX_LENGTH = 50;
    public static final int DISPLAY_NAME_MAX_LENGTH = 150;

    public static Employee create(
            UUID organizationId,
            String rawCode,
            String rawDisplayName,
            UUID positionId,
            UUID siteId,
            Instant now
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(positionId, "positionId no puede ser nulo");
        Objects.requireNonNull(siteId, "siteId no puede ser nulo");
        Objects.requireNonNull(now, "now no puede ser nulo");

        String normalizedCode = validateAndNormalizeCode(rawCode);
        String normalizedDisplayName = validateAndNormalizeDisplayName(rawDisplayName);

        return new Employee(
                UUID.randomUUID(),
                organizationId,
                normalizedCode,
                normalizedDisplayName,
                positionId,
                siteId,
                true,
                0L,
                now,
                now
        );
    }

    public static Employee reconstruct(
            UUID id,
            UUID organizationId,
            String code,
            String displayName,
            UUID positionId,
            UUID siteId,
            boolean isActive,
            long lockVersion,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Employee(
                Objects.requireNonNull(id, "id no puede ser nulo"),
                Objects.requireNonNull(organizationId, "organizationId no puede ser nulo"),
                Objects.requireNonNull(code, "code no puede ser nulo"),
                Objects.requireNonNull(displayName, "displayName no puede ser nulo"),
                Objects.requireNonNull(positionId, "positionId no puede ser nulo"),
                Objects.requireNonNull(siteId, "siteId no puede ser nulo"),
                isActive,
                lockVersion,
                Objects.requireNonNull(createdAt, "createdAt no puede ser nulo"),
                Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo")
        );
    }

    public static String validateAndNormalizeCode(String rawCode) {
        if (rawCode == null) {
            throw new InvalidEmployeeDataException("El código del colaborador no puede ser nulo");
        }
        String trimmed = rawCode.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidEmployeeDataException("El código del colaborador no puede estar vacío");
        }
        if (trimmed.length() > CODE_MAX_LENGTH) {
            throw new InvalidEmployeeDataException("El código no puede superar los " + CODE_MAX_LENGTH + " caracteres");
        }
        return trimmed;
    }

    public static String validateAndNormalizeDisplayName(String rawDisplayName) {
        if (rawDisplayName == null) {
            throw new InvalidEmployeeDataException("El nombre del colaborador no puede ser nulo");
        }
        String trimmed = rawDisplayName.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidEmployeeDataException("El nombre del colaborador no puede estar vacío");
        }
        if (trimmed.length() > DISPLAY_NAME_MAX_LENGTH) {
            throw new InvalidEmployeeDataException("El nombre no puede superar los " + DISPLAY_NAME_MAX_LENGTH + " caracteres");
        }
        return trimmed;
    }

    public Employee update(String newDisplayName, UUID newPositionId, Instant now) {
        Objects.requireNonNull(newPositionId, "newPositionId no puede ser nulo");
        Objects.requireNonNull(now, "now no puede ser nulo");

        String normalizedName = validateAndNormalizeDisplayName(newDisplayName);
        return new Employee(
                this.id,
                this.organizationId,
                this.code,
                normalizedName,
                newPositionId,
                this.siteId,
                this.isActive,
                this.lockVersion + 1,
                this.createdAt,
                now
        );
    }

    public Employee changeStatus(boolean newStatus, Instant now) {
        Objects.requireNonNull(now, "now no puede ser nulo");
        return new Employee(
                this.id,
                this.organizationId,
                this.code,
                this.displayName,
                this.positionId,
                this.siteId,
                newStatus,
                this.lockVersion + 1,
                this.createdAt,
                now
        );
    }
}
