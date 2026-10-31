package com.turnoshift.organization.infrastructure;

import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.organization.domain.OrganizationView;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para consultar y actualizar la configuración de organización y sede única en SQL Server.
 */
@Repository
public class JdbcOrganizationConfigurationRepository implements OrganizationConfigurationRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<OrganizationView> ORG_ROW_MAPPER = (rs, rowNum) -> new OrganizationView(
            rs.getObject("id", UUID.class),
            rs.getString("display_name"),
            rs.getString("timezone_id"),
            rs.getLong("lock_version")
    );

    private static final RowMapper<ActiveSiteView> SITE_ROW_MAPPER = (rs, rowNum) -> new ActiveSiteView(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getLong("lock_version")
    );

    public JdbcOrganizationConfigurationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public Optional<OrganizationView> findOrganizationById(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");

        String sql = """
                SELECT id, display_name, timezone_id, lock_version
                FROM organizations
                WHERE id = ?;
                """;
        try {
            List<OrganizationView> results = jdbcTemplate.query(sql, ORG_ROW_MAPPER, organizationId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new OrganizationConfigurationDataAccessException(e);
        }
    }

    @Override
    public Optional<ActiveSiteView> findActiveSiteByOrganizationId(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");

        String sql = """
                SELECT id, code, name, lock_version
                FROM sites
                WHERE organization_id = ?
                  AND is_active = 1;
                """;
        try {
            List<ActiveSiteView> results = jdbcTemplate.query(sql, SITE_ROW_MAPPER, organizationId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new OrganizationConfigurationDataAccessException(e);
        }
    }

    @Override
    public Optional<OrganizationView> updateOrganizationDisplayName(
            UUID organizationId,
            String newDisplayName,
            long expectedLockVersion
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(newDisplayName, "newDisplayName no puede ser nulo");

        String sql = """
                SET NOCOUNT ON;
                UPDATE organizations
                SET display_name = ?,
                    lock_version = lock_version + 1,
                    updated_at = SYSUTCDATETIME()
                OUTPUT inserted.id,
                       inserted.display_name,
                       inserted.timezone_id,
                       inserted.lock_version
                WHERE id = ?
                  AND lock_version = ?;
                """;
        try {
            List<OrganizationView> results = jdbcTemplate.query(
                    sql,
                    ORG_ROW_MAPPER,
                    newDisplayName,
                    organizationId,
                    expectedLockVersion
            );
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new OrganizationConfigurationDataAccessException(e);
        }
    }

    @Override
    public Optional<ActiveSiteView> updateActiveSiteName(
            UUID organizationId,
            String newSiteName,
            long expectedLockVersion
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(newSiteName, "newSiteName no puede ser nulo");

        String sql = """
                SET NOCOUNT ON;
                UPDATE sites
                SET name = ?,
                    lock_version = lock_version + 1,
                    updated_at = SYSUTCDATETIME()
                OUTPUT inserted.id,
                       inserted.code,
                       inserted.name,
                       inserted.lock_version
                WHERE organization_id = ?
                  AND is_active = 1
                  AND lock_version = ?;
                """;
        try {
            List<ActiveSiteView> results = jdbcTemplate.query(
                    sql,
                    SITE_ROW_MAPPER,
                    newSiteName,
                    organizationId,
                    expectedLockVersion
            );
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new OrganizationConfigurationDataAccessException(e);
        }
    }
}
