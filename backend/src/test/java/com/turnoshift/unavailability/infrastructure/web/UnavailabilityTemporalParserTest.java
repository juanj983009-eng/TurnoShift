package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: UnavailabilityTemporalParser — Validación y Expansión Temporal")
class UnavailabilityTemporalParserTest {

    private final ZoneId bogotaZone = ZoneId.of("America/Bogota"); // UTC-5 fijo
    private final ZoneId madridZone = ZoneId.of("Europe/Madrid"); // DST: UTC+1 invierno, UTC+2 verano
    private final ZoneId newYorkZone = ZoneId.of("America/New_York"); // DST: UTC-5 invierno, UTC-4 verano

    // =========================================================================
    // 1. Formatos Válidos y DST
    // =========================================================================

    @Test
    @DisplayName("Formato parcial UTC válido: parsea instantes exactos y devuelve TimeInterval")
    void formatoParcialValido() {
        TimeInterval parsed = UnavailabilityTemporalParser.parse(
                "2026-10-15T14:00:00Z",
                "2026-10-15T18:00:00Z",
                null,
                null,
                null,
                bogotaZone
        );

        assertThat(parsed.startsAt()).isEqualTo(Instant.parse("2026-10-15T14:00:00Z"));
        assertThat(parsed.endsAt()).isEqualTo(Instant.parse("2026-10-15T18:00:00Z"));
        assertThat(Duration.between(parsed.startsAt(), parsed.endsAt()).toHours()).isEqualTo(4);
    }

    @Test
    @DisplayName("Formato día completo válido: expande fechas locales con endDate exclusiva en America/Bogota (UTC-5 fijo, 48h)")
    void formatoDiaCompletoBogota() {
        TimeInterval parsed = UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-10-15",
                "2026-10-17",
                bogotaZone
        );

