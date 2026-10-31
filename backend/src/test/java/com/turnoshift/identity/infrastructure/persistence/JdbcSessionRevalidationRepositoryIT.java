package com.turnoshift.identity.infrastructure.persistence;

import com.turnoshift.identity.domain.AuthenticationDataAccessException;
import com.turnoshift.identity.domain.SessionRevalidationCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: JdbcSessionRevalidationRepository — consulta de estado vigente")
class JdbcSessionRevalidationRepositoryIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense()
                    .withStartupTimeout(java.time.Duration.ofMinutes(5));

    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Autowired
    JdbcSessionRevalidationRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private UUID insertarOrganizacion(String code, String displayName, String status, String timezone) {
        UUID orgId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, ?, ?, 10, 1)
                """, orgId.toString(), code, displayName, status, timezone);
        return orgId;
    }

    private UUID insertarUsuario(UUID orgId, String email, String displayName, String roleCode,
                                 boolean isActive, long authVersion) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, ?, 'dummy-hash', ?, ?, ?)
                """, userId.toString(), orgId.toString(), email, email.toLowerCase(), displayName,
                roleCode, isActive ? 1 : 0, authVersion);
        return userId;
    }

    @Test
    @DisplayName("Búsqueda exitosa devuelve candidato completo con datos vigentes")
    void busquedaExitosa_devuelveCandidatoCompleto() {
        UUID orgId = insertarOrganizacion("CORP-REVAL", "Reval Corp S.A.", "ACTIVA", "America/Bogota");
        UUID userId = insertarUsuario(orgId, "ana.reval@test.com", "Ana Reval", "ADMINISTRADOR", true, 2L);

        Optional<SessionRevalidationCandidate> result = repository.findCandidate(userId, orgId);

        assertThat(result).isPresent();
        SessionRevalidationCandidate candidate = result.get();
        assertThat(candidate.userId()).isEqualTo(userId);
        assertThat(candidate.organizationId()).isEqualTo(orgId);
        assertThat(candidate.displayName()).isEqualTo("Ana Reval");
        assertThat(candidate.roleCode()).isEqualTo("ADMINISTRADOR");
        assertThat(candidate.isActive()).isTrue();
        assertThat(candidate.authVersion()).isEqualTo(2L);
        assertThat(candidate.organizationStatus()).isEqualTo("ACTIVA");
        assertThat(candidate.organizationDisplayName()).isEqualTo("Reval Corp S.A.");
        assertThat(candidate.organizationTimeZone()).isEqualTo("America/Bogota");
    }

    @Test
    @DisplayName("Usuario inactivo: devuelve candidato con isActive = false")
    void usuarioInactivo_devuelveIsActiveFalse() {
        UUID orgId = insertarOrganizacion("CORP-INACT", "Inact Corp S.A.", "ACTIVA", "America/Lima");
        UUID userId = insertarUsuario(orgId, "inactivo@test.com", "Juan Inactivo", "COLABORADOR", false, 1L);

        Optional<SessionRevalidationCandidate> result = repository.findCandidate(userId, orgId);

        assertThat(result).isPresent();
        assertThat(result.get().isActive()).isFalse();
    }

    @Test
    @DisplayName("Organización suspendida: devuelve candidato con organizationStatus = SUSPENDIDA")
    void organizacionSuspendida_devuelveStatusSuspendida() {
        UUID orgId = insertarOrganizacion("CORP-SUSP", "Susp Corp S.A.", "SUSPENDIDA", "America/Lima");
        UUID userId = insertarUsuario(orgId, "susp@test.com", "Pedro Susp", "SUPERVISOR", true, 1L);

        Optional<SessionRevalidationCandidate> result = repository.findCandidate(userId, orgId);

        assertThat(result).isPresent();
        assertThat(result.get().organizationStatus()).isEqualTo("SUSPENDIDA");
    }

    @Test
    @DisplayName("Aislamiento multi-tenant: userId con organizationId ajeno devuelve Optional.empty()")
    void aislamientoMultiTenant_userIdConOrgAjenoDevuelveVacio() {
        UUID org1 = insertarOrganizacion("CORP-1", "Corp 1", "ACTIVA", "America/Lima");
        UUID org2 = insertarOrganizacion("CORP-2", "Corp 2", "ACTIVA", "America/Lima");
        UUID user1 = insertarUsuario(org1, "user1@corp1.com", "User 1", "ADMINISTRADOR", true, 1L);

        Optional<SessionRevalidationCandidate> result = repository.findCandidate(user1, org2);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Usuario inexistente: devuelve Optional.empty()")
    void usuarioInexistente_devuelveVacio() {
        UUID orgId = insertarOrganizacion("CORP-EMPTY", "Corp Empty", "ACTIVA", "America/Lima");

        Optional<SessionRevalidationCandidate> result = repository.findCandidate(UUID.randomUUID(), orgId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Fallo de conexión SQL Server: propaga AuthenticationDataAccessException")
    void falloConexion_propagaAuthenticationDataAccessException() {
        DriverManagerDataSource badDataSource = new DriverManagerDataSource();
        badDataSource.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        badDataSource.setUrl("jdbc:sqlserver://127.0.0.1:54321;databaseName=turnoshift_dev;loginTimeout=1");
        badDataSource.setUsername("sa");
        badDataSource.setPassword("wrong-password");

        JdbcSessionRevalidationRepository badRepo =
                new JdbcSessionRevalidationRepository(new JdbcTemplate(badDataSource));

        assertThatThrownBy(() -> badRepo.findCandidate(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(AuthenticationDataAccessException.class)
                .hasMessage("El servicio de base de datos no está disponible temporalmente");
    }
}
