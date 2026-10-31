package com.turnoshift.unavailability.infrastructure.web.dto;

public record CreateUnavailabilityRequest(
        String startsAt,
        String endsAt,
        Boolean allDay,
        String startDate,
        String endDate,
        String reasonCode,
        String restrictedNote
) {}
