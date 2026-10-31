package com.turnoshift.team.infrastructure.web;

import com.turnoshift.team.domain.Position;

import java.time.Instant;
import java.util.UUID;

public record PositionResponse(
        UUID id,
        String code,
        String name,
        boolean isActive,
        long lockVersion,
        Instant createdAt,
        Instant updatedAt
) {
    public static PositionResponse from(Position position) {
        return new PositionResponse(
                position.id(),
                position.code(),
                position.name(),
                position.isActive(),
                position.lockVersion(),
                position.createdAt(),
                position.updatedAt()
        );
    }
}
