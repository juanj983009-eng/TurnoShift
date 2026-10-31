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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelUnavailabilityUseCaseTest {

    @Mock
    private EmployeeUnavailabilityRepository repository;

    private Clock clock;
    private CancelUnavailabilityUseCase useCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID unavId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        useCase = new CancelUnavailabilityUseCase(repository, clock);
    }

    @Test
    @DisplayName("Cancela exitosamente una indisponibilidad activa futura")
    void cancelaExitosamenteIndisponibilidadFutura() {
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );
        EmployeeUnavailability existing = EmployeeUnavailability.reconstruct(
                unavId, orgId, UUID.randomUUID(), interval,
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                now.minusSeconds(86400), now.minusSeconds(86400), 0L, null
        );
        when(repository.findById(orgId, unavId)).thenReturn(Optional.of(existing));

        EmployeeUnavailability cancelled = EmployeeUnavailability.reconstruct(
                unavId, orgId, existing.employeeId(), interval,
                UnavailabilityReason.VACACIONES, null, existing.createdByUserId(),
                existing.createdAt(), now, 1L,
                new CancellationInfo(now, actorUserId)
        );
        when(repository.cancel(orgId, unavId, actorUserId, 0L)).thenReturn(Optional.of(cancelled));

        EmployeeUnavailability result = useCase.execute(orgId, unavId, actorUserId, 0L);

        assertThat(result).isNotNull();
        assertThat(result.isCancelled()).isTrue();
        assertThat(result.cancellationInfo()).isPresent();
        assertThat(result.cancellationInfo().get().cancelledByUserId()).isEqualTo(actorUserId);
    }

    @Test
    @DisplayName("Rechaza con UnavailabilityNotFoundException si no existe")
    void rechazaSiNoExiste() {
        when(repository.findById(orgId, unavId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, unavId, actorUserId, 0L))
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

        assertThatThrownBy(() -> useCase.execute(orgId, unavId, actorUserId, 1L))
                .isInstanceOf(UnavailabilityAlreadyCancelledException.class);
    }

    @Test
    @DisplayName("RN-013: Rechaza con PastIntervalModificationProhibitedException si startsAt <= now")
    void rechazaSiIntervaloYaInicio() {
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

        assertThatThrownBy(() -> useCase.execute(orgId, unavId, actorUserId, 0L))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("ya inició o finalizó");
    }

    @Test
    @DisplayName("Lanza UnavailabilityConcurrentModificationException si el repositorio cancel retorna vacío")
    void lanzaExcepcionDeConcurrenciaOptimista() {
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );
        EmployeeUnavailability existing = EmployeeUnavailability.reconstruct(
                unavId, orgId, UUID.randomUUID(), interval,
                UnavailabilityReason.VACACIONES, null, UUID.randomUUID(),
                now.minusSeconds(86400), now.minusSeconds(86400), 0L, null
        );
        when(repository.findById(orgId, unavId)).thenReturn(Optional.of(existing));
        when(repository.cancel(orgId, unavId, actorUserId, 0L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, unavId, actorUserId, 0L))
                .isInstanceOf(UnavailabilityConcurrentModificationException.class);
    }
}
