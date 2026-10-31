package com.turnoshift.team.domain;

import java.util.UUID;

public class NoActiveSiteException extends RuntimeException {
    private final UUID organizationId;

    public NoActiveSiteException(UUID organizationId) {
        super("La organización " + organizationId + " no tiene ninguna sede activa");
        this.organizationId = organizationId;
    }

    public UUID organizationId() {
        return organizationId;
    }
}
