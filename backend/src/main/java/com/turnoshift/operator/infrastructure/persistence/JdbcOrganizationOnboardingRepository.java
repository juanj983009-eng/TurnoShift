package com.turnoshift.operator.infrastructure.persistence;

import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.OrganizationOnboardingRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador JDBC para la persistencia de organizaciones, sedes y administradores en SQL Server.
 */
@Repository
public class JdbcOrganizationOnboardingRepository implements OrganizationOnboardingRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrganizationOnboardingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public boolean existsOrganizationCode(String orgCode) {
        Objects.requireNonNull(orgCode, "orgCode no puede ser nulo");
        String sql = "SELECT COUNT(*) FROM organizations WHERE code = ?";
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, orgCode);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            throw new OperatorDataAccessException();
        }
    }

    @Override
    public void insertOrganizationSiteAndAdmin(
            UUID organizationId,
            String orgCode,
            String orgDisplayName,
            String timeZoneId,
            int maxActiveEmployees,
            UUID siteId,
            String siteCode,
            String siteDisplayName,
            UUID adminId,
            String adminEmail,
            String adminNormalizedEmail,
            String adminDisplayName
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(orgCode, "orgCode no puede ser nulo");
        Objects.requireNonNull(orgDisplayName, "orgDisplayName no puede ser nulo");
        Objects.requireNonNull(timeZoneId, "timeZoneId no puede ser nulo");
        Objects.requireNonNull(siteId, "siteId no puede ser nulo");
        Objects.requireNonNull(siteCode, "siteCode no puede ser nulo");
        Objects.requireNonNull(siteDisplayName, "siteDisplayName no puede ser nulo");
        Objects.requireNonNull(adminId, "adminId no puede ser nulo");
        Objects.requireNonNull(adminEmail, "adminEmail no puede ser nulo");
        Objects.requireNonNull(adminNormalizedEmail, "adminNormalizedEmail no puede ser nulo");
        Objects.requireNonNull(adminDisplayName, "adminDisplayName no puede ser nulo");

        String insertOrgSql = """
                INSERT INTO organizations (
                    id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites, current_policy_version_id
                ) VALUES (
                    ?, ?, ?, 'ACTIVA', ?,
                    ?, 1, NULL
                )
                """;

        String insertSiteSql = """
                INSERT INTO sites (
                    organization_id, id, code, name, is_active
                ) VALUES (
                    ?, ?, ?, ?, 1
                )
                """;

        String insertAdminSql = """
                INSERT INTO app_users (
                    organization_id, id, email, normalized_email, display_name,
                    password_hash, role_code, is_active, employee_id, must_change_password,
                    auth_version, lock_version
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    NULL, 'ADMINISTRADOR', 1, NULL, 0,
                    0, 0
                )
                """;

        try {
            jdbcTemplate.update(insertOrgSql, organizationId, orgCode, orgDisplayName, timeZoneId, maxActiveEmployees);
            jdbcTemplate.update(insertSiteSql, organizationId, siteId, siteCode, siteDisplayName);
            jdbcTemplate.update(insertAdminSql, organizationId, adminId, adminEmail, adminNormalizedEmail, adminDisplayName);
        } catch (DataAccessException e) {
            throw new OperatorDataAccessException();
        }
    }
}
