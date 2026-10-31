package com.turnoshift.unavailability.infrastructure.web.dto;

public record UpdateUnavailabilityRequest(
        String startsAt,
        String endsAt,
        Boolean allDay,
        String startDate,
        String endDate,
        String reasonCode,
        String restrictedNote,
        Long expectedLockVersion
) {}
