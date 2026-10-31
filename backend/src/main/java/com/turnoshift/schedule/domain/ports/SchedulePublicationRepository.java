package com.turnoshift.schedule.domain.ports;

import com.turnoshift.schedule.domain.Assignment;
import com.turnoshift.schedule.domain.AssignmentRevision;
import com.turnoshift.schedule.domain.PlanningChangeSet;
import com.turnoshift.schedule.domain.PlanningWarningAcknowledgement;
import com.turnoshift.schedule.domain.PublishedAssignmentRef;
import com.turnoshift.schedule.domain.PublishedShiftRecord;
import com.turnoshift.schedule.domain.SchedulePublication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para el agregado SchedulePublication, su histórico y la secuencia de publicación atómica.
 */
public interface SchedulePublicationRepository {

    Optional<SchedulePublication> findById(UUID organizationId, UUID publicationId);

    Optional<SchedulePublication> findCurrentPublication(UUID organizationId, UUID scheduleId);

    List<SchedulePublication> findPublicationHistory(UUID organizationId, UUID scheduleId);

    int findNextPublicationNumber(UUID organizationId, UUID scheduleId);

    List<PublishedShiftRecord> findPublishedShiftsInInterval(UUID organizationId, UUID siteId, Instant from, Instant to);

    SchedulePublication createPublicationAtomic(
            SchedulePublication publication,
            PlanningChangeSet changeSet,
            List<PlanningWarningAcknowledgement> acknowledgements,
            List<Assignment> newAssignments,
            List<AssignmentRevision> newRevisions,
            List<PublishedAssignmentRef> publicationAssignments,
            long expectedDraftLockVersion,
            long expectedScheduleLockVersion
    );
}
