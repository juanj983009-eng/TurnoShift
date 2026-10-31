package com.turnoshift.team.infrastructure.web;

public record ChangePositionStatusRequest(
        Boolean isActive,
        Long expectedLockVersion
) {}
