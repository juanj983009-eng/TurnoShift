package com.turnoshift.team.application;

import com.turnoshift.organization.domain.ActiveSiteView;
import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeCodeAlreadyExistsException;
import com.turnoshift.team.domain.EmployeeLicenseLimitExceededException;
import com.turnoshift.team.domain.EmployeeRepository;
import com.turnoshift.team.domain.NoActiveSiteException;
import com.turnoshift.team.domain.Position;
import com.turnoshift.team.domain.PositionInactiveException;
import com.turnoshift.team.domain.PositionNotFoundException;
import com.turnoshift.team.domain.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: CreateEmployeeUseCase")
class CreateEmployeeUseCaseTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private OrganizationConfigurationRepository organizationConfigurationRepository;

    private Clock clock;
    private CreateEmployeeUseCase useCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID posId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-16T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        useCase = new CreateEmployeeUseCase(
                employeeRepository,
                positionRepository,
                organizationConfigurationRepository,
                clock
        );
    }

    @Test
    @DisplayName("Falla si positionId no existe en la organización")
    void positionNoExiste() {
        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, "EMP-01", "Ana Gomez", posId))
                .isInstanceOf(PositionNotFoundException.class);

        verify(employeeRepository, never()).insert(any());
    }

    @Test
    @DisplayName("Falla si positionId no está activo")
    void positionInactivo() {
        Position inactivePos = new Position(posId, orgId, "POS-01", "Barista", false, 0L, now, now);
        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.of(inactivePos));

        assertThatThrownBy(() -> useCase.execute(orgId, "EMP-01", "Ana Gomez", posId))
                .isInstanceOf(PositionInactiveException.class);

        verify(employeeRepository, never()).insert(any());
    }

    @Test
    @DisplayName("Falla si la organización no tiene sede activa (NO_ACTIVE_SITE)")
    void sinSedeActiva() {
        Position activePos = new Position(posId, orgId, "POS-01", "Barista", true, 0L, now, now);
        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.of(activePos));
        when(organizationConfigurationRepository.findActiveSiteByOrganizationId(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, "EMP-01", "Ana Gomez", posId))
                .isInstanceOf(NoActiveSiteException.class);

        verify(employeeRepository, never()).insert(any());
    }

    @Test
    @DisplayName("Falla si el código ya existe en la organización")
    void codigoDuplicado() {
        Position activePos = new Position(posId, orgId, "POS-01", "Barista", true, 0L, now, now);
        ActiveSiteView activeSite = new ActiveSiteView(siteId, "HQ", "Sede Central", 0L);

        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.of(activePos));
        when(organizationConfigurationRepository.findActiveSiteByOrganizationId(orgId)).thenReturn(Optional.of(activeSite));
        when(employeeRepository.findByCode(orgId, "EMP-01")).thenReturn(Optional.of(
                Employee.create(orgId, "EMP-01", "Existente", posId, siteId, now)));

        assertThatThrownBy(() -> useCase.execute(orgId, "EMP-01", "Ana Gomez", posId))
                .isInstanceOf(EmployeeCodeAlreadyExistsException.class);

        verify(employeeRepository, never()).insert(any());
    }

    @Test
    @DisplayName("Falla si el límite de licencia de empleados activos ya fue alcanzado")
    void limiteLicenciaAlcanzado() {
        Position activePos = new Position(posId, orgId, "POS-01", "Barista", true, 0L, now, now);
        ActiveSiteView activeSite = new ActiveSiteView(siteId, "HQ", "Sede Central", 0L);

        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.of(activePos));
        when(organizationConfigurationRepository.findActiveSiteByOrganizationId(orgId)).thenReturn(Optional.of(activeSite));
        when(employeeRepository.findByCode(orgId, "EMP-01")).thenReturn(Optional.empty());
        when(employeeRepository.getMaxActiveEmployees(orgId)).thenReturn(10);
        when(employeeRepository.countActiveEmployees(orgId)).thenReturn(10L);

        assertThatThrownBy(() -> useCase.execute(orgId, "EMP-01", "Ana Gomez", posId))
                .isInstanceOf(EmployeeLicenseLimitExceededException.class);

        verify(employeeRepository).acquireOrganizationLicenseLock(orgId);
        verify(employeeRepository, never()).insert(any());
    }

    @Test
    @DisplayName("Creación exitosa: adquiere lock de licencia, inserta empleado activo y retorna")
    void creacionExitosa() {
        Position activePos = new Position(posId, orgId, "POS-01", "Barista", true, 0L, now, now);
        ActiveSiteView activeSite = new ActiveSiteView(siteId, "HQ", "Sede Central", 0L);

        when(positionRepository.findById(orgId, posId)).thenReturn(Optional.of(activePos));
        when(organizationConfigurationRepository.findActiveSiteByOrganizationId(orgId)).thenReturn(Optional.of(activeSite));
        when(employeeRepository.findByCode(orgId, "EMP-01")).thenReturn(Optional.empty());
        when(employeeRepository.getMaxActiveEmployees(orgId)).thenReturn(10);
        when(employeeRepository.countActiveEmployees(orgId)).thenReturn(9L);

        when(employeeRepository.insert(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee created = useCase.execute(orgId, "EMP-01", "Ana Gomez", posId);

        assertThat(created).isNotNull();
        assertThat(created.organizationId()).isEqualTo(orgId);
        assertThat(created.code()).isEqualTo("EMP-01");
        assertThat(created.displayName()).isEqualTo("Ana Gomez");
        assertThat(created.positionId()).isEqualTo(posId);
        assertThat(created.siteId()).isEqualTo(siteId);
        assertThat(created.isActive()).isTrue();

        verify(employeeRepository).acquireOrganizationLicenseLock(orgId);
        verify(employeeRepository).insert(any(Employee.class));
    }
}