        // 2026-10-15 00:00:00 en America/Bogota (-05:00) es 05:00:00 UTC
        assertThat(parsed.startsAt()).isEqualTo(Instant.parse("2026-10-15T05:00:00Z"));
        // 2026-10-17 00:00:00 en America/Bogota (-05:00) es 05:00:00 UTC (48 horas exactas)
        assertThat(parsed.endsAt()).isEqualTo(Instant.parse("2026-10-17T05:00:00Z"));
        assertThat(Duration.between(parsed.startsAt(), parsed.endsAt()).toHours()).isEqualTo(48);
    }

    @Test
    @DisplayName("DST caso 1: día completo cruzando cambio de horario de verano en Madrid (DST spring forward, 23h)")
    void formatoDiaCompletoMadridDstSpring() {
        // En 2026, el cambio de hora en Europa es el domingo 29 de marzo (02:00 -> 03:00, se adelanta 1h)
        TimeInterval parsed = UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-03-29",
                "2026-03-30",
                madridZone
        );

        // 29 de marzo a medianoche en Madrid es UTC+1 -> 2026-03-28T23:00:00Z
        assertThat(parsed.startsAt()).isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
        // 30 de marzo a medianoche en Madrid ya es UTC+2 -> 2026-03-29T22:00:00Z (23 horas exactas)
        assertThat(parsed.endsAt()).isEqualTo(Instant.parse("2026-03-29T22:00:00Z"));
        assertThat(Duration.between(parsed.startsAt(), parsed.endsAt()).toHours()).isEqualTo(23);
    }

    @Test
    @DisplayName("DST caso 2: día completo cruzando cambio de horario de invierno en New York (DST fall back, 25h)")
    void formatoDiaCompletoNewYorkDstFall() {
        // En 2026, el cambio en EE.UU. ocurre el domingo 1 de noviembre (02:00 -> 01:00, se atrasa 1h)
        TimeInterval parsed = UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-11-01",
                "2026-11-02",
                newYorkZone
        );

        // 1 de noviembre a medianoche en NY es EDT (UTC-4) -> 2026-11-01T04:00:00Z
        assertThat(parsed.startsAt()).isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
        // 2 de noviembre a medianoche en NY ya es EST (UTC-5) -> 2026-11-02T05:00:00Z (25 horas exactas)
        assertThat(parsed.endsAt()).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"));
        assertThat(Duration.between(parsed.startsAt(), parsed.endsAt()).toHours()).isEqualTo(25);
    }

    // =========================================================================
    // 2. Exclusión Mutua (Payloads Mixtos)
    // =========================================================================

    @Test
    @DisplayName("Payload mixto: allDay true con startsAt presente es rechazado")
    void payloadMixtoRechazado_allDayTrueConStartsAt() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                "2026-10-15T14:00:00Z",
                null,
                true,
                "2026-10-15",
                "2026-10-16",
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("No se permite mezclar el formato de rango parcial con el formato de día completo");
    }

    @Test
    @DisplayName("Payload mixto: allDay true con endsAt presente es rechazado")
    void payloadMixtoRechazado_allDayTrueConEndsAt() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                null,
                "2026-10-15T18:00:00Z",
                true,
                "2026-10-15",
                "2026-10-16",
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("No se permite mezclar el formato de rango parcial con el formato de día completo");
    }

    @Test
    @DisplayName("Payload mixto: allDay false con startDate presente es rechazado")
    void payloadMixtoRechazado_allDayFalseConStartDate() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                "2026-10-15T14:00:00Z",
                "2026-10-15T18:00:00Z",
                false,
                "2026-10-15",
                null,
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("Los campos 'startDate' y 'endDate' solo se permiten cuando 'allDay' es true");
    }

    @Test
    @DisplayName("Payload mixto: allDay null con endDate presente es rechazado")
    void payloadMixtoRechazado_allDayNullConEndDate() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                "2026-10-15T14:00:00Z",
                "2026-10-15T18:00:00Z",
                null,
                null,
                "2026-10-16",
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("Los campos 'startDate' y 'endDate' solo se permiten cuando 'allDay' es true");
    }

    // =========================================================================
    // 3. Payloads Incompletos
    // =========================================================================

    @Test
    @DisplayName("Payload incompleto: allDay true sin endDate es rechazado")
    void payloadIncompletoAllDaySinEndDate() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-10-15",
                null,
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("El formato de día completo requiere 'startDate' y 'endDate'");
    }

    @Test
    @DisplayName("Payload incompleto: allDay true sin startDate es rechazado")
    void payloadIncompletoAllDaySinStartDate() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                null,
                "2026-10-16",
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("El formato de día completo requiere 'startDate' y 'endDate'");
    }

    @Test
    @DisplayName("Payload incompleto: formato parcial sin endsAt es rechazado")
    void payloadIncompletoParcialSinEndsAt() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                "2026-10-15T14:00:00Z",
                null,
                false,
                null,
                null,
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("El formato de rango parcial requiere 'startsAt' y 'endsAt'");
    }

    @Test
    @DisplayName("Payload incompleto: formato parcial sin startsAt es rechazado")
    void payloadIncompletoParcialSinStartsAt() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                null,
                "2026-10-15T18:00:00Z",
                null,
                null,
                null,
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("El formato de rango parcial requiere 'startsAt' y 'endsAt'");
    }

    // =========================================================================
    // 4. Cronología y Formatos Inválidos
    // =========================================================================

    @Test
    @DisplayName("Cronología invertida en parcial: startsAt posterior a endsAt es rechazado")
    void cronologiaInvertidaParcial() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                "2026-10-15T18:00:00Z",
                "2026-10-15T14:00:00Z",
                false,
                null,
                null,
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("startsAt debe ser anterior a endsAt");
    }

    @Test
    @DisplayName("Cronología igual en parcial: startsAt igual a endsAt es rechazado")
    void cronologiaIgualParcial() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                "2026-10-15T14:00:00Z",
                "2026-10-15T14:00:00Z",
                null,
                null,
                null,
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("startsAt debe ser anterior a endsAt");
    }

    @Test
    @DisplayName("Cronología invertida en día completo: startDate posterior a endDate es rechazado")
    void cronologiaInvertidaDiaCompleto() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-10-20",
                "2026-10-15",
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("startDate debe ser anterior a endDate");
    }

    @Test
    @DisplayName("Cronología igual en día completo: startDate igual a endDate es rechazado (endDate es exclusiva)")
    void cronologiaIgualDiaCompleto() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-10-15",
                "2026-10-15",
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("startDate debe ser anterior a endDate");
    }

    @Test
    @DisplayName("Formato de fecha inválido en día completo es rechazado")
    void formatoFechaInvalidoDiaCompleto() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "15/10/2026",
                "2026-10-16",
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("Las fechas 'startDate' y 'endDate' deben tener formato ISO-8601 (YYYY-MM-DD)");
    }

    @Test
    @DisplayName("Formato de instante inválido en parcial es rechazado")
    void formatoFechaInvalidoParcial() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                "2026-10-15 14:00",
                "2026-10-15T18:00:00Z",
                null,
                null,
                null,
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("Los campos 'startsAt' y 'endsAt' deben tener formato ISO-8601 UTC válido");
    }

    // =========================================================================
    // 5. Límites y Precondiciones
    // =========================================================================

    @Test
    @DisplayName("Rango mayor a 365 días es rechazado")
    void rangoMayorA365DiasRechazado() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-01-01",
                "2027-01-05",
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("El rango de indisponibilidad no puede exceder 365 días");
    }

    @Test
    @DisplayName("ZoneId nulo lanza NullPointerException")
    void zoneIdNuloRechazado() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                "2026-10-15T14:00:00Z",
                "2026-10-15T18:00:00Z",
                null,
                null,
                null,
                null
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("zoneId no puede ser nulo");
    }
}
