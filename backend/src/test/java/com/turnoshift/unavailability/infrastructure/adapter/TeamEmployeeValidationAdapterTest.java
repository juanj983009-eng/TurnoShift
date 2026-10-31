package com.turnoshift.unavailability.infrastructure.adapter;

import com.turnoshift.team.domain.Employee;
import com.turnoshift.team.domain.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamEmployeeValidationAdapterTest {

    @Mock
    private EmployeeRepository employeeRepository;

    private TeamEmployeeValidationAdapter adapter;

    private final UUID orgId = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new TeamEmployeeValidationAdapter(employeeRepository);
    }

    @Test
    @DisplayName("Retorna true cuando el colaborador existe y está activo")
    void retornaTrueCuandoColaboradorActivo() {
        Employee activeEmployee = Employee.reconstruct(
                empId, orgId, "EMP-01", "Juan Perez",
                UUID.randomUUID(), UUID.randomUUID(), true, 0L, Instant.now(), Instant.now()
        );
        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(activeEmployee));

        assertThat(adapter.isEmployeeActive(orgId, empId)).isTrue();
    }

    @Test
    @DisplayName("Retorna false cuando el colaborador existe pero está inactivo")
    void retornaFalseCuandoColaboradorInactivo() {
        Employee inactiveEmployee = Employee.reconstruct(
                empId, orgId, "EMP-02", "Maria Gomez",
                UUID.randomUUID(), UUID.randomUUID(), false, 0L, Instant.now(), Instant.now()
        );
        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.of(inactiveEmployee));

        assertThat(adapter.isEmployeeActive(orgId, empId)).isFalse();
    }

    @Test
    @DisplayName("Retorna false cuando el colaborador no existe")
    void retornaFalseCuandoColaboradorNoExiste() {
        when(employeeRepository.findById(orgId, empId)).thenReturn(Optional.empty());

        assertThat(adapter.isEmployeeActive(orgId, empId)).isFalse();
    }

    @Test
    @DisplayName("Rechaza argumentos nulos")
    void rechazaArgumentosNulos() {
        assertThatThrownBy(() -> adapter.isEmployeeActive(null, empId))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> adapter.isEmployeeActive(orgId, null))
                .isInstanceOf(NullPointerException.class);
    }
}
