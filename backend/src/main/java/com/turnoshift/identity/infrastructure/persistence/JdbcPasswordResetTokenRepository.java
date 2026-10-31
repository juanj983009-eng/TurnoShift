package com.turnoshift.identity.infrastructure.persistence;

import com.turnoshift.identity.domain.PasswordResetTokenRepository;
import com.turnoshift.identity.domain.ResetTokenPurpose;
import com.turnoshift.identity.domain.TokenDataAccessException;
import com.turnoshift.identity.domain.TokenInspectionCandidate;
import com.turnoshift.identity.domain.UserSetupEligibility;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para el ciclo de vida de tokens de configuración de contraseñas.
 *
 * <p>Asegura:
 * <ul>
 *   <li>Consultas SQL estrictamente parametrizadas sin interpolación de cadenas.</li>
 *   <li>Aislamiento multi-tenant mediante claves compuestas (organization_id, user_id).</li>
 *   <li>Consumo atómico y condicional de un solo ganador.</li>
 *   <li>Conversión de {@link DataAccessException} a {@link TokenDataAccessException} sin fugas de información.</li>
 * </ul>
 */
@Repository
public class JdbcPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private static final String SELECT_ELIGIBILITY_SQL = """
            SELECT
                u.organization_id,
                u.id AS user_id,
                o.service_status AS org_service_status,
                u.is_active,
                u.password_hash
            FROM app_users u
            JOIN organizations o ON u.organization_id = o.id
            WHERE u.organization_id = ?
              AND u.id = ?
            """;

    private static final String SELECT_ELIGIBILITY_FOR_EMISSION_SQL = """
            SELECT
                u.organization_id,
                u.id AS user_id,
                o.service_status AS org_service_status,
                u.is_active,
                u.password_hash
            FROM app_users u WITH (UPDLOCK, HOLDLOCK)
            JOIN organizations o WITH (UPDLOCK, HOLDLOCK) ON u.organization_id = o.id
            WHERE u.organization_id = ?
              AND u.id = ?
            """;

    private static final String REVOKE_UNCONSUMED_SQL = """
            UPDATE password_reset_tokens
            SET revoked_at = ?
            WHERE organization_id = ?
              AND user_id = ?
              AND purpose = ?
              AND consumed_at IS NULL
              AND revoked_at IS NULL
            """;

    private static final String INSERT_TOKEN_SQL = """
            INSERT INTO password_reset_tokens (
                id,
                organization_id,
                user_id,
                token_hash,
                purpose,
                expires_at,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_CANDIDATE_SQL = """
            SELECT
                prt.id AS token_id,
                prt.organization_id,
                prt.user_id,
                prt.purpose,
                prt.expires_at,
                prt.consumed_at,
                prt.revoked_at,
                u.is_active AS is_user_active,
                u.password_hash,
                o.service_status AS org_service_status
            FROM password_reset_tokens prt
            JOIN app_users u ON prt.organization_id = u.organization_id AND prt.user_id = u.id
            JOIN organizations o ON prt.organization_id = o.id
            WHERE prt.token_hash = ?
            """;

    private static final String UPDATE_CONSUME_SQL = """
            UPDATE prt
            SET prt.consumed_at = ?
            FROM password_reset_tokens prt
            JOIN app_users u ON prt.organization_id = u.organization_id AND prt.user_id = u.id
            JOIN organizations o ON prt.organization_id = o.id
            WHERE prt.token_hash = ?
              AND prt.purpose = ?
              AND prt.consumed_at IS NULL
              AND prt.revoked_at IS NULL
              AND prt.expires_at > ?
              AND u.is_active = 1
              AND u.password_hash IS NULL
              AND o.service_status = 'ACTIVA'
            """;

    private static final String SELECT_CONSUMED_INFO_SQL = """
            SELECT organization_id, user_id, purpose
            FROM password_reset_tokens
            WHERE token_hash = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EligibilityRowMapper eligibilityRowMapper = new EligibilityRowMapper();
    private final InspectionCandidateRowMapper inspectionCandidateRowMapper = new InspectionCandidateRowMapper();

    public JdbcPasswordResetTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public Optional<UserSetupEligibility> findEligibility(UUID organizationId, UUID userId) {
        if (organizationId == null || userId == null) {
            return Optional.empty();
        }

        try {
            List<UserSetupEligibility> results = jdbcTemplate.query(
                    SELECT_ELIGIBILITY_SQL,
                    eligibilityRowMapper,
                    organizationId,
                    userId
            );

            if (results.size() == 1) {
                return Optional.ofNullable(results.get(0));
            }
            return Optional.empty();
        } catch (DataAccessException e) {
            throw new TokenDataAccessException(e);
        }
    }

    @Override
    public Optional<UserSetupEligibility> findEligibilityForEmission(UUID organizationId, UUID userId) {
        if (organizationId == null || userId == null) {
            return Optional.empty();
        }

        try {
            List<UserSetupEligibility> results = jdbcTemplate.query(
                    SELECT_ELIGIBILITY_FOR_EMISSION_SQL,
                    eligibilityRowMapper,
                    organizationId,
                    userId
            );

            if (results.size() == 1) {
                return Optional.ofNullable(results.get(0));
            }
            return Optional.empty();
        } catch (DataAccessException e) {
            throw new TokenDataAccessException(e);
        }
    }

    @Override
    public void revokeUnconsumedTokens(UUID organizationId, UUID userId, ResetTokenPurpose purpose, Instant revokedAt) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(userId, "userId no puede ser nulo");
        Objects.requireNonNull(purpose, "purpose no puede ser nulo");
        Objects.requireNonNull(revokedAt, "revokedAt no puede ser nulo");

        try {
            jdbcTemplate.update(
                    REVOKE_UNCONSUMED_SQL,
                    toOffsetDateTime(revokedAt),
                    organizationId,
                    userId,
                    purpose.name()
            );
        } catch (DataAccessException e) {
            throw new TokenDataAccessException(e);
        }
    }

    @Override
    public void insertToken(
            UUID tokenId,
            UUID organizationId,
            UUID userId,
            byte[] tokenHash,
            ResetTokenPurpose purpose,
            Instant expiresAt,
            Instant createdAt
    ) {
        Objects.requireNonNull(tokenId, "tokenId no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(userId, "userId no puede ser nulo");
        Objects.requireNonNull(tokenHash, "tokenHash no puede ser nulo");
        Objects.requireNonNull(purpose, "purpose no puede ser nulo");
        Objects.requireNonNull(expiresAt, "expiresAt no puede ser nulo");
        Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");

        try {
            jdbcTemplate.update(
                    INSERT_TOKEN_SQL,
                    tokenId,
                    organizationId,
                    userId,
                    tokenHash,
                    purpose.name(),
                    toOffsetDateTime(expiresAt),
                    toOffsetDateTime(createdAt)
            );
        } catch (DataAccessException e) {
            throw new TokenDataAccessException(e);
        }
    }

    @Override
    public Optional<TokenInspectionCandidate> findCandidateForInspection(byte[] tokenHash) {
        if (tokenHash == null || tokenHash.length != 32) {
            return Optional.empty();
        }

        try {
            List<TokenInspectionCandidate> results = jdbcTemplate.query(
                    SELECT_CANDIDATE_SQL,
                    inspectionCandidateRowMapper,
                    tokenHash
            );

            if (results.size() == 1) {
                return Optional.ofNullable(results.get(0));
            }
            return Optional.empty();
        } catch (DataAccessException e) {
            throw new TokenDataAccessException(e);
        }
    }

    @Override
    public Optional<ConsumedTokenInfo> consumeToken(byte[] tokenHash, ResetTokenPurpose purpose, Instant consumedAt) {
        if (tokenHash == null || purpose == null || consumedAt == null) {
            return Optional.empty();
        }

        OffsetDateTime consumedAtOdt = toOffsetDateTime(consumedAt);
        try {
            int rowsUpdated = jdbcTemplate.update(
                    UPDATE_CONSUME_SQL,
                    consumedAtOdt,
                    tokenHash,
                    purpose.name(),
                    consumedAtOdt
            );

            if (rowsUpdated != 1) {
                return Optional.empty();
            }

            List<ConsumedTokenInfo> results = jdbcTemplate.query(
                    SELECT_CONSUMED_INFO_SQL,
                    (rs, rowNum) -> {
                        UUID orgId = rs.getObject("organization_id", UUID.class);
                        UUID userId = rs.getObject("user_id", UUID.class);
                        String pStr = rs.getString("purpose");
                        if (orgId == null || userId == null || pStr == null) {
                            throw new SQLException("Fila de token consumido incompleta en base de datos");
                        }
                        ResetTokenPurpose p;
                        try {
                            p = ResetTokenPurpose.valueOf(pStr);
                        } catch (IllegalArgumentException ex) {
                            throw new SQLException("Propósito de token consumido no reconocido: " + pStr, ex);
                        }
                        return new ConsumedTokenInfo(orgId, userId, p);
                    },
                    tokenHash
            );

            if (results.size() == 1) {
                return Optional.ofNullable(results.get(0));
            }
            return Optional.empty();
        } catch (DataAccessException e) {
            throw new TokenDataAccessException(e);
        }
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private static class EligibilityRowMapper implements RowMapper<UserSetupEligibility> {
        @Override
        public UserSetupEligibility mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID orgId = rs.getObject("organization_id", UUID.class);
            UUID userId = rs.getObject("user_id", UUID.class);
            String orgStatus = rs.getString("org_service_status");
            boolean isActive = rs.getBoolean("is_active");
            String passwordHash = rs.getString("password_hash");

            if (orgId == null || userId == null || orgStatus == null) {
                throw new SQLException("Fila de aptitud incompleta en base de datos");
            }

            boolean hasPassword = passwordHash != null && !passwordHash.isBlank();
            return new UserSetupEligibility(orgId, userId, orgStatus, isActive, hasPassword);
        }
    }

    private static class InspectionCandidateRowMapper implements RowMapper<TokenInspectionCandidate> {
        @Override
        public TokenInspectionCandidate mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID tokenId = rs.getObject("token_id", UUID.class);
            UUID orgId = rs.getObject("organization_id", UUID.class);
            UUID userId = rs.getObject("user_id", UUID.class);
            String purposeStr = rs.getString("purpose");
            OffsetDateTime expiresAtOdt = rs.getObject("expires_at", OffsetDateTime.class);
            OffsetDateTime consumedAtOdt = rs.getObject("consumed_at", OffsetDateTime.class);
            OffsetDateTime revokedAtOdt = rs.getObject("revoked_at", OffsetDateTime.class);
            boolean isUserActive = rs.getBoolean("is_user_active");
            String passwordHash = rs.getString("password_hash");
            String orgStatus = rs.getString("org_service_status");

            if (tokenId == null || orgId == null || userId == null || purposeStr == null
                    || expiresAtOdt == null || orgStatus == null) {
                throw new SQLException("Fila de token de configuración incompleta en base de datos");
            }

            ResetTokenPurpose purpose;
            try {
                purpose = ResetTokenPurpose.valueOf(purposeStr);
            } catch (IllegalArgumentException e) {
                throw new SQLException("Propósito de token corrupto o no reconocido: " + purposeStr, e);
            }

            boolean hasPassword = passwordHash != null && !passwordHash.isBlank();

            return new TokenInspectionCandidate(
                    tokenId,
                    orgId,
                    userId,
                    purpose,
                    expiresAtOdt.toInstant(),
                    consumedAtOdt != null ? consumedAtOdt.toInstant() : null,
                    revokedAtOdt != null ? revokedAtOdt.toInstant() : null,
                    isUserActive,
                    hasPassword,
                    orgStatus
            );
        }
    }
}
