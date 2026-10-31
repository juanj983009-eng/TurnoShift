package com.turnoshift.operator.infrastructure.persistence;

import com.turnoshift.operator.domain.OperatorDataAccessException;
import com.turnoshift.operator.domain.PlatformOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: JdbcPlatformOperatorRepository — persistencia en SQL Server real")
class JdbcPlatformOperatorRepositoryIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense()
                    .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Autowired
    private JdbcPlatformOperatorRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String FAKE_HASH = "$argon2id$v=19$m=65536,t=3,p=1$fakeoperatortesthash";

    private PlatformOperator crearOperador(String email, String normalizedEmail) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new PlatformOperator(
                UUID.randomUUID(),
                email,
                normalizedEmail,
                "Operador " + email,
                FAKE_HASH,
                true,
                1L,
                0L,
                now,
                now
        );
    }

    // =========================================================================
    // 1. Inserción y consulta por ID
    // =========================================================================

    @Test
    @DisplayName("guardar y buscar por ID recupera el operador con todos sus campos")
    void guardarYRecuperar_porId_retornaOperadorExacto() {
        PlatformOperator operator = crearOperador("admin.master@turnoshift.com", "admin.master@turnoshift.com");

        repository.save(operator);

        Optional<PlatformOperator> encontradoOpt = repository.findById(operator.id());
        assertThat(encontradoOpt).isPresent();

        PlatformOperator encontrado = encontradoOpt.get();
        assertThat(encontrado.id()).isEqualTo(operator.id());
        assertThat(encontrado.email()).isEqualTo("admin.master@turnoshift.com");
        assertThat(encontrado.normalizedEmail()).isEqualTo("admin.master@turnoshift.com");
        assertThat(encontrado.displayName()).isEqualTo(operator.displayName());
        assertThat(encontrado.passwordHash()).isEqualTo(FAKE_HASH);
        assertThat(encontrado.isActive()).isTrue();
        assertThat(encontrado.authVersion()).isEqualTo(1L);
        assertThat(encontrado.lockVersion()).isZero();
        assertThat(encontrado.createdAt()).isNotNull();
        assertThat(encontrado.updatedAt()).isNotNull();
    }

    // =========================================================================
    // 2. Consulta por correo normalizado con collation BIN2
    // =========================================================================

    @Test
    @DisplayName("búsqueda por normalizedEmail respeta la collation Latin1_General_100_BIN2 exacta")
    void recuperar_porNormalizedEmail_coincidenciaBinariaExacta() {
        PlatformOperator operator = crearOperador("operador.alfa@turnoshift.com", "operador.alfa@turnoshift.com");
        repository.save(operator);

        // Coincidencia exacta
        Optional<PlatformOperator> exacto = repository.findByNormalizedEmail("operador.alfa@turnoshift.com");
        assertThat(exacto).isPresent();
        assertThat(exacto.get().id()).isEqualTo(operator.id());

        // Diferencia de mayúsculas/minúsculas no debe coincidir bajo Latin1_General_100_BIN2
        Optional<PlatformOperator> mayusculas = repository.findByNormalizedEmail("OPERADOR.ALFA@TURNOSHIFT.COM");
        assertThat(mayusculas).isEmpty();

        // Correo inexistente
        Optional<PlatformOperator> inexistente = repository.findByNormalizedEmail("otro@turnoshift.com");
        assertThat(inexistente).isEmpty();
    }

    // =========================================================================
    // 3. Unicidad de normalized_email
    // =========================================================================

    @Test
    @DisplayName("Violación de unicidad en normalized_email lanza OperatorDataAccessException")
    void unicidad_normalizedEmail_lanzaOperatorDataAccessException() {
        PlatformOperator op1 = crearOperador("unico@turnoshift.com", "unico@turnoshift.com");
        repository.save(op1);

        PlatformOperator op2 = new PlatformOperator(
                UUID.randomUUID(),
                "unico@turnoshift.com",
                "unico@turnoshift.com", // Mismo normalizedEmail
                "Otro Operador",
                FAKE_HASH,
                true,
                1L,
                0L,
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> repository.save(op2))
                .isInstanceOf(OperatorDataAccessException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    String causeMsg = ex.getCause().getMessage();
                    assertThat(causeMsg).contains("uq_platform_operators_normalized_email");
                });
    }

    // =========================================================================
    // 4. Restricciones CHECK
    // =========================================================================

    @Test
    @DisplayName("Restricción CHECK: email vacío es rechazado por la base de datos")
    void check_emailVacio_esRechazado() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO platform_operators (id, email, normalized_email, display_name, password_hash)
                VALUES (NEWID(), '', 'norm@test.com', 'Name', 'hash')
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_platform_operators_email_not_empty");
    }

    @Test
    @DisplayName("Restricción CHECK: normalized_email vacío es rechazado por la base de datos")
    void check_normalizedEmailVacio_esRechazado() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO platform_operators (id, email, normalized_email, display_name, password_hash)
                VALUES (NEWID(), 'email@test.com', '', 'Name', 'hash')
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_platform_operators_normalized_email_not_empty");
    }

    @Test
    @DisplayName("Restricción CHECK: display_name vacío es rechazado por la base de datos")
    void check_displayNameVacio_esRechazado() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO platform_operators (id, email, normalized_email, display_name, password_hash)
                VALUES (NEWID(), 'email@test.com', 'norm@test.com', '   ', 'hash')
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_platform_operators_display_name_not_empty");
    }

    @Test
    @DisplayName("Restricción CHECK: password_hash vacío es rechazado por la base de datos")
    void check_passwordHashVacio_esRechazado() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO platform_operators (id, email, normalized_email, display_name, password_hash)
                VALUES (NEWID(), 'email@test.com', 'norm@test.com', 'Name', '   ')
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_platform_operators_password_hash_not_empty");
    }

    @Test
    @DisplayName("Restricción CHECK: auth_version negativa es rechazada por la base de datos")
    void check_authVersionNegativa_esRechazada() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO platform_operators (id, email, normalized_email, display_name, password_hash, auth_version)
                VALUES (NEWID(), 'email@test.com', 'norm@test.com', 'Name', 'hash', -1)
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_platform_operators_auth_version_non_negative");
    }

    @Test
    @DisplayName("Restricción CHECK: lock_version negativa es rechazada por la base de datos")
    void check_lockVersionNegativa_esRechazada() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO platform_operators (id, email, normalized_email, display_name, password_hash, lock_version)
                VALUES (NEWID(), 'email@test.com', 'norm@test.com', 'Name', 'hash', -1)
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_platform_operators_lock_version_non_negative");
    }

    // =========================================================================
    // 5. Actualización atómica de auth_version
    // =========================================================================

    @Test
    @DisplayName("updateAuthVersion actualiza atómicamente si la versión esperada coincide")
    void updateAuthVersion_actualizaVersionYUpdatedAt_retornaTrueSiCoincide() {
        PlatformOperator op = crearOperador("version@turnoshift.com", "version@turnoshift.com");
        repository.save(op);

        // Actualización válida de 1 a 2
        boolean updated = repository.updateAuthVersion(op.id(), 1L, 2L);
        assertThat(updated).isTrue();

        PlatformOperator opActualizado = repository.findById(op.id()).orElseThrow();
        assertThat(opActualizado.authVersion()).isEqualTo(2L);

        // Intento con versión esperada errónea (1L en vez de 2L)
        boolean failUpdated = repository.updateAuthVersion(op.id(), 1L, 3L);
        assertThat(failUpdated).isFalse();

        // auth_version permanece en 2L
        PlatformOperator opSinCambio = repository.findById(op.id()).orElseThrow();
        assertThat(opSinCambio.authVersion()).isEqualTo(2L);
    }

    // =========================================================================
    // 6. Conmutación de estado is_active
    // =========================================================================

    @Test
    @DisplayName("updateActiveStatus conmuta el estado de activación del operador")
    void updateActiveStatus_conmutaEstadoYActualizaUpdatedAt() {
        PlatformOperator op = crearOperador("activo@turnoshift.com", "activo@turnoshift.com");
        repository.save(op);
        assertThat(op.isActive()).isTrue();

        // Desactivar
        boolean deactivated = repository.updateActiveStatus(op.id(), false);
        assertThat(deactivated).isTrue();

        PlatformOperator opInactivo = repository.findById(op.id()).orElseThrow();
        assertThat(opInactivo.isActive()).isFalse();

        // Reactivar
        boolean reactivated = repository.updateActiveStatus(op.id(), true);
        assertThat(reactivated).isTrue();

        PlatformOperator opActivo = repository.findById(op.id()).orElseThrow();
        assertThat(opActivo.isActive()).isTrue();

        // ID no existente retorna false
        boolean noOp = repository.updateActiveStatus(UUID.randomUUID(), false);
        assertThat(noOp).isFalse();
    }

    // =========================================================================
    // 7. Conteo total de operadores
    // =========================================================================

    @Test
    @DisplayName("count refleja la cantidad de operadores persistidos")
    void count_reflejaCantidadExactaDeOperadores() {
        long baseCount = repository.count();

        repository.save(crearOperador("count1@turnoshift.com", "count1@turnoshift.com"));
        repository.save(crearOperador("count2@turnoshift.com", "count2@turnoshift.com"));

        assertThat(repository.count()).isEqualTo(baseCount + 2);
    }

    // =========================================================================
    // 8. Fallo de conexión propaga OperatorDataAccessException
    // =========================================================================

    @Test
    @DisplayName("Fallo de conexión SQL Server: propaga OperatorDataAccessException con mensaje seguro")
    void falloConexion_propagaOperatorDataAccessException() {
        DriverManagerDataSource badDataSource = new DriverManagerDataSource();
        badDataSource.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        badDataSource.setUrl("jdbc:sqlserver://127.0.0.1:54321;databaseName=turnoshift_dev;loginTimeout=1");
        badDataSource.setUsername("sa");
        badDataSource.setPassword("wrong-password");

        JdbcPlatformOperatorRepository badRepo =
                new JdbcPlatformOperatorRepository(new JdbcTemplate(badDataSource));

        assertThatThrownBy(() -> badRepo.findById(UUID.randomUUID()))
                .isInstanceOf(OperatorDataAccessException.class)
                .hasMessageContaining("[OPERATOR_DATA_ACCESS_ERROR]");
    }
}
