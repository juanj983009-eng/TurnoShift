package com.turnoshift.schedule.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Referencia inmutable entre una publicación y una revisión concreta de asignación.
 */
public record PublishedAssignmentRef(
        UUID assignmentId,
        UUID assignmentRevisionId
) {

    public PublishedAssignmentRef {
        Objects.requireNonNull(assignmentId, "assignmentId no puede ser nulo");
        Objects.requireNonNull(assignmentRevisionId, "assignmentRevisionId no puede ser nulo");
    }
}
