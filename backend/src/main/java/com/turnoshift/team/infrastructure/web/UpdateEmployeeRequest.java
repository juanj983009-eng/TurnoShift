package com.turnoshift.team.infrastructure.web;

import java.util.UUID;

public record UpdateEmployeeRequest(
        String displayName,
        UUID positionId,
        Long expectedLockVersion
) {}
