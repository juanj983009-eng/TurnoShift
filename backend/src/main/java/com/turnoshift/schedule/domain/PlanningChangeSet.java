package com.turnoshift.schedule.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de auditoría y trazabilidad de una operación confirmada de planificación.
 */
public record PlanningChangeSet(
        UUID id,
        UUID organizationId,
        UUID scheduleId,
        PlanningChangeType changeType,
        UUID actorUserId,
        UUID policyVersionId,
        UUID sourceDraftId,
        String reason,
        boolean emptyScheduleConfirmed,
        byte[] validationFingerprint,
        int validationSchemaVersion,
        UUID correlationId,
        Instant confirmedAt
) {

    public PlanningChangeSet {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");
        Objects.requireNonNull(changeType, "changeType no puede ser nulo");
        Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");
        Objects.requireNonNull(policyVersionId, "policyVersionId no puede ser nulo");
        Objects.requireNonNull(validationFingerprint, "validationFingerprint no puede ser nulo");
        if (validationFingerprint.length != 32) {
            throw new ScheduleDomainException("validationFingerprint debe tener exactamente 32 bytes (SHA-256)");
        }
        if (emptyScheduleConfirmed && (reason == null || reason.isBlank())) {
            throw new ScheduleDomainException("Una publicación vacía confirmada requiere un motivo obligatorio no vacío");
        }
        if (changeType != PlanningChangeType.APROBACION_INTERCAMBIO && sourceDraftId == null) {
            throw new ScheduleDomainException("sourceDraftId es obligatorio para publicaciones basadas en borrador");
        }
        if (validationSchemaVersion <= 0) {
            throw new ScheduleDomainException("validationSchemaVersion debe ser estrictamente positivo");
        }

        // Copia defensiva en constructor
        validationFingerprint = validationFingerprint.clone();
    }

    // Copia defensiva en accessor
    @Override
    public byte[] validationFingerprint() {
        return validationFingerprint.clone();
    }

    public String validationFingerprintHex() {
        return HexFormat.of().formatHex(validationFingerprint);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanningChangeSet that = (PlanningChangeSet) o;
        return emptyScheduleConfirmed == that.emptyScheduleConfirmed &&
                validationSchemaVersion == that.validationSchemaVersion &&
                Objects.equals(id, that.id) &&
                Objects.equals(organizationId, that.organizationId) &&
                Objects.equals(scheduleId, that.scheduleId) &&
                changeType == that.changeType &&
                Objects.equals(actorUserId, that.actorUserId) &&
                Objects.equals(policyVersionId, that.policyVersionId) &&
                Objects.equals(sourceDraftId, that.sourceDraftId) &&
                Objects.equals(reason, that.reason) &&
                Arrays.equals(validationFingerprint, that.validationFingerprint) &&
                Objects.equals(correlationId, that.correlationId) &&
                Objects.equals(confirmedAt, that.confirmedAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, organizationId, scheduleId, changeType, actorUserId,
                policyVersionId, sourceDraftId, reason, emptyScheduleConfirmed,
                validationSchemaVersion, correlationId, confirmedAt);
        result = 31 * result + Arrays.hashCode(validationFingerprint);
        return result;
    }
}
