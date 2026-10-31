package com.turnoshift.identity.infrastructure.persistence;

import com.turnoshift.identity.domain.AuthenticationCandidate;
import com.turnoshift.identity.domain.AuthenticationDataAccessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
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

/**
 * Prueba de integración para {@link JdbcAuthenticationCandidateRepository}.
 *
 * <p>Verifica sobre una base de datos SQL Server real (Testcontainers) que:
 * <ul>
 *   <li>La búsqueda combina correctamente código de empresa y correo normalizado.</li>
 *   <li>El aislamiento multi-tenant se respeta estrictamente.</li>
 *   <li>El mismo correo normalizado puede coexistir en dos empresas distintas.</li>
 *   <li>Se recuperan los atributos empresariales y de usuario correctos.</li>
 *   <li>El SQL es parametrizado y seguro contra inyecciones.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: JdbcAuthenticationCandidateRepository — búsqueda y aislamiento multi-tenant")
class JdbcAuthenticationCandidateRepositoryIT {

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
    JdbcAuthenticationCandidateRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private String insertarOrganizacion(String code, String displayName, String status, String timezone) {
        String orgId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, ?, ?, 10, 1)
                """, orgId, code, displayName, status, timezone);
        return orgId;
    }

    private String insertarUsuario(String orgId, String email, String normalizedEmail,
                                   String displayName, String roleCode, String passwordHash,
                                   boolean isActive, long authVersion) {
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO app_users
                    (id, organization_id, email, normalized_email, display_name, password_hash,
                     role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, orgId, email, normalizedEmail, displayName, passwordHash,
                roleCode, isActive ? 1 : 0, authVersion);
        return userId;
    }

    // =========================================================================
    // 1. Búsqueda correcta por código + correo normalizado
    // =========================================================================

    @Test
    @DisplayName("Búsqueda exitosa por código de empresa y correo normalizado")
    void busquedaCorrectaPorCodigoEmpresaYCorreoNormalizado() {
        String orgId = insertarOrganizacion("DEMO-CORP", "Demo Corp S.A.", "ACTIVA", "America/Lima");
        String userId = insertarUsuario(orgId, "carlos@democorp.example", "carlos@democorp.example",
                "Carlos Perez", "ADMINISTRADOR", "hash-dummy-carlos", true, 3L);

        Optional<AuthenticationCandidate> opt = repository.findByOrgCodeAndEmail("DEMO-CORP", "carlos@democorp.example");

        assertThat(opt).isPresent();
        AuthenticationCandidate candidate = opt.get();
        assertThat(candidate.userId()).isEqualTo(UUID.fromString(userId));
        assertThat(candidate.organizationId()).isEqualTo(UUID.fromString(orgId));
        assertThat(candidate.normalizedEmail()).isEqualTo("carlos@democorp.example");
        assertThat(candidate.displayName()).isEqualTo("Carlos Perez");
        assertThat(candidate.roleCode()).isEqualTo("ADMINISTRADOR");
        assertThat(candidate.isActive()).isTrue();
        assertThat(candidate.authVersion()).isEqualTo(3L);
        assertThat(candidate.organizationDisplayName()).isEqualTo("Demo Corp S.A.");
        assertThat(candidate.organizationTimeZone()).isEqualTo("America/Lima");
        assertThat(candidate.organizationStatus()).isEqualTo("ACTIVA");
        assertThat(candidate.passwordHash()).isEqualTo("hash-dummy-carlos");
    }

    // =========================================================================
    // 2. Insensible a mayúsculas mediante normalización de aplicación
    // =========================================================================

    @Test
    @DisplayName("Código de empresa insensible a mayúsculas vía valor normalizado por la aplicación")
    void codigoEmpresaInsensibleAMayusculasMedianteNormalizacion() {
        String orgId = insertarOrganizacion("LIMA-CAFE", "Café Lima", "ACTIVA", "America/Lima");
        insertarUsuario(orgId, "maria@lima.example", "maria@lima.example",
                "Maria", "SUPERVISOR", "hash-maria", true, 1L);

        // La base almacena 'LIMA-CAFE' (collation BIN2).
        // La aplicación normaliza 'lima-cafe' -> 'LIMA-CAFE'.
        String inputNormalizado = "lima-cafe".trim().toUpperCase();
        Optional<AuthenticationCandidate> opt = repository.findByOrgCodeAndEmail(inputNormalizado, "maria@lima.example");

        assertThat(opt).isPresent();
        assertThat(opt.get().organizationDisplayName()).isEqualTo("Café Lima");
    }

    // =========================================================================
    // 3. Mismo correo normalizado permitido y recuperado en dos empresas
    // =========================================================================

    @Test
    @DisplayName("Mismo correo normalizado en dos empresas distintas se recupera de forma aislada")
    void mismoCorreoNormalizadoEnDosEmpresasDistintas() {
        String orgA = insertarOrganizacion("ORG-ALFA", "Empresa Alfa", "ACTIVA", "UTC");
        String userAId = insertarUsuario(orgA, "shared@empleado.com", "shared@empleado.com",
                "Empleado en Alfa", "COLABORADOR", "hash-alfa", true, 1L);

        String orgB = insertarOrganizacion("ORG-BETA", "Empresa Beta", "ACTIVA", "America/Bogota");
        String userBId = insertarUsuario(orgB, "shared@empleado.com", "shared@empleado.com",
                "Empleado en Beta", "ADMINISTRADOR", "hash-beta", true, 2L);

        // Buscar en Alfa
        Optional<AuthenticationCandidate> candAlfa = repository.findByOrgCodeAndEmail("ORG-ALFA", "shared@empleado.com");
        assertThat(candAlfa).isPresent();
        assertThat(candAlfa.get().userId()).isEqualTo(UUID.fromString(userAId));
        assertThat(candAlfa.get().organizationId()).isEqualTo(UUID.fromString(orgA));
        assertThat(candAlfa.get().displayName()).isEqualTo("Empleado en Alfa");
        assertThat(candAlfa.get().roleCode()).isEqualTo("COLABORADOR");

        // Buscar en Beta
        Optional<AuthenticationCandidate> candBeta = repository.findByOrgCodeAndEmail("ORG-BETA", "shared@empleado.com");
        assertThat(candBeta).isPresent();
        assertThat(candBeta.get().userId()).isEqualTo(UUID.fromString(userBId));
        assertThat(candBeta.get().organizationId()).isEqualTo(UUID.fromString(orgB));
        assertThat(candBeta.get().displayName()).isEqualTo("Empleado en Beta");
        assertThat(candBeta.get().roleCode()).isEqualTo("ADMINISTRADOR");
    }

    // =========================================================================
    // 4. Código de empresa A nunca recupera usuario de B
    // =========================================================================

    @Test
    @DisplayName("Aislamiento: código de empresa A no recupera usuario exclusivo de empresa B")
    void codigoEmpresaANoRecuperaUsuarioDeB() {
        String orgA = insertarOrganizacion("ORG-SOLO-A", "Organización A", "ACTIVA", "UTC");
        String orgB = insertarOrganizacion("ORG-SOLO-B", "Organización B", "ACTIVA", "UTC");

        insertarUsuario(orgB, "exclusivo.b@test.com", "exclusivo.b@test.com",
                "Usuario B", "SUPERVISOR", "hash-b", true, 0L);

        // Intentar recuperar el usuario de B usando el código de A
        Optional<AuthenticationCandidate> opt = repository.findByOrgCodeAndEmail("ORG-SOLO-A", "exclusivo.b@test.com");

        assertThat(opt).isEmpty();
    }

    // =========================================================================
    // 5. Usuario inexistente devuelve ausencia, no datos parciales
    // =========================================================================

    @Test
    @DisplayName("Usuario inexistente devuelve Optional.empty sin datos parciales")
    void usuarioInexistenteDevuelveAusencia() {
        insertarOrganizacion("ORG-VACIA", "Org Vacía", "ACTIVA", "UTC");

        // Empresa existe pero correo no
        Optional<AuthenticationCandidate> opt1 = repository.findByOrgCodeAndEmail("ORG-VACIA", "noexiste@test.com");
        assertThat(opt1).isEmpty();

        // Empresa no existe pero correo podría existir
        Optional<AuthenticationCandidate> opt2 = repository.findByOrgCodeAndEmail("ORG-INEXISTENTE", "alguien@test.com");
        assertThat(opt2).isEmpty();

        // Parámetros nulos o vacíos
        assertThat(repository.findByOrgCodeAndEmail(null, "a@b.com")).isEmpty();
        assertThat(repository.findByOrgCodeAndEmail("ORG-VACIA", null)).isEmpty();
        assertThat(repository.findByOrgCodeAndEmail("", "")).isEmpty();
    }

    // =========================================================================
    // 6. Recupera estado empresarial, estado usuario, rol y auth_version
    // =========================================================================

    @Test
    @DisplayName("Recupera con precisión estado de servicio, estado de usuario, rol y auth_version")
    void recuperaEstadosRolYAuthVersion() {
        String orgId = insertarOrganizacion("ORG-SUSPENDIDA", "Org Bloqueada", "SUSPENDIDA", "Europe/Madrid");
        insertarUsuario(orgId, "inactivo@susp.com", "inactivo@susp.com",
                "Inactivo", "SUPERVISOR", "hash-inactivo", false, 99L);

        Optional<AuthenticationCandidate> opt = repository.findByOrgCodeAndEmail("ORG-SUSPENDIDA", "inactivo@susp.com");

        assertThat(opt).isPresent();
        AuthenticationCandidate c = opt.get();
        assertThat(c.organizationStatus()).isEqualTo("SUSPENDIDA");
        assertThat(c.organizationTimeZone()).isEqualTo("Europe/Madrid");
        assertThat(c.isActive()).isFalse();
        assertThat(c.roleCode()).isEqualTo("SUPERVISOR");
        assertThat(c.authVersion()).isEqualTo(99L);
        assertThat(c.passwordHash()).isEqualTo("hash-inactivo");
    }

    // =========================================================================
    // 7. SQL parametrizado protege contra inyecciones
    // =========================================================================

    @Test
    @DisplayName("SQL parametrizado: inyecciones en código o correo son tratadas como cadenas literales")
    void sqlParametrizadoProtegeContraInyeccion() {
        Optional<AuthenticationCandidate> opt = repository.findByOrgCodeAndEmail("' OR '1'='1", "' OR '1'='1");
        assertThat(opt).isEmpty();
    }

    // =========================================================================
    // 8. Error de acceso a datos se propaga como AuthenticationDataAccessException
    // =========================================================================

    @Test
    @DisplayName("Error real de BD: DataAccessException se propaga como AuthenticationDataAccessException, no como Optional.empty()")
    void errorRealDeBD_propagaComoAuthenticationDataAccessException() {
        // Construir un repositorio con un DataSource que siempre falla (URL inválida)
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setUrl("jdbc:sqlserver://localhost:1;connectRetryCount=0;loginTimeout=1");
        ds.setUsername("invalid");
        ds.setPassword("invalid");
        JdbcTemplate brokenTemplate = new JdbcTemplate(ds);
        JdbcAuthenticationCandidateRepository repoRoto = new JdbcAuthenticationCandidateRepository(brokenTemplate);

        // No debe retornar Optional.empty() — debe propagar AuthenticationDataAccessException
        assertThatThrownBy(() -> repoRoto.findByOrgCodeAndEmail("ORG-TEST", "user@test.com"))
                .isInstanceOf(AuthenticationDataAccessException.class)
                .hasMessageNotContaining("ORG-TEST")
                .hasMessageNotContaining("user@test.com")
                .hasMessageNotContaining("password")
                .hasCauseInstanceOf(CannotGetJdbcConnectionException.class);
    }
}
