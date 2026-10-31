package com.turnoshift.team.infrastructure.web;

public record ChangeEmployeeStatusRequest(
        Long expectedLockVersion
) {}
