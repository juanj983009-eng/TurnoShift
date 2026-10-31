package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.unavailability.domain.TimeInterval;
import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Validador y analizador de formatos temporales para la entrada de indisponibilidades.
 *
 * <p>Asegura la exclusión mutua estricta entre el formato parcial (instantes UTC)
 * y el formato de día completo (fechas locales de calendario expandidas a UTC mediante la zona horaria del tenant).
 */
public final class UnavailabilityTemporalParser {

    private static final long MAX_ALLOWED_DAYS = 365;

    private UnavailabilityTemporalParser() {
        // Utilidad estática
    }

    public static TimeInterval parse(
            String startsAtRaw,
            String endsAtRaw,
            Boolean allDay,
            String startDateRaw,
            String endDateRaw,
            ZoneId zoneId
    ) {
        Objects.requireNonNull(zoneId, "zoneId no puede ser nulo");

        boolean isAllDay = Boolean.TRUE.equals(allDay);
        boolean hasUtcFields = (startsAtRaw != null && !startsAtRaw.isBlank())
                || (endsAtRaw != null && !endsAtRaw.isBlank());
        boolean hasLocalFields = (startDateRaw != null && !startDateRaw.isBlank())
                || (endDateRaw != null && !endDateRaw.isBlank());

        // Regla 1: Exclusión mutua de ambos formatos
        if (isAllDay && hasUtcFields) {
            throw new InvalidUnavailabilityDataException(
                    "No se permite mezclar el formato de rango parcial con el formato de día completo ('allDay': true con 'startsAt' o 'endsAt').");
        }
        if (!isAllDay && hasLocalFields) {
            throw new InvalidUnavailabilityDataException(
                    "Los campos 'startDate' y 'endDate' solo se permiten cuando 'allDay' es true.");
        }

        Instant startsAt;
        Instant endsAt;

        if (isAllDay) {
            if (startDateRaw == null || startDateRaw.isBlank() || endDateRaw == null || endDateRaw.isBlank()) {
                throw new InvalidUnavailabilityDataException(
                        "El formato de día completo requiere 'startDate' y 'endDate' con formato YYYY-MM-DD.");
            }

            LocalDate startDate;
            LocalDate endDate;
            try {
                startDate = LocalDate.parse(startDateRaw.trim());
                endDate = LocalDate.parse(endDateRaw.trim());
            } catch (DateTimeParseException e) {
                throw new InvalidUnavailabilityDataException(
                        "Las fechas 'startDate' y 'endDate' deben tener formato ISO-8601 (YYYY-MM-DD).", e);
            }

            if (!startDate.isBefore(endDate)) {
                throw new InvalidUnavailabilityDataException(
                        "startDate debe ser anterior a endDate (la fecha fin es exclusiva).");
            }

            try {
                startsAt = startDate.atStartOfDay(zoneId).toInstant();
                endsAt = endDate.atStartOfDay(zoneId).toInstant();
            } catch (DateTimeException e) {
                throw new InvalidUnavailabilityDataException(
                        "Error expandiendo fechas locales a UTC en la zona horaria: " + zoneId.getId(), e);
            }

        } else {
            if (startsAtRaw == null || startsAtRaw.isBlank() || endsAtRaw == null || endsAtRaw.isBlank()) {
                throw new InvalidUnavailabilityDataException(
                        "El formato de rango parcial requiere 'startsAt' y 'endsAt' en formato UTC ISO-8601.");
            }

            try {
                startsAt = Instant.parse(startsAtRaw.trim());
                endsAt = Instant.parse(endsAtRaw.trim());
            } catch (DateTimeParseException e) {
                throw new InvalidUnavailabilityDataException(
                        "Los campos 'startsAt' y 'endsAt' deben tener formato ISO-8601 UTC válido (ej. 2026-10-15T14:00:00Z).", e);
            }

            if (!startsAt.isBefore(endsAt)) {
                throw new InvalidUnavailabilityDataException(
                        "startsAt debe ser anterior a endsAt.");
            }
        }

        long daysBetween = ChronoUnit.DAYS.between(startsAt, endsAt);
        if (daysBetween > MAX_ALLOWED_DAYS) {
            throw new InvalidUnavailabilityDataException(
                    "El rango de indisponibilidad no puede exceder 365 días (días solicitados: " + daysBetween + ").");
        }

        return new TimeInterval(startsAt, endsAt);
    }
}
