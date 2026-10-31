package com.turnoshift.schedule.domain.ports;

import com.turnoshift.schedule.domain.DraftAssignment;
import com.turnoshift.schedule.domain.DraftStatus;
import com.turnoshift.schedule.domain.ScheduleDraft;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para el agregado ScheduleDraft y sus líneas DraftAssignment.
 */
public interface ScheduleDraftRepository {

    Optional<ScheduleDraft> findOpenDraftBySchedule(UUID organizationId, UUID scheduleId);

    Optional<ScheduleDraft> findById(UUID organizationId, UUID draftId);

    ScheduleDraft createDraft(ScheduleDraft draft);

    void updateDraftStatus(UUID organizationId, UUID draftId, DraftStatus status, UUID resultPublicationId, long expectedLockVersion);

    void updateDraftLockVersion(UUID organizationId, UUID draftId, long expectedLockVersion);

    List<DraftAssignment> findAssignmentsByDraftId(UUID organizationId, UUID draftId);

    DraftAssignment insertAssignment(DraftAssignment assignment, long expectedDraftLockVersion);

    void updateAssignment(DraftAssignment assignment, long expectedDraftLockVersion);

    void deleteAssignment(UUID organizationId, UUID draftId, UUID assignmentId, long expectedDraftLockVersion);

    void deleteAssignmentsByDraftId(UUID organizationId, UUID draftId, long expectedDraftLockVersion);
}
