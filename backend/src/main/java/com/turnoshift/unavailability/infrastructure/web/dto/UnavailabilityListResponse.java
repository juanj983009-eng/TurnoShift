package com.turnoshift.unavailability.infrastructure.web.dto;

import java.util.List;

public record UnavailabilityListResponse(
        List<EmployeeUnavailabilityResponse> items
) {
    public UnavailabilityListResponse {
        items = items != null ? List.copyOf(items) : List.of();
    }
}
