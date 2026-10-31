package com.turnoshift.unavailability.infrastructure.web.dto;

import com.turnoshift.unavailability.domain.CancellationInfo;
import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.RestrictedNote;

import java.time.Instant;
import java.util.UUID;

public record EmployeeUnavailabilityResponse(
        UUID id,
        UUID employeeId,
        Instant startsAt,
        Instant endsAt,
        String reasonCode,
        String restrictedNote,
        boolean cancelled,
        Instant cancelledAt,
        UUID cancelledByUserId,
        UUID createdByUserId,
        long lockVersion,
        Instant createdAt,
        Instant updatedAt
) {
    public static EmployeeUnavailabilityResponse from(EmployeeUnavailability u) {
        return new EmployeeUnavailabilityResponse(
                u.id(),
                u.employeeId(),
                u.interval().startsAt(),
                u.interval().endsAt(),
                u.reason().map(Enum::name).orElse(null),
                u.restrictedNote().map(RestrictedNote::value).orElse(null),
                u.isCancelled(),
                u.cancellationInfo().map(CancellationInfo::cancelledAt).orElse(null),
                u.cancellationInfo().map(CancellationInfo::cancelledByUserId).orElse(null),
                u.createdByUserId(),
                u.lockVersion(),
                u.createdAt(),
                u.updatedAt()
        );
    }
}
