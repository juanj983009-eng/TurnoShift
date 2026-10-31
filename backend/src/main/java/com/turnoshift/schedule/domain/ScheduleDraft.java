package com.turnoshift.schedule.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Propuesta de cuadrante semanal editable o finalizada.
 */
public record ScheduleDraft(
        UUID id,
        UUID organizationId,
        UUID scheduleId,
        UUID basePublicationId,
        DraftStatus status,
        UUID resultPublicationId,
        String changeReason,
        UUID createdByUserId,
        long lockVersion,
        Instant createdAt,
        Instant updatedAt,
        List<DraftAssignment> assignments
) {

    public ScheduleDraft {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");
        Objects.requireNonNull(status, "status no puede ser nulo");
        Objects.requireNonNull(createdByUserId, "createdByUserId no puede ser nulo");
        if (lockVersion < 0) {
            throw new ScheduleDomainException("lockVersion no puede ser negativo");
        }

        if (status == DraftStatus.PUBLICADO && resultPublicationId == null) {
            throw new ScheduleDomainException("Un borrador en estado PUBLICADO debe tener resultPublicationId no nulo");
        }
        if (status != DraftStatus.PUBLICADO && resultPublicationId != null) {
            throw new ScheduleDomainException("Un borrador no PUBLICADO no puede tener resultPublicationId");
        }
        if (changeReason != null && changeReason.isBlank()) {
            throw new ScheduleDomainException("changeReason no puede ser una cadena en blanco");
        }

        assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }

    public boolean isEditable() {
        return status == DraftStatus.BORRADOR;
    }

    public static ScheduleDraft initial(UUID organizationId, UUID scheduleId, UUID createdByUserId, List<DraftAssignment> assignments) {
        return initial(UUID.randomUUID(), organizationId, scheduleId, createdByUserId, assignments);
    }

    public static ScheduleDraft initial(UUID id, UUID organizationId, UUID scheduleId, UUID createdByUserId, List<DraftAssignment> assignments) {
        List<DraftAssignment> reboundAssignments = assignments == null ? List.of() : assignments.stream()
                .map(a -> a.draftId().equals(id) ? a : new DraftAssignment(
                        a.id(), a.organizationId(), a.scheduleId(), id,
                        a.assignmentId(), a.baseRevisionId(), a.employeeId(), a.positionId(),
                        a.timeInterval(), a.createdAt(), a.updatedAt()
                ))
                .toList();
        return new ScheduleDraft(
                id,
                organizationId,
                scheduleId,
                null,
                DraftStatus.BORRADOR,
                null,
                null,
                createdByUserId,
                0L,
                Instant.now(),
                Instant.now(),
                reboundAssignments
        );
    }
}
