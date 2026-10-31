package com.turnoshift.team.application;

import com.turnoshift.team.domain.Position;
import com.turnoshift.team.domain.PositionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GetPositionsUseCase {
    private final PositionRepository positionRepository;

    public GetPositionsUseCase(PositionRepository positionRepository) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
    }

    public List<Position> execute(UUID organizationId, Boolean activeFilter) {
        Objects.requireNonNull(organizationId, "organizationId cannot be null");
        return positionRepository.findAll(organizationId, activeFilter);
    }
}
