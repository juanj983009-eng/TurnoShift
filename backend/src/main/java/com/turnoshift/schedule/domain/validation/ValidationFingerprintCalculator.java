package com.turnoshift.schedule.domain.validation;

import com.turnoshift.schedule.domain.DraftAssignment;
import com.turnoshift.schedule.domain.PublishedShiftRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Calculador determinista de la huella criptográfica SHA-256 (32 bytes) para la previsualización de validación.
 * Normaliza y ordena canónicamente todas las asignaciones del borrador, turnos de frontera y advertencias.
 */
public final class ValidationFingerprintCalculator {

    private static final Comparator<DraftAssignment> DRAFT_ASSIGNMENT_COMPARATOR = Comparator
            .comparing(DraftAssignment::startsAt)
            .thenComparing(DraftAssignment::endsAt)
            .thenComparing(DraftAssignment::employeeId)
            .thenComparing(DraftAssignment::positionId);

    private static final Comparator<PublishedShiftRecord> FRONTIER_SHIFT_COMPARATOR = Comparator
            .comparing(PublishedShiftRecord::startsAt)
            .thenComparing(PublishedShiftRecord::endsAt)
            .thenComparing(PublishedShiftRecord::employeeId)
            .thenComparing(PublishedShiftRecord::positionId);

    private static final Comparator<ValidationWarning> WARNING_COMPARATOR = Comparator
            .comparing(ValidationWarning::warningFingerprintHex);

    private ValidationFingerprintCalculator() {
    }

    public static byte[] calculate(
            UUID organizationId,
            UUID scheduleId,
            UUID policyVersionId,
            long draftLockVersion,
            List<DraftAssignment> assignments,
            FrontierShiftsSnapshot frontierShifts,
            List<ValidationWarning> warnings
    ) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(scheduleId, "scheduleId no puede ser nulo");

        StringBuilder sb = new StringBuilder();
        sb.append("v1|")
                .append(organizationId).append("|")
                .append(scheduleId).append("|")
                .append(policyVersionId != null ? policyVersionId.toString() : "null").append("|")
                .append(draftLockVersion).append("|");

        if (assignments != null && !assignments.isEmpty()) {
            List<DraftAssignment> sortedAssignments = new ArrayList<>(assignments);
            sortedAssignments.sort(DRAFT_ASSIGNMENT_COMPARATOR);
            for (DraftAssignment a : sortedAssignments) {
                sb.append("shift:")
                        .append(a.employeeId()).append(":")
                        .append(a.positionId()).append(":")
                        .append(a.startsAt()).append(":")
                        .append(a.endsAt()).append(";");
            }
        }

        if (frontierShifts != null) {
            List<PublishedShiftRecord> combinedFrontier = new ArrayList<>();
            combinedFrontier.addAll(frontierShifts.previousWeekShifts());
            combinedFrontier.addAll(frontierShifts.nextWeekShifts());
            combinedFrontier.sort(FRONTIER_SHIFT_COMPARATOR);
            for (PublishedShiftRecord fs : combinedFrontier) {
                sb.append("frontier:")
                        .append(fs.employeeId()).append(":")
                        .append(fs.startsAt()).append(":")
                        .append(fs.endsAt()).append(";");
            }
        }

        if (warnings != null && !warnings.isEmpty()) {
            List<ValidationWarning> sortedWarnings = new ArrayList<>(warnings);
            sortedWarnings.sort(WARNING_COMPARATOR);
            for (ValidationWarning w : sortedWarnings) {
                sb.append("warn:")
                        .append(w.warningFingerprintHex()).append(";");
            }
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM", e);
        }
    }
}
