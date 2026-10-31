package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.EmployeeValidationPort;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import com.turnoshift.unavailability.domain.exceptions.EmployeeNotFoundException;
import com.turnoshift.unavailability.domain.exceptions.PastIntervalModificationProhibitedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateUnavailabilityUseCaseTest {

    @Mock
    private EmployeeUnavailabilityRepository repository;

    @Mock
    private EmployeeValidationPort employeeValidationPort;

    private Clock clock;
    private CreateUnavailabilityUseCase useCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        useCase = new CreateUnavailabilityUseCase(repository, employeeValidationPort, clock);
    }

    @Test
    @DisplayName("Crea exitosamente una indisponibilidad para un colaborador activo")
    void creaExitosamenteIndisponibilidad() {
        when(employeeValidationPort.isEmployeeActive(orgId, empId)).thenReturn(true);
        when(repository.insert(any(EmployeeUnavailability.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant startsAt = Instant.parse("2026-10-15T08:00:00Z");
        Instant endsAt = Instant.parse("2026-10-15T16:00:00Z");

        EmployeeUnavailability result = useCase.execute(
                orgId, empId, startsAt, endsAt, UnavailabilityReason.MEDICO, "Cita medica", userId
        );

        assertThat(result).isNotNull();
        assertThat(result.organizationId()).isEqualTo(orgId);
        assertThat(result.employeeId()).isEqualTo(empId);
        assertThat(result.interval().startsAt()).isEqualTo(startsAt);
        assertThat(result.interval().endsAt()).isEqualTo(endsAt);
        assertThat(result.reason()).contains(UnavailabilityReason.MEDICO);
        assertThat(result.restrictedNote().get().value()).isEqualTo("Cita medica");

        ArgumentCaptor<EmployeeUnavailability> captor = ArgumentCaptor.forClass(EmployeeUnavailability.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().createdAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Rechaza si el colaborador no existe o está inactivo")
    void rechazaSiEmpleadoNoExisteOInactivo() {
        when(employeeValidationPort.isEmployeeActive(orgId, empId)).thenReturn(false);

        Instant startsAt = Instant.parse("2026-10-15T08:00:00Z");
        Instant endsAt = Instant.parse("2026-10-15T16:00:00Z");

        assertThatThrownBy(() -> useCase.execute(
                orgId, empId, startsAt, endsAt, UnavailabilityReason.VACACIONES, null, userId
        ))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("no encontrado o inactivo");
    }

    @Test
    @DisplayName("Rechaza si el intervalo completo ya finalizó en el pasado (endsAt <= now)")
    void rechazaIntervaloCompletamenteEnElPasado() {
        when(employeeValidationPort.isEmployeeActive(orgId, empId)).thenReturn(true);

        Instant startsAt = Instant.parse("2026-09-01T08:00:00Z");
        Instant endsAt = Instant.parse("2026-09-01T16:00:00Z"); // anterior a now (2026-10-01)

        assertThatThrownBy(() -> useCase.execute(
                orgId, empId, startsAt, endsAt, UnavailabilityReason.VACACIONES, null, userId
        ))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("ya finalizado");
    }

    @Test
    @DisplayName("Permite solapamiento: registra sin error aunque coincida con otra indisponibilidad")
    void permiteSolapamiento() {
        when(employeeValidationPort.isEmployeeActive(orgId, empId)).thenReturn(true);
        when(repository.insert(any(EmployeeUnavailability.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant startsAt = Instant.parse("2026-10-15T08:00:00Z");
        Instant endsAt = Instant.parse("2026-10-15T16:00:00Z");

        EmployeeUnavailability result = useCase.execute(
                orgId, empId, startsAt, endsAt, UnavailabilityReason.VACACIONES, null, userId
        );

        assertThat(result).isNotNull();
        verify(repository).insert(any(EmployeeUnavailability.class));
    }
}
