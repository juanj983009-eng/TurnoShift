package com.turnoshift.unavailability.infrastructure.web.dto;

public record CancelUnavailabilityRequest(
        Long expectedLockVersion
) {}
