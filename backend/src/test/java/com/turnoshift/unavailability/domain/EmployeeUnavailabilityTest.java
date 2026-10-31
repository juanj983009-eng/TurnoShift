package com.turnoshift.unavailability.domain;

import com.turnoshift.unavailability.domain.exceptions.PastIntervalModificationProhibitedException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityAlreadyCancelledException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeUnavailabilityTest {

    private final UUID orgId = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("Crea una indisponibilidad activa inicial con lock_version 0")
    void creaIndisponibilidadInicial() {
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );

        EmployeeUnavailability unav = EmployeeUnavailability.create(
                orgId, empId, interval, UnavailabilityReason.MEDICO,
                RestrictedNote.of("Consulta"), userId, now
        );

        assertThat(unav.isActive()).isTrue();
        assertThat(unav.isCancelled()).isFalse();
        assertThat(unav.lockVersion()).isEqualTo(0L);
        assertThat(unav.createdAt()).isEqualTo(now);
        assertThat(unav.updatedAt()).isEqualTo(now);
        assertThat(unav.cancellationInfo()).isEmpty();
    }

    @Test
    @DisplayName("Cancela lógicamente una indisponibilidad activa futura (startsAt > now)")
    void cancelaIndisponibilidadFutura() {
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );
        EmployeeUnavailability unav = EmployeeUnavailability.create(
                orgId, empId, interval, UnavailabilityReason.VACACIONES, null, userId, now
        );

        Instant cancelTime = Instant.parse("2026-10-05T10:00:00Z");
        UUID cancelUser = UUID.randomUUID();

        EmployeeUnavailability cancelled = unav.cancel(cancelUser, cancelTime);

        assertThat(cancelled.isCancelled()).isTrue();
        assertThat(cancelled.isActive()).isFalse();
        assertThat(cancelled.cancellationInfo()).isPresent();
        assertThat(cancelled.cancellationInfo().get().cancelledByUserId()).isEqualTo(cancelUser);
        assertThat(cancelled.cancellationInfo().get().cancelledAt()).isEqualTo(cancelTime);
    }

    @Test
    @DisplayName("RN-013: Rechaza cancelar si el intervalo ya inició (startsAt == now)")
    void rechazaCancelarSiIntervaloIniciaExactamenteAhora() {
        Instant t = Instant.parse("2026-10-15T08:00:00Z");
        TimeInterval interval = TimeInterval.of(t, t.plusSeconds(28800));
        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                UUID.randomUUID(), orgId, empId, interval, UnavailabilityReason.MEDICO,
                null, userId, t.minusSeconds(86400), t.minusSeconds(86400), 0L, null
        );

        assertThatThrownBy(() -> unav.cancel(userId, t))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("ya inició o finalizó");
    }

    @Test
    @DisplayName("RN-013: Rechaza cancelar si el intervalo ya inició en el pasado (startsAt < now)")
    void rechazaCancelarSiIntervaloYaInicio() {
        Instant start = Instant.parse("2026-10-15T08:00:00Z");
        Instant end = Instant.parse("2026-10-15T16:00:00Z");
        Instant now = Instant.parse("2026-10-15T09:00:00Z"); // Dentro del intervalo
        TimeInterval interval = TimeInterval.of(start, end);
        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                UUID.randomUUID(), orgId, empId, interval, UnavailabilityReason.MEDICO,
                null, userId, start.minusSeconds(86400), start.minusSeconds(86400), 0L, null
        );

        assertThatThrownBy(() -> unav.cancel(userId, now))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("ya inició o finalizó");
    }

    @Test
    @DisplayName("RN-013: Rechaza modificar si el intervalo ya inició o finalizó (startsAt <= now)")
    void rechazaModificarSiIntervaloYaInicio() {
        Instant start = Instant.parse("2026-10-15T08:00:00Z");
        Instant end = Instant.parse("2026-10-15T16:00:00Z");
        Instant now = Instant.parse("2026-10-15T08:00:00Z");
        TimeInterval interval = TimeInterval.of(start, end);
        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                UUID.randomUUID(), orgId, empId, interval, UnavailabilityReason.MEDICO,
                null, userId, start.minusSeconds(86400), start.minusSeconds(86400), 0L, null
        );

        TimeInterval nuevoIntervalo = TimeInterval.of(start.plusSeconds(3600), end.plusSeconds(3600));

        assertThatThrownBy(() -> unav.update(nuevoIntervalo, UnavailabilityReason.OTRO, null, now))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("ya inició o finalizó");
    }

    @Test
    @DisplayName("Rechaza cancelar una indisponibilidad ya cancelada")
    void rechazaCancelarYaCancelada() {
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );
        EmployeeUnavailability unav = EmployeeUnavailability.create(
                orgId, empId, interval, UnavailabilityReason.VACACIONES, null, userId, now
        );
        EmployeeUnavailability cancelled = unav.cancel(userId, now.plusSeconds(3600));

        assertThatThrownBy(() -> cancelled.cancel(userId, now.plusSeconds(7200)))
                .isInstanceOf(UnavailabilityAlreadyCancelledException.class);
    }
}
