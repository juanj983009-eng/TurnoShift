package com.turnoshift.schedule.domain.validation;

import com.turnoshift.schedule.domain.PublishedShiftRecord;
import java.util.List;

/**
 * Instantánea de turnos publicados en las semanas adyacentes (W-1 y W+1) relevantes para solapamientos y descansos.
 */
public record FrontierShiftsSnapshot(
        List<PublishedShiftRecord> previousWeekShifts,
        List<PublishedShiftRecord> nextWeekShifts
) {
    public FrontierShiftsSnapshot {
        previousWeekShifts = previousWeekShifts == null ? List.of() : List.copyOf(previousWeekShifts);
        nextWeekShifts = nextWeekShifts == null ? List.of() : List.copyOf(nextWeekShifts);
    }
}
