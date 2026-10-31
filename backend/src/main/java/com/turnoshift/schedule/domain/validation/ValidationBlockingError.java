package com.turnoshift.schedule.domain.validation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa un error bloqueante determinista detectado en la validación de un borrador.
 */
public record ValidationBlockingError(
        BlockingErrorCode code,
        String message,
        UUID draftAssignmentId,
        UUID employeeId,
        UUID positionId,
        Map<String, Object> details
) {

    public ValidationBlockingError {
        Objects.requireNonNull(code, "code no puede ser nulo");
        Objects.requireNonNull(message, "message no puede ser nulo");
        details = details == null ? Collections.emptyMap() : Collections.unmodifiableMap(details);
    }

    public static ValidationBlockingError of(BlockingErrorCode code, String message) {
        return new ValidationBlockingError(code, message, null, null, null, Collections.emptyMap());
    }

    public static ValidationBlockingError of(
            BlockingErrorCode code,
            String message,
            UUID draftAssignmentId,
            UUID employeeId,
            UUID positionId,
            Map<String, Object> details
    ) {
        return new ValidationBlockingError(code, message, draftAssignmentId, employeeId, positionId, details);
    }
}
