package com.turnoshift.team.infrastructure.web;

import com.turnoshift.team.domain.Employee;

import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String code,
        String displayName,
        UUID positionId,
        UUID siteId,
        boolean active,
        long lockVersion
) {
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.id(),
                employee.code(),
                employee.displayName(),
                employee.positionId(),
                employee.siteId(),
                employee.isActive(),
                employee.lockVersion()
        );
    }
}
