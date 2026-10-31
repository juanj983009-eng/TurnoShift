package com.turnoshift.team.infrastructure;

import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeCodeAlreadyExistsException;
import com.turnoshift.team.domain.EmployeeRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para la persistencia de colaboradores/empleados en SQL Server.
 * Garantiza multi-tenancy estricto mediante filtrado obligatorio por organization_id,
 * control de concurrencia optimista vía lock_version, timestamps autoritativos con SYSUTCDATETIME()
 * y serialización de control de licencias con sp_getapplock (@LockOwner = 'Transaction').
 */
@Repository
public class JdbcEmployeeRepository implements EmployeeRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Employee> EMPLOYEE_ROW_MAPPER = (rs, rowNum) -> {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID orgId = UUID.fromString(rs.getString("organization_id"));
        String code = rs.getString("code");
        String displayName = rs.getString("display_name");
        UUID positionId = UUID.fromString(rs.getString("position_id"));
        UUID siteId = UUID.fromString(rs.getString("site_id"));
        boolean isActive = rs.getBoolean("is_active");
        long lockVersion = rs.getLong("lock_version");
        Instant createdAt = rs.getObject("created_at", OffsetDateTime.class).toInstant();
        Instant updatedAt = rs.getObject("updated_at", OffsetDateTime.class).toInstant();

        return Employee.reconstruct(
                id, orgId, code, displayName, positionId, siteId, isActive, lockVersion, createdAt, updatedAt
        );
    };

    public JdbcEmployeeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public Optional<Employee> findById(UUID organizationId, UUID employeeId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(employeeId, "employeeId no puede ser nulo");

        String sql = """
                SELECT id, organization_id, code, display_name, position_id, site_id,
                       is_active, lock_version, created_at, updated_at
                FROM employees
                WHERE organization_id = ? AND id = ?
                """;
        try {
            List<Employee> results = jdbcTemplate.query(sql, EMPLOYEE_ROW_MAPPER, organizationId, employeeId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new EmployeeDataAccessException(e);
        }
    }

    @Override
    public Optional<Employee> findByCode(UUID organizationId, String normalizedCode) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(normalizedCode, "normalizedCode no puede ser nulo");

        String sql = """
                SELECT id, organization_id, code, display_name, position_id, site_id,
                       is_active, lock_version, created_at, updated_at
                FROM employees
                WHERE organization_id = ? AND code = ?
                """;
        try {
            List<Employee> results = jdbcTemplate.query(sql, EMPLOYEE_ROW_MAPPER, organizationId, normalizedCode);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new EmployeeDataAccessException(e);
        }
    }

    @Override
    public List<Employee> findAll(UUID organizationId, Boolean activeFilter) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");

        try {
            if (activeFilter == null) {
                String sql = """
                        SELECT id, organization_id, code, display_name, position_id, site_id,
                               is_active, lock_version, created_at, updated_at
                        FROM employees
                        WHERE organization_id = ?
                        ORDER BY code ASC
                        """;
                return jdbcTemplate.query(sql, EMPLOYEE_ROW_MAPPER, organizationId);
            } else {
                String sql = """
                        SELECT id, organization_id, code, display_name, position_id, site_id,
                               is_active, lock_version, created_at, updated_at
                        FROM employees
                        WHERE organization_id = ? AND is_active = ?
                        ORDER BY code ASC
                        """;
                return jdbcTemplate.query(sql, EMPLOYEE_ROW_MAPPER, organizationId, activeFilter);
            }
        } catch (DataAccessException e) {
            throw new EmployeeDataAccessException(e);
        }
    }

    @Override
    public Employee insert(Employee employee) {
        Objects.requireNonNull(employee, "employee no puede ser nulo");

        String sql = """
                INSERT INTO employees (
                    id, organization_id, code, display_name, position_id, site_id,
                    is_active, lock_version, created_at, updated_at
                )
                OUTPUT inserted.id,
                       inserted.organization_id,
                       inserted.code,
                       inserted.display_name,
                       inserted.position_id,
                       inserted.site_id,
                       inserted.is_active,
                       inserted.lock_version,
                       inserted.created_at,
                       inserted.updated_at
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME(), SYSUTCDATETIME()
                );
                """;
        try {
            List<Employee> results = jdbcTemplate.query(
                    sql,
                    EMPLOYEE_ROW_MAPPER,
                    employee.id(),
                    employee.organizationId(),
                    employee.code(),
                    employee.displayName(),
                    employee.positionId(),
                    employee.siteId(),
                    employee.isActive(),
                    employee.lockVersion()
            );
            return results.get(0);
        } catch (DataAccessException e) {
            if (isUniqueCodeViolation(e)) {
                throw new EmployeeCodeAlreadyExistsException(employee.code());
            }
            throw e;
        }
    }

    @Override
    public Optional<Employee> update(Employee updatedEmployee, long expectedLockVersion) {
        Objects.requireNonNull(updatedEmployee, "updatedEmployee no puede ser nulo");

        String sql = """
                SET NOCOUNT ON;
                UPDATE employees
                SET display_name = ?,
                    position_id = ?,
                    is_active = ?,
                    lock_version = lock_version + 1,
                    updated_at = SYSUTCDATETIME()
                OUTPUT inserted.id,
                       inserted.organization_id,
                       inserted.code,
                       inserted.display_name,
                       inserted.position_id,
                       inserted.site_id,
                       inserted.is_active,
                       inserted.lock_version,
                       inserted.created_at,
                       inserted.updated_at
                WHERE organization_id = ?
                  AND id = ?
                  AND lock_version = ?;
                """;
        try {
            List<Employee> results = jdbcTemplate.query(
                    sql,
                    EMPLOYEE_ROW_MAPPER,
                    updatedEmployee.displayName(),
                    updatedEmployee.positionId(),
                    updatedEmployee.isActive(),
                    updatedEmployee.organizationId(),
                    updatedEmployee.id(),
                    expectedLockVersion
            );
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new EmployeeDataAccessException(e);
        }
    }

    @Override
    public long countActiveEmployees(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        String sql = "SELECT COUNT(*) FROM employees WHERE organization_id = ? AND is_active = 1";
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class, organizationId);
            return count != null ? count : 0L;
        } catch (DataAccessException e) {
            throw new EmployeeDataAccessException(e);
        }
    }

    @Override
    public int getMaxActiveEmployees(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        String sql = "SELECT max_active_employees FROM organizations WHERE id = ?";
        try {
            Integer max = jdbcTemplate.queryForObject(sql, Integer.class, organizationId);
            return max != null ? max : 0;
        } catch (DataAccessException e) {
            throw new EmployeeDataAccessException(e);
        }
    }

    @Override
    public void acquireOrganizationLicenseLock(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new EmployeeDataAccessException(
                    "No se puede adquirir el bloqueo de licencia de empleados: se requiere una transacción activa."
            );
        }

        String resourceName = "TurnoShift:Employees:Org:" + organizationId;
        String sql = """
                DECLARE @res INT;
                EXEC @res = sp_getapplock
                    @Resource = ?,
                    @LockMode = 'Exclusive',
                    @LockOwner = 'Transaction',
                    @LockTimeout = 10000;
                IF @res < 0
                BEGIN
                    RAISERROR('No se pudo adquirir el bloqueo de licencia de empleados (%d)', 16, 1, @res);
                END
                """;
        try {
            jdbcTemplate.update(sql, resourceName);
        } catch (DataAccessException e) {
            throw new EmployeeDataAccessException(e);
        }
    }

    private boolean isUniqueCodeViolation(DataAccessException e) {
        Throwable current = e;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("uq_employees_org_code")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
