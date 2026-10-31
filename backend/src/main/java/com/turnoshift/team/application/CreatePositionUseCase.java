package com.turnoshift.team.application;

import com.turnoshift.team.domain.Position;
import com.turnoshift.team.domain.PositionCodeAlreadyExistsException;
import com.turnoshift.team.domain.PositionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class CreatePositionUseCase {
    private final PositionRepository positionRepository;
    private final Clock clock;

    public CreatePositionUseCase(PositionRepository positionRepository, Clock clock) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public Position execute(UUID organizationId, String rawCode, String rawName) {
        Instant now = clock.instant();
        Position position = Position.create(organizationId, rawCode, rawName, now);

        if (positionRepository.findByCode(organizationId, position.code()).isPresent()) {
            throw new PositionCodeAlreadyExistsException(position.code());
        }

        positionRepository.insert(position);
        return position;
    }
}
