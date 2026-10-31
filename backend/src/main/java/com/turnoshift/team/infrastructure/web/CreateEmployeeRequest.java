package com.turnoshift.team.infrastructure.web;

import java.util.UUID;

public record CreateEmployeeRequest(
        String code,
        String displayName,
        UUID positionId
) {}
