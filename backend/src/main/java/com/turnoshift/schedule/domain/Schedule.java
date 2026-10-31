package com.turnoshift.schedule.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad raíz que representa el contenedor de planificación semanal para una sede.
 */
public record Schedule(
        UUID id,
        UUID organizationId,
        UUID siteId,
        LocalDate weekStart,
        UUID currentPublicationId,
        long lockVersion,
        Instant createdAt,
        Instant updatedAt
) {

    public Schedule {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(siteId, "siteId no puede ser nulo");
        Objects.requireNonNull(weekStart, "weekStart no puede ser nulo");
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ScheduleDomainException(
                    "La semana de planificación debe iniciar obligatoriamente en lunes. Fecha recibida: " + weekStart
            );
        }
        if (lockVersion < 0) {
            throw new ScheduleDomainException("lockVersion no puede ser negativo");
        }
    }

    public boolean isPublished() {
        return currentPublicationId != null;
    }

    public static Schedule initial(UUID organizationId, UUID siteId, LocalDate weekStart) {
        return new Schedule(
                UUID.randomUUID(),
                organizationId,
                siteId,
                weekStart,
                null,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    public Schedule withCurrentPublication(UUID publicationId, Instant updatedAtTimestamp) {
        return new Schedule(
                id,
                organizationId,
                siteId,
                weekStart,
                publicationId,
                lockVersion + 1,
                createdAt,
                updatedAtTimestamp != null ? updatedAtTimestamp : Instant.now()
        );
    }
}
