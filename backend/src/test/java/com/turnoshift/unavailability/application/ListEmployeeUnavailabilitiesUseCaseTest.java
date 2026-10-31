package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.EmployeeValidationPort;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import com.turnoshift.unavailability.domain.exceptions.EmployeeNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListEmployeeUnavailabilitiesUseCaseTest {

    @Mock
    private EmployeeUnavailabilityRepository repository;

    @Mock
    private EmployeeValidationPort employeeValidationPort;

    private ListEmployeeUnavailabilitiesUseCase useCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ListEmployeeUnavailabilitiesUseCase(repository, employeeValidationPort);
    }

    @Test
    @DisplayName("Lista las indisponibilidades de un colaborador existente")
    void listaIndisponibilidadesDeColaborador() {
        when(employeeValidationPort.isEmployeeActive(orgId, empId)).thenReturn(true);

        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                UUID.randomUUID(), orgId, empId,
                TimeInterval.of(Instant.parse("2026-10-15T08:00:00Z"), Instant.parse("2026-10-15T16:00:00Z")),
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                Instant.now(), Instant.now(), 0L, null
        );
        when(repository.findByEmployee(orgId, empId, false)).thenReturn(List.of(unav));

        List<EmployeeUnavailability> results = useCase.execute(orgId, empId, false);

        assertThat(results).containsExactly(unav);
    }

    @Test
    @DisplayName("Rechaza si el colaborador no existe o está inactivo")
    void rechazaSiColaboradorNoExiste() {
        when(employeeValidationPort.isEmployeeActive(orgId, empId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(orgId, empId, false))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("no encontrado o inactivo");
    }
}
