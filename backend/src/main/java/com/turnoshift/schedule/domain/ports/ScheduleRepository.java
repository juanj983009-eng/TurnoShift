package com.turnoshift.schedule.domain.ports;

import com.turnoshift.schedule.domain.Schedule;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para el agregado Schedule (contenedor semanal por sede).
 */
public interface ScheduleRepository {

    Optional<Schedule> findBySiteAndWeek(UUID organizationId, UUID siteId, LocalDate weekStart);

    Optional<Schedule> findById(UUID organizationId, UUID scheduleId);

    Schedule createSchedule(Schedule schedule);

    void updateCurrentPublication(UUID organizationId, UUID scheduleId, UUID currentPublicationId, long expectedLockVersion);
}
