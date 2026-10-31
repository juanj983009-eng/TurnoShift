package com.turnoshift.unavailability.domain;

import java.util.UUID;

public interface EmployeeValidationPort {

    boolean isEmployeeActive(UUID organizationId, UUID employeeId);
}
