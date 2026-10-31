package com.turnoshift.team.infrastructure;

import com.turnoshift.team.domain.Position;
import com.turnoshift.team.domain.PositionCodeAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Testcontainers
@DisplayName("IT: Persistencia JDBC de Puestos con SQL Server real y Testcontainers")
class JdbcPositionRepositoryIT {

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
    private JdbcPositionRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID orgA;
    private UUID orgB;
    private final Instant now = Instant.parse("2026-09-16T14:30:00Z");

    @BeforeEach
    void setUpData() {
        orgA = UUID.randomUUID();
        orgB = UUID.randomUUID();

        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);

        String orgACode = "ORG-POS-A-" + suffix;
        String orgBCode = "ORG-POS-B-" + suffix;

        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Empresa Pos A', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgA, orgACode);

        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, 'Empresa Pos B', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgB, orgBCode);
    }

    @Test
    @DisplayName("1. Inserción y consulta por ID y Código: persiste y recupera todos los campos correctamente")
    void insercionYConsulta_persisteYRecuperaCampos() {
        Position position = Position.create(orgA, "MED-01", "Médico General", now);
        repository.insert(position);

        Optional<Position> byId = repository.findById(orgA, position.id());
        assertThat(byId).isPresent();
        Position found = byId.get();

        assertThat(found.id()).isEqualTo(position.id());
        assertThat(found.organizationId()).isEqualTo(orgA);
        assertThat(found.code()).isEqualTo("MED-01");
        assertThat(found.name()).isEqualTo("Médico General");
        assertThat(found.isActive()).isTrue();
        assertThat(found.lockVersion()).isEqualTo(0L);
        assertThat(found.createdAt()).isCloseTo(now, within(2, ChronoUnit.SECONDS));
        assertThat(found.updatedAt()).isCloseTo(now, within(2, ChronoUnit.SECONDS));

        Optional<Position> byCode = repository.findByCode(orgA, "MED-01");
        assertThat(byCode).isPresent();
        assertThat(byCode.get().id()).isEqualTo(position.id());
    }

    @Test
    @DisplayName("2. Multi-tenancy: permite el mismo código de puesto en organizaciones distintas")
    void mismoCodigoEnOrganizacionesDistintas_permiteInsercionSinConflicto() {
        Position posA = Position.create(orgA, "MED-01", "Médico General A", now);
        Position posB = Position.create(orgB, "MED-01", "Médico General B", now);

        repository.insert(posA);
        repository.insert(posB);

        Optional<Position> foundA = repository.findByCode(orgA, "MED-01");
        Optional<Position> foundB = repository.findByCode(orgB, "MED-01");

        assertThat(foundA).isPresent();
        assertThat(foundB).isPresent();
        assertThat(foundA.get().id()).isEqualTo(posA.id());
        assertThat(foundB.get().id()).isEqualTo(posB.id());
        assertThat(foundA.get().organizationId()).isEqualTo(orgA);
        assertThat(foundB.get().organizationId()).isEqualTo(orgB);
    }

    @Test
    @DisplayName("3. Unicidad por tenant: rechaza código duplicado con PositionCodeAlreadyExistsException")
    void codigoDuplicadoEnMismaOrganizacion_fallaConExcepcionCodigoExistente() {
        Position pos1 = Position.create(orgA, "MED-01", "Médico General", now);
        repository.insert(pos1);

        Position pos2 = Position.create(orgA, "MED-01", "Otro Médico", now);
        assertThatThrownBy(() -> repository.insert(pos2))
                .isInstanceOf(PositionCodeAlreadyExistsException.class)
                .hasMessageContaining("MED-01");
    }

    @Test
    @DisplayName("4. Aislamiento estricto: findById y findByCode de otra organización retornan vacío")
    void aislamientoTenant_retornaVacioParaOtraOrganizacion() {
        Position posA = Position.create(orgA, "ENF-01", "Enfermero Jefe", now);
        repository.insert(posA);

        assertThat(repository.findById(orgB, posA.id())).isEmpty();
        assertThat(repository.findByCode(orgB, "ENF-01")).isEmpty();
    }

    @Test
    @DisplayName("5. Listado findAll: filtra por tenant, ordena por code ASC y soporta filtro activo")
    void findAll_filtraPorTenantYOrdenaPorCodigo() {
        Position p3 = Position.create(orgA, "TER-01", "Terapeuta", now);
        Position p1 = Position.create(orgA, "ADM-01", "Administrador", now);
        Position p2 = Position.create(orgA, "ENF-01", "Enfermero", now);
        Position pB = Position.create(orgB, "CIR-01", "Cirujano", now);

        repository.insert(p3);
        repository.insert(p1);
        repository.insert(p2);
        repository.insert(pB);

        // Desactivar TER-01
        Position inactiveP3 = p3.changeStatus(false, now.plusSeconds(10));
        Optional<Position> updated = repository.update(inactiveP3, 0L);
        assertThat(updated).isPresent();

        // Consulta sin filtro de activo
        List<Position> allOrgA = repository.findAll(orgA, null);
        assertThat(allOrgA).extracting(Position::code)
                .containsExactly("ADM-01", "ENF-01", "TER-01");

        // Consulta solo activos
        List<Position> activeOnly = repository.findAll(orgA, true);
        assertThat(activeOnly).extracting(Position::code)
                .containsExactly("ADM-01", "ENF-01");

        // Consulta solo inactivos
        List<Position> inactiveOnly = repository.findAll(orgA, false);
        assertThat(inactiveOnly).extracting(Position::code)
                .containsExactly("TER-01");

        // Org B solo ve sus propios puestos
        List<Position> allOrgB = repository.findAll(orgB, null);
        assertThat(allOrgB).extracting(Position::code)
                .containsExactly("CIR-01");
    }

    @Test
    @DisplayName("6. Concurrencia optimista: actualización exitosa retorna la fila persistida con timestamp autoritativo")
    void concurrenciaOptimista_actualizacionExitosa() {
        Position pos = Position.create(orgA, "MED-01", "Médico General", now);
        repository.insert(pos);

        Instant beforeUpdate = Instant.now().minus(100, ChronoUnit.MILLIS);
        Position candidate = pos.updateName("Médico Especialista", now.plusSeconds(60));

        Optional<Position> result = repository.update(candidate, 0L);
        assertThat(result).isPresent();

        Position persisted = result.get();
        assertThat(persisted.id()).isEqualTo(pos.id());
        assertThat(persisted.organizationId()).isEqualTo(orgA);
        assertThat(persisted.code()).isEqualTo("MED-01");
        assertThat(persisted.name()).isEqualTo("Médico Especialista");
        assertThat(persisted.isActive()).isTrue();
        assertThat(persisted.lockVersion()).isEqualTo(1L);

        // updated_at generado por SQL Server y no anterior al instante previo
        assertThat(persisted.updatedAt()).isNotNull();
        assertThat(persisted.updatedAt()).isAfterOrEqualTo(beforeUpdate);

        // Confirmar en base de datos que coincide exactamente con lo devuelto por OUTPUT
        Position fetched = repository.findById(orgA, pos.id()).orElseThrow();
        assertThat(fetched.name()).isEqualTo("Médico Especialista");
        assertThat(fetched.lockVersion()).isEqualTo(1L);
        assertThat(fetched.updatedAt()).isEqualTo(persisted.updatedAt());
    }

    @Test
    @DisplayName("7. Concurrencia optimista: actualización con versión obsoleta retorna Optional.empty() y no muta datos")
    void concurrenciaOptimista_versionObsoleta_retornaVacioYNoModifica() {
        Position pos = Position.create(orgA, "MED-01", "Médico General", now);
        repository.insert(pos);

        // Primer update exitoso (0 -> 1)
        Position v1Candidate = pos.updateName("Médico Especialista", now.plusSeconds(30));
        Optional<Position> v1 = repository.update(v1Candidate, 0L);
        assertThat(v1).isPresent();

        // Intento de update concurrente con versión obsoleta (0L en vez de 1L)
        Position stale = pos.updateName("Médico Auditor", now.plusSeconds(60));
        Optional<Position> staleResult = repository.update(stale, 0L);

        assertThat(staleResult).isEmpty();

        // Los datos siguen siendo los de v1
        Position current = repository.findById(orgA, pos.id()).orElseThrow();
        assertThat(current.name()).isEqualTo("Médico Especialista");
        assertThat(current.lockVersion()).isEqualTo(1L);
        assertThat(current.updatedAt()).isEqualTo(v1.get().updatedAt());
    }

    @Test
    @DisplayName("8. Aislamiento en update: actualización bajo otra organización retorna Optional.empty() y no muta datos")
    void update_conOtraOrganizacion_retornaVacioYNoModifica() {
        Position pos = Position.create(orgA, "MED-01", "Médico", now);
        repository.insert(pos);

        // Intento de modificar bajo orgB
        Position manipulated = new Position(
                pos.id(),
                orgB,
                pos.code(),
                "Médico Hackeado",
                pos.isActive(),
                pos.lockVersion() + 1,
                pos.createdAt(),
                now.plusSeconds(60)
        );

        Optional<Position> result = repository.update(manipulated, 0L);
        assertThat(result).isEmpty();

        // Posición en Org A permanece intacta
        Position current = repository.findById(orgA, pos.id()).orElseThrow();
        assertThat(current.name()).isEqualTo("Médico");
        assertThat(current.lockVersion()).isEqualTo(0L);
    }
}
