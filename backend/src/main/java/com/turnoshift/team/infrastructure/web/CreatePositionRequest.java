package com.turnoshift.team.infrastructure.web;

public record CreatePositionRequest(
        String code,
        String name
) {}
