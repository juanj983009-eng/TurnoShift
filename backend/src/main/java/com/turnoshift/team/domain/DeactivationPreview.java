package com.turnoshift.team.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DeactivationPreview(
        UUID employeeId,
        String displayName,
        boolean canDeactivate,
        List<String> blockers
) {
    public DeactivationPreview {
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");
        Objects.requireNonNull(displayName, "displayName no puede ser nulo");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
}
