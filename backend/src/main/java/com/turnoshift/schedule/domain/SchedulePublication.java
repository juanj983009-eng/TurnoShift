package com.turnoshift.schedule.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Instantánea oficial e inmutable de un horario semanal publicado.
 */
public record SchedulePublication(
        UUID id,
        UUID organizationId,
        UUID scheduleId,
        int publicationNumber,
        UUID previousPublicationId,
        UUID policyVersionId,
        ZoneId timezoneIdSnapshot,
        PublicationOrigin origin,
        UUID changeSetId,
        Instant publishedAt,
        List<PublishedAssignmentRef> assignments
) {

    public SchedulePublication {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");
        Objects.requireNonNull(policyVersionId, "policyVersionId no puede ser nulo");
        Objects.requireNonNull(timezoneIdSnapshot, "timezoneIdSnapshot no puede ser nulo");
        Objects.requireNonNull(origin, "origin no puede ser nulo");
        Objects.requireNonNull(changeSetId, "changeSetId no puede ser nulo");
        Objects.requireNonNull(publishedAt, "publishedAt no puede ser nulo");

        if (publicationNumber <= 0) {
            throw new ScheduleDomainException("publicationNumber debe ser estrictamente positivo");
        }
        if (publicationNumber == 1) {
            if (previousPublicationId != null || origin != PublicationOrigin.INICIAL) {
                throw new ScheduleDomainException(
                        "La publicación número 1 debe tener previousPublicationId nulo y origin INICIAL"
                );
            }
        } else {
            if (previousPublicationId == null || origin == PublicationOrigin.INICIAL) {
                throw new ScheduleDomainException(
                        "Publicaciones mayores a 1 deben tener previousPublicationId no nulo y origin REVISION o INTERCAMBIO"
                );
            }
        }

        assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }
}
