package com.turnoshift.identity.infrastructure.persistence;

import com.turnoshift.identity.application.AuthenticationCandidateRepository;
import com.turnoshift.identity.domain.AuthenticationCandidate;
import com.turnoshift.identity.domain.AuthenticationDataAccessException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC para la búsqueda de candidatos de autenticación.
 *
 * <p>Ejecuta una consulta SQL parametrizada que combina {@code organizations}
 * y {@code app_users}. Prohibida la concatenación de valores en SQL.
 *
 * <p><b>Aislamiento multi-tenant:</b> la condición {@code o.code = ? AND u.normalized_email = ?}
 * unida por {@code u.organization_id = o.id} garantiza que un usuario de la organización A
 * jamás pueda ser recuperado mediante el código de la organización B.
 *
 * <p><b>Collation binaria:</b> {@code normalized_email} usa {@code Latin1_General_100_BIN2} (V8)
 * y {@code o.code} usa {@code Latin1_General_100_BIN2} (V1), asegurando comparaciones binarias
 * exactas sin depender de la configuración regional del servidor.
 *
 * <p><b>Distinción de fallos:</b>
 * <ul>
 *   <li>Usuario u organización no encontrados → {@code Optional.empty()} (resultado normal).</li>
 *   <li>Error real de acceso a datos (base de datos caída, timeout, red) →
 *       {@link AuthenticationDataAccessException} (error técnico, candidato a 503 en la capa HTTP).</li>
 *   <li>Dato corrupto en fila (UUID malformado) → {@code Optional.empty()} fail-closed,
 *       sin conceder acceso.</li>
 * </ul>
 */
@Repository
public class JdbcAuthenticationCandidateRepository implements AuthenticationCandidateRepository {

    private static final String SELECT_CANDIDATE_SQL = """
            SELECT
                u.id AS user_id,
                u.organization_id,
                u.normalized_email,
                u.display_name AS user_display_name,
                u.role_code,
                u.employee_id,
                u.is_active,
                u.auth_version,
                o.display_name AS org_display_name,
                o.timezone_id AS org_timezone_id,
                o.service_status AS org_service_status,
                u.password_hash
            FROM app_users u
            JOIN organizations o ON u.organization_id = o.id
            WHERE o.code = ?
              AND u.normalized_email = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CandidateRowMapper rowMapper = new CandidateRowMapper();

    public JdbcAuthenticationCandidateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * {@inheritDoc}
     *
     * @throws AuthenticationDataAccessException si la base de datos no puede atender la consulta.
     *         Esta excepción NO debe ser tratada como credenciales inválidas.
     */
    @Override
    public Optional<AuthenticationCandidate> findByOrgCodeAndEmail(
            String normalizedOrgCode, String normalizedEmail) {

        if (normalizedOrgCode == null || normalizedOrgCode.isBlank()
                || normalizedEmail == null || normalizedEmail.isBlank()) {
            return Optional.empty();
        }

        List<AuthenticationCandidate> results;
        try {
            results = jdbcTemplate.query(
                    SELECT_CANDIDATE_SQL,
                    rowMapper,
                    normalizedOrgCode,
                    normalizedEmail
            );
        } catch (DataAccessException e) {
            // Error real de acceso a datos: no es "usuario no encontrado".
            // Se propaga como excepción técnica de dominio sin exponer SQL, credenciales ni detalles.
            // La capa HTTP debe convertir esto en 503 Service Unavailable, nunca en 401.
            throw new AuthenticationDataAccessException(
                    "El servicio de autenticación no está disponible temporalmente", e);
        }

        if (results.size() == 1) {
            AuthenticationCandidate candidate = results.get(0);
            // Fail-closed: si el mapeo produjo null por dato corrupto (UUID inválido capturado
            // en CandidateRowMapper), retornar vacío sin conceder acceso.
            return Optional.ofNullable(candidate);
        }

        // Sin resultados → usuario/organización no existe (resultado normal).
        // Más de uno → violación de integridad inesperada → fail-closed.
        return Optional.empty();
    }

    private static class CandidateRowMapper implements RowMapper<AuthenticationCandidate> {
        @Override
        public AuthenticationCandidate mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                String userIdStr = rs.getString("user_id");
                String orgIdStr = rs.getString("organization_id");
                if (userIdStr == null || orgIdStr == null) {
                    return null;
                }

                UUID userId = UUID.fromString(userIdStr);
                UUID organizationId = UUID.fromString(orgIdStr);

                String empIdStr = rs.getString("employee_id");
                UUID employeeId = empIdStr != null ? UUID.fromString(empIdStr) : null;

                return new AuthenticationCandidate(
                        userId,
                        organizationId,
                        rs.getString("normalized_email"),
                        rs.getString("user_display_name"),
                        rs.getString("role_code"),
                        employeeId,
                        rs.getBoolean("is_active"),
                        rs.getLong("auth_version"),
                        rs.getString("org_display_name"),
                        rs.getString("org_timezone_id"),
                        rs.getString("org_service_status"),
                        rs.getString("password_hash")
                );
            } catch (IllegalArgumentException e) {
                // UUID malformado: dato corrupto en base de datos.
                // Fail-closed: retornar null para que el repositorio devuelva Optional.empty().
                // No se lanza DataAccessException porque esto es un dato corrupto en fila,
                // no un error de conectividad o acceso.
                return null;
            }
        }
    }
}
