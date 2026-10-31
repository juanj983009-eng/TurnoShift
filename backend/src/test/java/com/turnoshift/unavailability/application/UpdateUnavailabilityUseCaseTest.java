package com.turnoshift.unavailability.application;

import com.turnoshift.unavailability.domain.CancellationInfo;
import com.turnoshift.unavailability.domain.EmployeeUnavailability;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.UnavailabilityReason;
import com.turnoshift.unavailability.domain.exceptions.PastIntervalModificationProhibitedException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityAlreadyCancelledException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityConcurrentModificationException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUnavailabilityUseCaseTest {

    @Mock
    private EmployeeUnavailabilityRepository repository;

    private Clock clock;
    private UpdateUnavailabilityUseCase useCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID unavId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        useCase = new UpdateUnavailabilityUseCase(repository, clock);
    }

    @Test
    @DisplayName("Actualiza exitosamente una indisponibilidad activa futura")
    void actualizaExitosamenteIndisponibilidadFutura() {
        TimeInterval originalInterval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );
        EmployeeUnavailability existing = EmployeeUnavailability.reconstruct(
                unavId, orgId, UUID.randomUUID(), originalInterval,
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                now.minusSeconds(86400), now.minusSeconds(86400), 0L, null
        );
        when(repository.findById(orgId, unavId)).thenReturn(Optional.of(existing));

        Instant newStart = Instant.parse("2026-10-16T08:00:00Z");
        Instant newEnd = Instant.parse("2026-10-16T16:00:00Z");

        when(repository.update(any(EmployeeUnavailability.class), eq(0L)))
                .thenAnswer(inv -> Optional.of(inv.getArgument(0)));

        EmployeeUnavailability updated = useCase.execute(
                orgId, unavId, newStart, newEnd, UnavailabilityReason.MEDICO, "Nueva nota", 0L
        );

        assertThat(updated).isNotNull();
        assertThat(updated.interval().startsAt()).isEqualTo(newStart);
        assertThat(updated.interval().endsAt()).isEqualTo(newEnd);
        assertThat(updated.reason()).contains(UnavailabilityReason.MEDICO);
    }

    @Test
    @DisplayName("Rechaza con UnavailabilityNotFoundException si no existe")
    void rechazaSiNoExiste() {
        when(repository.findById(orgId, unavId)).thenReturn(Optional.empty());

        Instant newStart = Instant.parse("2026-10-16T08:00:00Z");
        Instant newEnd = Instant.parse("2026-10-16T16:00:00Z");

        assertThatThrownBy(() -> useCase.execute(orgId, unavId, newStart, newEnd, UnavailabilityReason.MEDICO, null, 0L))
                .isInstanceOf(UnavailabilityNotFoundException.class);
    }

    @Test
    @DisplayName("Rechaza con UnavailabilityAlreadyCancelledException si ya está cancelada")
    void rechazaSiYaEstaCancelada() {
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );
        EmployeeUnavailability cancelled = EmployeeUnavailability.reconstruct(
                unavId, orgId, UUID.randomUUID(), interval,
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                now.minusSeconds(86400), now.minusSeconds(86400), 1L,
                new CancellationInfo(now.minusSeconds(3600), UUID.randomUUID())
        );
        when(repository.findById(orgId, unavId)).thenReturn(Optional.of(cancelled));

        Instant newStart = Instant.parse("2026-10-16T08:00:00Z");
        Instant newEnd = Instant.parse("2026-10-16T16:00:00Z");

        assertThatThrownBy(() -> useCase.execute(orgId, unavId, newStart, newEnd, UnavailabilityReason.MEDICO, null, 1L))
                .isInstanceOf(UnavailabilityAlreadyCancelledException.class);
    }

    @Test
    @DisplayName("RN-013: Rechaza con PastIntervalModificationProhibitedException si startsAt <= now")
    void rechazaSiIntervaloYaInicio() {
        // startsAt anterior a now (now = 2026-10-01T12:00:00Z)
        TimeInterval pastInterval = TimeInterval.of(
                Instant.parse("2026-10-01T08:00:00Z"),
                Instant.parse("2026-10-01T16:00:00Z")
        );
        EmployeeUnavailability existing = EmployeeUnavailability.reconstruct(
                unavId, orgId, UUID.randomUUID(), pastInterval,
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                now.minusSeconds(86400), now.minusSeconds(86400), 0L, null
        );
        when(repository.findById(orgId, unavId)).thenReturn(Optional.of(existing));

        Instant newStart = Instant.parse("2026-10-16T08:00:00Z");
        Instant newEnd = Instant.parse("2026-10-16T16:00:00Z");

        assertThatThrownBy(() -> useCase.execute(orgId, unavId, newStart, newEnd, UnavailabilityReason.MEDICO, null, 0L))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("ya inició o finalizó");
    }

    @Test
    @DisplayName("Lanza UnavailabilityConcurrentModificationException si el repositorio retorna vacío")
    void lanzaExcepcionDeConcurrenciaOptimista() {
        TimeInterval originalInterval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );
        EmployeeUnavailability existing = EmployeeUnavailability.reconstruct(
                unavId, orgId, UUID.randomUUID(), originalInterval,
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                now.minusSeconds(86400), now.minusSeconds(86400), 0L, null
        );
        when(repository.findById(orgId, unavId)).thenReturn(Optional.of(existing));

        Instant newStart = Instant.parse("2026-10-16T08:00:00Z");
        Instant newEnd = Instant.parse("2026-10-16T16:00:00Z");

        when(repository.update(any(EmployeeUnavailability.class), eq(0L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, unavId, newStart, newEnd, UnavailabilityReason.MEDICO, null, 0L))
                .isInstanceOf(UnavailabilityConcurrentModificationException.class);
    }

    @Test
    @DisplayName("RN-013: Rechaza si el nuevo intervalo propuesto tiene newStartsAt en el pasado")
    void rechazaSiNuevoIntervaloTieneStartsAtEnElPasado() {
        TimeInterval futureInterval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );
        EmployeeUnavailability existing = EmployeeUnavailability.reconstruct(
                unavId, orgId, UUID.randomUUID(), futureInterval,
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                now.minusSeconds(86400), now.minusSeconds(86400), 0L, null
        );
        when(repository.findById(orgId, unavId)).thenReturn(Optional.of(existing));

        // newStartsAt en el pasado respecto a now (now = 2026-10-01T12:00:00Z)
        Instant newStart = Instant.parse("2026-10-01T10:00:00Z");
        Instant newEnd = Instant.parse("2026-10-15T16:00:00Z");

        assertThatThrownBy(() -> useCase.execute(orgId, unavId, newStart, newEnd, UnavailabilityReason.MEDICO, null, 0L))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("futuro");
    }

    @Test
    @DisplayName("RN-013: Rechaza si el nuevo intervalo propuesto tiene newStartsAt exactamente igual a now")
    void rechazaSiNuevoIntervaloTieneStartsAtIgualANow() {
        TimeInterval futureInterval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );
        EmployeeUnavailability existing = EmployeeUnavailability.reconstruct(
                unavId, orgId, UUID.randomUUID(), futureInterval,
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                now.minusSeconds(86400), now.minusSeconds(86400), 0L, null
        );
        when(repository.findById(orgId, unavId)).thenReturn(Optional.of(existing));

        // newStartsAt == now
        Instant newStart = now;
        Instant newEnd = Instant.parse("2026-10-15T16:00:00Z");

        assertThatThrownBy(() -> useCase.execute(orgId, unavId, newStart, newEnd, UnavailabilityReason.MEDICO, null, 0L))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("futuro");
    }

    @Test
    @DisplayName("RN-013: Rechaza si la indisponibilidad original tiene startsAt exactamente igual a now")
    void rechazaSiIntervaloOriginalTieneStartsAtIgualANow() {
        TimeInterval intervalAtNow = TimeInterval.of(
                now,
                now.plusSeconds(14400)
        );
        EmployeeUnavailability existing = EmployeeUnavailability.reconstruct(
                unavId, orgId, UUID.randomUUID(), intervalAtNow,
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                now.minusSeconds(86400), now.minusSeconds(86400), 0L, null
        );
        when(repository.findById(orgId, unavId)).thenReturn(Optional.of(existing));

        Instant newStart = now.plusSeconds(3600);
        Instant newEnd = now.plusSeconds(7200);

        assertThatThrownBy(() -> useCase.execute(orgId, unavId, newStart, newEnd, UnavailabilityReason.MEDICO, null, 0L))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("ya inició o finalizó");
    }
}
