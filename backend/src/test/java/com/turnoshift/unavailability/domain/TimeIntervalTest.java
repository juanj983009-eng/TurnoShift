package com.turnoshift.unavailability.domain;

import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeIntervalTest {

    @Test
    @DisplayName("Crea un intervalo semiabierto válido cuando startsAt < endsAt")
    void creaIntervaloValido() {
        Instant start = Instant.parse("2026-10-15T08:00:00Z");
        Instant end = Instant.parse("2026-10-15T16:00:00Z");

        TimeInterval interval = TimeInterval.of(start, end);

        assertThat(interval.startsAt()).isEqualTo(start);
        assertThat(interval.endsAt()).isEqualTo(end);
    }

    @Test
    @DisplayName("Rechaza instantes nulos")
    void rechazaInstantesNulos() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> TimeInterval.of(null, now))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("startsAt");

        assertThatThrownBy(() -> TimeInterval.of(now, null))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("endsAt");
    }

    @Test
    @DisplayName("Rechaza startsAt igual o posterior a endsAt")
    void rechazaCronologiaInvalida() {
        Instant t = Instant.parse("2026-10-15T08:00:00Z");

        assertThatThrownBy(() -> TimeInterval.of(t, t))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("debe ser anterior");

        assertThatThrownBy(() -> TimeInterval.of(t, t.minusSeconds(60)))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("debe ser anterior");
    }

    @Test
    @DisplayName("Evalúa solapamiento semiabierto estricto (RN-017)")
    void evaluaSolapamientoSemiabierto() {
        TimeInterval base = TimeInterval.of(
                Instant.parse("2026-10-15T10:00:00Z"),
                Instant.parse("2026-10-15T14:00:00Z")
        );

        // Contacto contiguo previo (no solapa)
        assertThat(base.overlaps(TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T10:00:00Z")
        ))).isFalse();

        // Contacto contiguo posterior (no solapa)
        assertThat(base.overlaps(TimeInterval.of(
                Instant.parse("2026-10-15T14:00:00Z"),
                Instant.parse("2026-10-15T18:00:00Z")
        ))).isFalse();

        // Intersección parcial
        assertThat(base.overlaps(TimeInterval.of(
                Instant.parse("2026-10-15T12:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        ))).isTrue();

        // Contención total
        assertThat(base.overlaps(TimeInterval.of(
                Instant.parse("2026-10-15T11:00:00Z"),
                Instant.parse("2026-10-15T13:00:00Z")
        ))).isTrue();
    }
}
