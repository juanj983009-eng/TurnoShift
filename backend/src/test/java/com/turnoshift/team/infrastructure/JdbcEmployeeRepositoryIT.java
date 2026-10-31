package com.turnoshift.team.infrastructure;

import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.team.application.ActivateEmployeeUseCase;
import com.turnoshift.team.application.CreateEmployeeUseCase;
import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeCodeAlreadyExistsException;
import com.turnoshift.team.domain.EmployeeLicenseLimitExceededException;
import com.turnoshift.team.domain.NoActiveSiteException;
import com.turnoshift.team.domain.Position;
import com.turnoshift.team.domain.PositionInactiveException;
import com.turnoshift.team.domain.PositionNotFoundException;
import com.turnoshift.team.domain.PositionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@DisplayName("IT: JdbcEmployeeRepository y Casos de Uso")
class JdbcEmployeeRepositoryIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JdbcEmployeeRepository employeeRepository;

    @Autowired
    PositionRepository positionRepository;

    @Autowired
    OrganizationConfigurationRepository organizationConfigurationRepository;

    @Autowired
    CreateEmployeeUseCase createEmployeeUseCase;

    @Autowired
    ActivateEmployeeUseCase activateEmployeeUseCase;

    @Autowired
    TransactionTemplate transactionTemplate;

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID insertarOrganizacion(String code, int maxEmployees) {
        UUID orgId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Org Test', 'ACTIVA', 'America/Bogota', ?, 1)
                """, orgId, code, maxEmployees);
        return orgId;
    }

    private UUID insertarSede(UUID orgId, String code, boolean isActive) {
        UUID siteId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sites (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, 'Sede Test', ?)
                """, siteId, orgId, code, isActive ? 1 : 0);
        return siteId;
    }

    private UUID insertarPuesto(UUID orgId, String code, boolean isActive) {
        UUID posId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO positions (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, 'Puesto Test', ?)
                """, posId, orgId, code, isActive ? 1 : 0);
        return posId;
    }

    // =========================================================================
    // Pruebas
    // =========================================================================

    @Test
    @DisplayName("Crea y recupera empleado en la misma organización")
    void crearYRecuperarEmpleado() {
        UUID orgId = insertarOrganizacion("ORG-REPO-01", 10);
        UUID siteId = insertarSede(orgId, "SITE-01", true);
        UUID posId = insertarPuesto(orgId, "POS-01", true);

        Employee emp = Employee.create(orgId, "EMP-001", "Ana Gomez", posId, siteId, Instant.now());
        employeeRepository.insert(emp);

        Optional<Employee> found = employeeRepository.findById(orgId, emp.id());
        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("EMP-001");
        assertThat(found.get().displayName()).isEqualTo("Ana Gomez");
        assertThat(found.get().organizationId()).isEqualTo(orgId);
        assertThat(found.get().isActive()).isTrue();
        assertThat(found.get().lockVersion()).isEqualTo(0L);

        Optional<Employee> foundByCode = employeeRepository.findByCode(orgId, "EMP-001");
        assertThat(foundByCode).isPresent();
        assertThat(foundByCode.get().id()).isEqualTo(emp.id());
    }

    @Test
    @DisplayName("Listado de empleados aislado por tenant con filtro active")
    void listadoAisladoPorTenant() {
        UUID org1 = insertarOrganizacion("ORG-REPO-02A", 10);
        UUID site1 = insertarSede(org1, "SITE-02A", true);
        UUID pos1 = insertarPuesto(org1, "POS-02A", true);

        UUID org2 = insertarOrganizacion("ORG-REPO-02B", 10);
        UUID site2 = insertarSede(org2, "SITE-02B", true);
        UUID pos2 = insertarPuesto(org2, "POS-02B", true);

        Employee e1 = Employee.create(org1, "EMP-A1", "Activo Org 1", pos1, site1, Instant.now());
        Employee e2 = Employee.create(org1, "EMP-A2", "Inactivo Org 1", pos1, site1, Instant.now());
        employeeRepository.insert(e1);
        employeeRepository.insert(e2);
        employeeRepository.update(e2.changeStatus(false, Instant.now()), 0L);

        Employee e3 = Employee.create(org2, "EMP-B1", "Activo Org 2", pos2, site2, Instant.now());
        employeeRepository.insert(e3);

        List<Employee> org1All = employeeRepository.findAll(org1, null);
        assertThat(org1All).hasSize(2);

        List<Employee> org1Activos = employeeRepository.findAll(org1, true);
        assertThat(org1Activos).hasSize(1);
        assertThat(org1Activos.get(0).code()).isEqualTo("EMP-A1");

        List<Employee> org1Inactivos = employeeRepository.findAll(org1, false);
        assertThat(org1Inactivos).hasSize(1);
        assertThat(org1Inactivos.get(0).code()).isEqualTo("EMP-A2");

        List<Employee> org2All = employeeRepository.findAll(org2, null);
        assertThat(org2All).hasSize(1);
        assertThat(org2All.get(0).code()).isEqualTo("EMP-B1");
    }

    @Test
    @DisplayName("Actualización optimista y concurrencia")
    void actualizacionOptimista() {
        UUID orgId = insertarOrganizacion("ORG-REPO-03", 10);
        UUID siteId = insertarSede(orgId, "SITE-03", true);
        UUID pos1 = insertarPuesto(orgId, "POS-03A", true);
        UUID pos2 = insertarPuesto(orgId, "POS-03B", true);

        Employee emp = Employee.create(orgId, "EMP-03", "Nombre Original", pos1, siteId, Instant.now());
        employeeRepository.insert(emp);

        // Actualización con versión correcta (0)
        Employee updated = emp.update("Nombre Modificado", pos2, Instant.now());
        Optional<Employee> res = employeeRepository.update(updated, 0L);
        assertThat(res).isPresent();
        assertThat(res.get().lockVersion()).isEqualTo(1L);
        assertThat(res.get().displayName()).isEqualTo("Nombre Modificado");
        assertThat(res.get().positionId()).isEqualTo(pos2);

        // Intento con versión obsoleta (0 en lugar de 1)
        Optional<Employee> stale = employeeRepository.update(updated.update("Otro", pos1, Instant.now()), 0L);
        assertThat(stale).isEmpty();
    }

    @Test
    @DisplayName("Reconoce la constraint exacta de código duplicado")
    void constraintCodigoDuplicado() {
        UUID orgId = insertarOrganizacion("ORG-REPO-04", 10);
        UUID siteId = insertarSede(orgId, "SITE-04", true);
        UUID posId = insertarPuesto(orgId, "POS-04", true);

        Employee emp1 = Employee.create(orgId, "EMP-DUP", "Empleado 1", posId, siteId, Instant.now());
        employeeRepository.insert(emp1);

        Employee emp2 = Employee.create(orgId, "EMP-DUP", "Empleado 2", posId, siteId, Instant.now());
        assertThatThrownBy(() -> employeeRepository.insert(emp2))
                .isInstanceOf(EmployeeCodeAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Puesto inactivo y falta de sede activa se distinguen correctamente en CreateEmployeeUseCase")
    void distincionPuestoInactivoYSedeInactiva() {
        UUID orgId = insertarOrganizacion("ORG-REPO-05", 10);
        UUID posInactivo = insertarPuesto(orgId, "POS-INAC", false);
        UUID posActivo = insertarPuesto(orgId, "POS-ACT", true);

        // Caso 1: Puesto inactivo
        assertThatThrownBy(() -> createEmployeeUseCase.execute(orgId, "EMP-X1", "Ana", posInactivo))
                .isInstanceOf(PositionInactiveException.class);

        // Caso 2: Puesto activo pero no hay sede activa
        assertThatThrownBy(() -> createEmployeeUseCase.execute(orgId, "EMP-X2", "Ana", posActivo))
                .isInstanceOf(NoActiveSiteException.class);

        // Caso 3: Puesto inexistente
        UUID posFantasma = UUID.randomUUID();
        assertThatThrownBy(() -> createEmployeeUseCase.execute(orgId, "EMP-X3", "Ana", posFantasma))
                .isInstanceOf(PositionNotFoundException.class);
    }

    @Test
    @DisplayName("No aceptar referencias cross-tenant: puesto de otra organización rechazado por FK")
    void noAceptarReferenciasCrossTenant() {
        UUID org1 = insertarOrganizacion("ORG-REPO-06A", 10);
        UUID site1 = insertarSede(org1, "SITE-06A", true);

        UUID org2 = insertarOrganizacion("ORG-REPO-06B", 10);
        UUID posOrg2 = insertarPuesto(org2, "POS-06B", true);

        Employee cross = Employee.create(org1, "EMP-CROSS", "Cross", posOrg2, site1, Instant.now());
        assertThatThrownBy(() -> employeeRepository.insert(cross))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Solicitar lock de licencia sin transacción Spring activa falla de forma controlada sin abrir transacción manual")
    void lockSinTransaccionActivaFalla() {
        UUID orgId = insertarOrganizacion("ORG-REPO-LOCK-FAIL", 10);

        // Sin transactionTemplate ni @Transactional activo en el hilo actual
        assertThatThrownBy(() -> employeeRepository.acquireOrganizationLicenseLock(orgId))
                .isInstanceOf(EmployeeDataAccessException.class)
                .hasMessageContaining("transacción activa");
    }

    @Test
    @DisplayName("Inserción retorna Employee con timestamps autoritativos generados por SQL Server (SYSUTCDATETIME)")
    void insercionRetornaTimestampsAutoritativosDeSqlServer() {
        UUID orgId = insertarOrganizacion("ORG-REPO-TIMESTAMP", 10);
        UUID siteId = insertarSede(orgId, "SITE-TIME", true);
        UUID posId = insertarPuesto(orgId, "POS-TIME", true);

        // Instant deliberadamente en el pasado arbitrario (hace 10 días)
        Instant arbitraryPast = Instant.parse("2020-01-01T00:00:00Z");
        Employee candidate = Employee.create(orgId, "EMP-TIME-01", "Nombre Tiempo", posId, siteId, arbitraryPast);

        Instant beforeInsert = Instant.now().minusSeconds(5);
        Employee inserted = transactionTemplate.execute(status -> employeeRepository.insert(candidate));
        Instant afterInsert = Instant.now().plusSeconds(5);

        assertThat(inserted).isNotNull();
        assertThat(inserted.id()).isEqualTo(candidate.id());
        assertThat(inserted.code()).isEqualTo("EMP-TIME-01");
        assertThat(inserted.lockVersion()).isEqualTo(0L);

        // El timestamp retornado NO debe ser el arbitraryPast enviado a Java
        assertThat(inserted.createdAt()).isNotEqualTo(arbitraryPast);
        assertThat(inserted.updatedAt()).isNotEqualTo(arbitraryPast);

        // El timestamp retornado debe corresponder al tiempo actual del servidor de base de datos
        assertThat(inserted.createdAt()).isBetween(beforeInsert, afterInsert);
        assertThat(inserted.updatedAt()).isBetween(beforeInsert, afterInsert);
    }

    @Test
    @DisplayName("Concurrencia de licencia: dos operaciones concurrentes al límite, exactamente una triunfa")
    void concurrenciaLicenciaAlLimite() throws Exception {
        // Organización con límite estricto de 1 empleado activo
        UUID orgId = insertarOrganizacion("ORG-REPO-CONC", 1);
        insertarSede(orgId, "SITE-CONC", true);
        UUID posId = insertarPuesto(orgId, "POS-CONC", true);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger exitos = new AtomicInteger(0);
        AtomicInteger rechazosLicencia = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final String code = "EMP-CONC-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    createEmployeeUseCase.execute(orgId, code, "Empleado Concurrente", posId);
                    exitos.incrementAndGet();
                } catch (EmployeeLicenseLimitExceededException e) {
                    rechazosLicencia.incrementAndGet();
                } catch (Exception e) {
                    // Otros fallos inesperados
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(exitos.get()).as("Exactamente una de las dos creaciones concurrentes debe tener éxito").isEqualTo(1);
        assertThat(rechazosLicencia.get()).as("La otra creación debe fallar por límite de licencia").isEqualTo(1);
        assertThat(employeeRepository.countActiveEmployees(orgId)).isEqualTo(1L);
    }
}
