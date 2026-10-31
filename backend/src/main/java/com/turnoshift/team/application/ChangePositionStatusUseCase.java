package com.turnoshift.team.application;

import com.turnoshift.team.domain.Position;
import com.turnoshift.team.domain.PositionNotFoundException;
import com.turnoshift.team.domain.PositionOptimisticLockException;
import com.turnoshift.team.domain.PositionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ChangePositionStatusUseCase {
    private final PositionRepository positionRepository;
    private final Clock clock;

    public ChangePositionStatusUseCase(PositionRepository positionRepository, Clock clock) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public Position execute(UUID organizationId, UUID positionId, boolean desiredActive, long expectedLockVersion) {
        Position existing = positionRepository.findById(organizationId, positionId)
                .orElseThrow(() -> new PositionNotFoundException(positionId));

        if (existing.lockVersion() != expectedLockVersion) {
            throw new PositionOptimisticLockException(positionId, expectedLockVersion);
        }

        Instant now = clock.instant();
        Position candidate = existing.changeStatus(desiredActive, now);

        return positionRepository.update(candidate, expectedLockVersion)
                .orElseThrow(() -> new PositionOptimisticLockException(positionId, expectedLockVersion));
    }
}
