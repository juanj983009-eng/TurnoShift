package com.turnoshift.team.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Entidad de dominio que representa un puesto o cargo de trabajo dentro de una organización.
 */
public record Position(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        boolean isActive,
        long lockVersion,
        Instant createdAt,
        Instant updatedAt
) {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9-]+$");
    private static final int CODE_MIN_LENGTH = 2;
    private static final int CODE_MAX_LENGTH = 32;
    private static final int NAME_MAX_LENGTH = 100;

    public static Position create(UUID organizationId, String rawCode, String rawName, Instant now) {
        Objects.requireNonNull(organizationId, "organizationId cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        String normalizedCode = validateAndNormalizeCode(rawCode);
        String trimmedName = validateAndNormalizeName(rawName);

        return new Position(
                UUID.randomUUID(),
                organizationId,
                normalizedCode,
                trimmedName,
                true,
                0L,
                now,
                now
        );
    }

    public static Position reconstruct(
            UUID id,
            UUID organizationId,
            String code,
            String name,
            boolean isActive,
            long lockVersion,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Position(
                Objects.requireNonNull(id, "id cannot be null"),
                Objects.requireNonNull(organizationId, "organizationId cannot be null"),
                Objects.requireNonNull(code, "code cannot be null"),
                Objects.requireNonNull(name, "name cannot be null"),
                isActive,
                lockVersion,
                Objects.requireNonNull(createdAt, "createdAt cannot be null"),
                Objects.requireNonNull(updatedAt, "updatedAt cannot be null")
        );
    }

    public Position updateName(String newName, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        String trimmedName = validateAndNormalizeName(newName);

        return new Position(
                this.id,
                this.organizationId,
                this.code,
                trimmedName,
                this.isActive,
                this.lockVersion + 1,
                this.createdAt,
                now
        );
    }

    public Position changeStatus(boolean desiredActive, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        return new Position(
                this.id,
                this.organizationId,
                this.code,
                this.name,
                desiredActive,
                this.lockVersion + 1,
                this.createdAt,
                now
        );
    }

    public static String validateAndNormalizeCode(String rawCode) {
        if (rawCode == null) {
            throw new InvalidPositionDataException("El código no puede ser nulo");
        }
        String trimmed = rawCode.trim();
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (normalized.length() < CODE_MIN_LENGTH || normalized.length() > CODE_MAX_LENGTH) {
            throw new InvalidPositionDataException(
                    "El código debe tener entre " + CODE_MIN_LENGTH + " y " + CODE_MAX_LENGTH + " caracteres: " + rawCode
            );
        }
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new InvalidPositionDataException(
                    "El código solo puede contener letras mayúsculas, dígitos y guiones: " + rawCode
            );
        }
        return normalized;
    }

    public static String validateAndNormalizeName(String rawName) {
        if (rawName == null) {
            throw new InvalidPositionDataException("El nombre no puede ser nulo");
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidPositionDataException("El nombre no puede estar vacío");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new InvalidPositionDataException(
                    "El nombre no puede superar los " + NAME_MAX_LENGTH + " caracteres: " + trimmed.length()
            );
        }
        return trimmed;
    }
}
