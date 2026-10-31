package com.turnoshift.team.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository {

    Optional<Employee> findById(UUID organizationId, UUID employeeId);

    Optional<Employee> findByCode(UUID organizationId, String normalizedCode);

    List<Employee> findAll(UUID organizationId, Boolean activeFilter);

    Employee insert(Employee employee);

    Optional<Employee> update(Employee employee, long expectedLockVersion);

    long countActiveEmployees(UUID organizationId);

    int getMaxActiveEmployees(UUID organizationId);

    void acquireOrganizationLicenseLock(UUID organizationId);
}
