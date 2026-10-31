package com.turnoshift.identity.infrastructure.persistence;

import com.turnoshift.identity.domain.AppUserPasswordRepository;
import com.turnoshift.identity.domain.PasswordSetupDataAccessException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador JDBC para la actualización de contraseñas de cuentas de usuario en SQL Server.
 */
@Repository
public class JdbcAppUserPasswordRepository implements AppUserPasswordRepository {

    private static final int MAX_HASH_LENGTH = 512;

    private static final String UPDATE_INITIAL_PASSWORD_SQL = """
            UPDATE u
            SET u.password_hash = ?,
                u.password_changed_at = ?,
                u.auth_version = u.auth_version + 1,
                u.updated_at = ?
            FROM app_users u
            JOIN organizations o ON u.organization_id = o.id
            WHERE u.organization_id = ?
              AND u.id = ?
              AND u.is_active = 1
              AND u.password_hash IS NULL
              AND o.service_status = 'ACTIVA'
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAppUserPasswordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    @Override
    public boolean setInitialPassword(UUID organizationId, UUID userId, String passwordHash, Instant now) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(userId, "userId no puede ser nulo");
        Objects.requireNonNull(passwordHash, "passwordHash no puede ser nulo");
        Objects.requireNonNull(now, "now no puede ser nulo");

        if (passwordHash.length() > MAX_HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "El hash de contraseña excede la capacidad máxima de " + MAX_HASH_LENGTH + " caracteres."
            );
        }

        OffsetDateTime timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);

        try {
            int rowsUpdated = jdbcTemplate.update(
                    UPDATE_INITIAL_PASSWORD_SQL,
                    passwordHash,
                    timestamp,
                    timestamp,
                    organizationId.toString(),
                    userId.toString()
            );
            return rowsUpdated == 1;
        } catch (DataAccessException e) {
            throw new PasswordSetupDataAccessException(e);
        }
    }
}
