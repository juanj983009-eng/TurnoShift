package com.turnoshift.team.application;

import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeLicenseLimitExceededException;
import com.turnoshift.team.domain.EmployeeNotFoundException;
import com.turnoshift.team.domain.EmployeeOptimisticLockException;
import com.turnoshift.team.domain.EmployeeRepository;
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
@DisplayName("Unit: ActivateEmployeeUseCase")
class ActivateEmployeeUseCaseTest {

    @Mock
    private EmployeeRepository employeeRepository;

    private Clock clock;
    private ActivateEmployeeUseCase useCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();
    private final UUID posId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-16T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        useCase = new ActivateEmployeeUseCase(employeeRepository, clock);
    }

    @Test
    @DisplayName("Falla si el empleado no existe")
    void empleadoNoExiste() {
        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, empId, 0L))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    @DisplayName("Falla si la versión esperada no coincide")
    void versionObsoleta() {
        Employee existing = new Employee(empId, orgId, "EMP-01", "Nombre", posId, siteId, false, 2L, now, now);
        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(orgId, empId, 1L))
                .isInstanceOf(EmployeeOptimisticLockException.class);
    }

    @Test
    @DisplayName("Si ya está activo y la versión esperada es correcta, retorna no-op sin re-activar ni contar licencias")
    void noOpValido() {
        Employee existing = new Employee(empId, orgId, "EMP-01", "Nombre", posId, siteId, true, 2L, now, now);
        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(existing));

        Employee result = useCase.execute(orgId, empId, 2L);

        assertThat(result).isEqualTo(existing);
        verify(employeeRepository, never()).acquireOrganizationLicenseLock(any());
        verify(employeeRepository, never()).update(any(), any(Long.class));
    }

    @Test
    @DisplayName("Falla si está inactivo pero se alcanza el límite de licencias")
    void limiteLicenciaAlcanzado() {
        Employee existing = new Employee(empId, orgId, "EMP-01", "Nombre", posId, siteId, false, 0L, now, now);
        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(existing));
        when(employeeRepository.getMaxActiveEmployees(orgId)).thenReturn(5);
        when(employeeRepository.countActiveEmployees(orgId)).thenReturn(5L);

        assertThatThrownBy(() -> useCase.execute(orgId, empId, 0L))
                .isInstanceOf(EmployeeLicenseLimitExceededException.class);

        verify(employeeRepository).acquireOrganizationLicenseLock(orgId);
        verify(employeeRepository, never()).update(any(), any(Long.class));
    }

    @Test
    @DisplayName("Activación exitosa: adquiere lock, valida límite y actualiza")
    void activacionExitosa() {
        Employee existing = new Employee(empId, orgId, "EMP-01", "Nombre", posId, siteId, false, 0L, now, now);
        Employee activated = existing.changeStatus(true, now);

        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(existing));
        when(employeeRepository.getMaxActiveEmployees(orgId)).thenReturn(5);
        when(employeeRepository.countActiveEmployees(orgId)).thenReturn(4L);
        when(employeeRepository.update(any(), eq(0L))).thenReturn(Optional.of(activated));

        Employee result = useCase.execute(orgId, empId, 0L);

        assertThat(result.isActive()).isTrue();
        verify(employeeRepository).acquireOrganizationLicenseLock(orgId);
        verify(employeeRepository).update(any(), eq(0L));
    }
}
