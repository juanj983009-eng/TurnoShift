package com.turnoshift.team.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository {

    Optional<Position> findById(UUID organizationId, UUID positionId);

    Optional<Position> findByCode(UUID organizationId, String normalizedCode);

    List<Position> findAll(UUID organizationId, Boolean activeFilter);

    void insert(Position position);

    Optional<Position> update(Position updatedPosition, long expectedLockVersion);
}
