package com.turnoshift.team.infrastructure.web;

public record UpdatePositionRequest(
        String name,
        Long expectedLockVersion
) {}
