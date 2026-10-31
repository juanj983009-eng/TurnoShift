package com.turnoshift.team.application;

import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeNotFoundException;
import com.turnoshift.team.domain.EmployeeOptimisticLockException;
import com.turnoshift.team.domain.EmployeeRepository;
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
@DisplayName("Unit: UpdateEmployeeUseCase")
class UpdateEmployeeUseCaseTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PositionRepository positionRepository;

    private Clock clock;
    private UpdateEmployeeUseCase useCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();
    private final UUID posId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-16T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        useCase = new UpdateEmployeeUseCase(employeeRepository, positionRepository, clock);
    }

    @Test
    @DisplayName("Falla si el empleado no existe")
    void empleadoNoExiste() {
        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, empId, "Nuevo Nombre", posId, 0L))
                .isInstanceOf(EmployeeNotFoundException.class);

        verify(employeeRepository, never()).update(any(), eq(0L));
    }

    @Test
    @DisplayName("Falla si la versión esperada no coincide (optimistic lock)")
    void versionObsoleta() {
        Employee existing = new Employee(empId, orgId, "EMP-01", "Nombre", posId, siteId, true, 2L, now, now);
        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(orgId, empId, "Nuevo Nombre", posId, 1L))
                .isInstanceOf(EmployeeOptimisticLockException.class);

        verify(employeeRepository, never()).update(any(), eq(1L));
    }

    @Test
    @DisplayName("Falla si el nuevo puesto no existe en la organización")
    void nuevoPuestoNoExiste() {
        Employee existing = new Employee(empId, orgId, "EMP-01", "Nombre", posId, siteId, true, 0L, now, now);
        UUID nuevoPosId = UUID.randomUUID();
        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(existing));
        when(positionRepository.findById(orgId, nuevoPosId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, empId, "Nuevo Nombre", nuevoPosId, 0L))
                .isInstanceOf(PositionNotFoundException.class);

        verify(employeeRepository, never()).update(any(), eq(0L));
    }

    @Test
    @DisplayName("Falla si el nuevo puesto existe pero está inactivo")
    void nuevoPuestoInactivo() {
        Employee existing = new Employee(empId, orgId, "EMP-01", "Nombre", posId, siteId, true, 0L, now, now);
        UUID nuevoPosId = UUID.randomUUID();
        Position inactivePos = new Position(nuevoPosId, orgId, "POS-INAC", "Inactivo", false, 0L, now, now);

        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(existing));
        when(positionRepository.findById(orgId, nuevoPosId)).thenReturn(Optional.of(inactivePos));

        assertThatThrownBy(() -> useCase.execute(orgId, empId, "Nuevo Nombre", nuevoPosId, 0L))
                .isInstanceOf(PositionInactiveException.class);

        verify(employeeRepository, never()).update(any(), eq(0L));
    }

    @Test
    @DisplayName("Actualización exitosa de nombre y puesto")
    void actualizacionExitosa() {
        Employee existing = new Employee(empId, orgId, "EMP-01", "Nombre", posId, siteId, true, 0L, now, now);
        UUID nuevoPosId = UUID.randomUUID();
        Position activePos = new Position(nuevoPosId, orgId, "POS-NEW", "Nuevo Puesto", true, 0L, now, now);

        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(existing));
        when(positionRepository.findById(orgId, nuevoPosId)).thenReturn(Optional.of(activePos));

        Employee updatedCandidate = existing.update("Nombre Actualizado", nuevoPosId, now);
        when(employeeRepository.update(any(), eq(0L))).thenReturn(Optional.of(updatedCandidate));

        Employee result = useCase.execute(orgId, empId, "  Nombre Actualizado  ", nuevoPosId, 0L);

        assertThat(result.displayName()).isEqualTo("Nombre Actualizado");
        assertThat(result.positionId()).isEqualTo(nuevoPosId);
        verify(employeeRepository).update(any(), eq(0L));
    }

    @Test
    @DisplayName("Actualización exitosa solo de nombre manteniendo el puesto actual")
    void actualizacionSoloNombre() {
        Employee existing = new Employee(empId, orgId, "EMP-01", "Nombre", posId, siteId, true, 0L, now, now);
        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(existing));

        Employee updatedCandidate = existing.update("Nombre Actualizado", posId, now);
        when(employeeRepository.update(any(), eq(0L))).thenReturn(Optional.of(updatedCandidate));

        Employee result = useCase.execute(orgId, empId, "Nombre Actualizado", null, 0L);

        assertThat(result.displayName()).isEqualTo("Nombre Actualizado");
        assertThat(result.positionId()).isEqualTo(posId);
        verify(positionRepository, never()).findById(any(), any());
        verify(employeeRepository).update(any(), eq(0L));
    }

    @Test
    @DisplayName("Actualización exitosa solo de puesto manteniendo el nombre actual")
    void actualizacionSoloPuesto() {
        Employee existing = new Employee(empId, orgId, "EMP-01", "Nombre", posId, siteId, true, 0L, now, now);
        UUID nuevoPosId = UUID.randomUUID();
        Position activePos = new Position(nuevoPosId, orgId, "POS-NEW", "Nuevo Puesto", true, 0L, now, now);

        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(existing));
        when(positionRepository.findById(orgId, nuevoPosId)).thenReturn(Optional.of(activePos));

        Employee updatedCandidate = existing.update("Nombre", nuevoPosId, now);
        when(employeeRepository.update(any(), eq(0L))).thenReturn(Optional.of(updatedCandidate));

        Employee result = useCase.execute(orgId, empId, null, nuevoPosId, 0L);

        assertThat(result.displayName()).isEqualTo("Nombre");
        assertThat(result.positionId()).isEqualTo(nuevoPosId);
        verify(employeeRepository).update(any(), eq(0L));
    }
}
