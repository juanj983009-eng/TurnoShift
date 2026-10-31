package com.turnoshift.schedule.domain.validation;

import com.turnoshift.schedule.domain.PublishedShiftRecord;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Instantánea de la publicación base sobre la que se construyó el borrador.
 */
public record BasePublicationSnapshot(
        UUID publicationId,
        List<PublishedShiftRecord> publishedShifts
) {
    public BasePublicationSnapshot {
        Objects.requireNonNull(publicationId, "publicationId no puede ser nulo");
        publishedShifts = publishedShifts == null ? List.of() : List.copyOf(publishedShifts);
    }
}
