package com.turnoshift.unavailability.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CancellationInfo(Instant cancelledAt, UUID cancelledByUserId) {

    public CancellationInfo {
        Objects.requireNonNull(cancelledAt, "cancelledAt no puede ser nulo");
        Objects.requireNonNull(cancelledByUserId, "cancelledByUserId no puede ser nulo");
    }
}
