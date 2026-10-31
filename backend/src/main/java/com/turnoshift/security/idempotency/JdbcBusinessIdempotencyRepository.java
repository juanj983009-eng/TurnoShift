package com.turnoshift.security.idempotency;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para el registro y coordinación de idempotencia empresarial en SQL Server.
 */
@Repository
public class JdbcBusinessIdempotencyRepository implements BusinessIdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcBusinessIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public void acquireLock(String resourceName, int timeoutMs) {
        Objects.requireNonNull(resourceName, "resourceName no puede ser nulo");
        String sql = """
                IF @@TRANCOUNT = 0 BEGIN TRANSACTION;
                DECLARE @res INT;
                EXEC @res = sp_getapplock
                    @Resource = ?,
                    @LockMode = 'Exclusive',
                    @LockOwner = 'Transaction',
                    @LockTimeout = ?;
                IF @res < 0
                BEGIN
                    RAISERROR('No se pudo adquirir el bloqueo de aplicacion (%d)', 16, 1, @res);
                END
                """;
        try {
            jdbcTemplate.update(sql, resourceName, timeoutMs);
        } catch (DataAccessException e) {
            throw new BusinessIdempotencyDataAccessException(e);
        }
    }

    @Override
    public Optional<BusinessApiOperationRecord> findOperation(UUID organizationId, UUID actorUserId, UUID idempotencyKey) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey no puede ser nulo");

        String sql = """
                SELECT id, organization_id, actor_user_id, idempotency_key, request_fingerprint,
                       status, outcome, http_status, response_content_type, response_body,
                       created_at, completed_at, expires_at
                FROM api_operations
                WHERE organization_id = ? AND actor_user_id = ? AND idempotency_key = ?
                """;
        try {
            List<BusinessApiOperationRecord> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
                UUID id = UUID.fromString(rs.getString("id"));
                UUID orgId = UUID.fromString(rs.getString("organization_id"));
                UUID userId = UUID.fromString(rs.getString("actor_user_id"));
                UUID key = UUID.fromString(rs.getString("idempotency_key"));
                byte[] fingerprint = rs.getBytes("request_fingerprint");
                String status = rs.getString("status");
                String outcome = rs.getString("outcome");
                Integer httpStatus = (Integer) rs.getObject("http_status");
                String contentType = rs.getString("response_content_type");
                String body = rs.getString("response_body");
                Instant createdAt = rs.getObject("created_at", OffsetDateTime.class).toInstant();
                OffsetDateTime completedOffset = rs.getObject("completed_at", OffsetDateTime.class);
                Instant completedAt = completedOffset != null ? completedOffset.toInstant() : null;
                Instant expiresAt = rs.getObject("expires_at", OffsetDateTime.class).toInstant();
                return new BusinessApiOperationRecord(
                        id, orgId, userId, key, fingerprint, status, outcome, httpStatus, contentType, body, createdAt, completedAt, expiresAt);
            }, organizationId, actorUserId, idempotencyKey);

            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new BusinessIdempotencyDataAccessException(e);
        }
    }

    @Override
    public void saveCompletedOperation(
            UUID organizationId,
            UUID actorUserId,
            UUID idempotencyKey,
            String method,
            String path,
            byte[] fingerprint,
            String schemaVersion,
            String outcome,
            int httpStatus,
            String responseContentType,
            String responseBody,
            Instant completedAt,
            Instant expiresAt
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey no puede ser nulo");
        Objects.requireNonNull(method, "method no puede ser nulo");
        Objects.requireNonNull(path, "path no puede ser nulo");
        Objects.requireNonNull(fingerprint, "fingerprint no puede ser nulo");
        Objects.requireNonNull(schemaVersion, "schemaVersion no puede ser nulo");
        Objects.requireNonNull(outcome, "outcome no puede ser nulo");
        Objects.requireNonNull(expiresAt, "expiresAt no puede ser nulo");

        String sql = """
                INSERT INTO api_operations (
                    id, organization_id, actor_user_id, actor_operator_id, idempotency_key,
                    request_method, request_path, request_fingerprint, request_schema_version,
                    status, outcome, http_status, response_content_type, response_body,
                    response_location, completed_at, expires_at
                ) VALUES (
                    NEWID(), ?, ?, NULL, ?,
                    ?, ?, ?, ?,
                    'COMPLETED', ?, ?, ?, ?,
                    NULL, ?, ?
                )
                """;
        try {
            jdbcTemplate.update(
                    sql,
                    organizationId,
                    actorUserId,
                    idempotencyKey,
                    method,
                    path,
                    fingerprint,
                    schemaVersion,
                    outcome,
                    httpStatus,
                    responseContentType,
                    responseBody,
                    completedAt != null ? OffsetDateTime.ofInstant(completedAt, ZoneOffset.UTC) : null,
                    OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
            );
        } catch (DataAccessException e) {
            throw new BusinessIdempotencyDataAccessException(e);
        }
    }
}
