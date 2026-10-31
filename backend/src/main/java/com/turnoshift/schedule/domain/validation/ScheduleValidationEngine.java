package com.turnoshift.schedule.domain.validation;

import com.turnoshift.schedule.domain.DraftAssignment;
import com.turnoshift.schedule.domain.PublishedShiftRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Motor determinista y puro de validación de horarios semanales.
 * Evalúa políticas operativas, solapamientos, descansos continuos, disponibilidad y catálogo.
 */
public final class ScheduleValidationEngine {

    private ScheduleValidationEngine() {
    }

    public static ScheduleValidationResult validate(ValidationContext context, List<DraftAssignment> draftAssignments) {
        Objects.requireNonNull(context, "context no puede ser nulo");
        List<DraftAssignment> assignments = draftAssignments == null ? List.of() : List.copyOf(draftAssignments);

        List<ValidationBlockingError> blockingErrors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();
        List<EmployeeWeeklySummary> employeeSummaries = new ArrayList<>();

        // 1. Verificar Política Operativa
        if (context.currentPolicy() == null) {
            blockingErrors.add(ValidationBlockingError.of(
                    BlockingErrorCode.POLICY_NOT_CONFIGURED,
                    "No se ha configurado una versión de política operativa activa para la organización"
            ));
        }

        // 2. Verificar Turnos en el Pasado (RN-031)
        validatePastShifts(context, assignments, blockingErrors);

        // 3. Verificar Colaboradores y Puestos (RN-010)
        validateEmployeesAndPositions(context, assignments, blockingErrors);

        // 4. Verificar Indisponibilidades Activas (RN-019)
        validateUnavailabilities(context, assignments, blockingErrors);

        // 5. Verificar Solapamientos (RN-017) y Descanso Mínimo (RN-018)
        validateOverlapsAndRest(context, assignments, blockingErrors);

        // 6. Resúmenes Semanales y Advertencias de Exceso de Minutos (RN-023 / DEC-04)
        validateWeeklyHoursAndSummaries(context, assignments, warnings, employeeSummaries);

        // 7. Calcular Huella Global de Validación SHA-256
        UUID policyVersionId = context.currentPolicy() != null ? context.currentPolicy().policyVersionId() : null;
        byte[] validationFingerprint = ValidationFingerprintCalculator.calculate(
                context.organizationId(),
                context.scheduleId(),
                policyVersionId,
                context.draftLockVersion(),
                assignments,
                context.frontierShifts(),
                warnings
        );

        boolean canPublish = blockingErrors.isEmpty();
        boolean emptySchedule = assignments.isEmpty();

        return new ScheduleValidationResult(
                canPublish,
                emptySchedule,
                policyVersionId,
                Collections.unmodifiableList(blockingErrors),
                Collections.unmodifiableList(warnings),
                Collections.unmodifiableList(employeeSummaries),
                validationFingerprint
        );
    }

    private static void validatePastShifts(
            ValidationContext context,
            List<DraftAssignment> assignments,
            List<ValidationBlockingError> blockingErrors
    ) {
        Instant now = context.now();

        Map<UUID, PublishedShiftRecord> baseShiftsByAssignmentId = new HashMap<>();
        if (context.basePublication() != null) {
            for (PublishedShiftRecord record : context.basePublication().publishedShifts()) {
                baseShiftsByAssignmentId.put(record.assignmentId(), record);
            }
        }

        // A. Validar asignaciones en el borrador
        for (DraftAssignment da : assignments) {
            if (da.assignmentId() == null) {
                // Asignación nueva: no puede iniciar en el pasado
                if (!da.startsAt().isAfter(now)) {
                    blockingErrors.add(ValidationBlockingError.of(
                            BlockingErrorCode.PAST_SHIFT_MODIFICATION,
                            "No se puede programar un nuevo turno que comience en el pasado",
                            da.id(),
                            da.employeeId(),
                            da.positionId(),
                            Map.of("startsAt", da.startsAt().toString(), "now", now.toString())
                    ));
                }
            } else {
                // Asignación vinculada a publicación base previa
                PublishedShiftRecord baseRecord = baseShiftsByAssignmentId.get(da.assignmentId());
                if (baseRecord != null && !baseRecord.startsAt().isAfter(now)) {
                    // El turno base ya comenzó: solo se permite si permanece estrictamente idéntico
                    boolean identical = baseRecord.employeeId().equals(da.employeeId())
                            && baseRecord.positionId().equals(da.positionId())
                            && baseRecord.startsAt().equals(da.startsAt())
                            && baseRecord.endsAt().equals(da.endsAt());

                    if (!identical) {
                        blockingErrors.add(ValidationBlockingError.of(
                                BlockingErrorCode.PAST_SHIFT_MODIFICATION,
                                "No se puede modificar un turno publicado que ya ha comenzado",
                                da.id(),
                                da.employeeId(),
                                da.positionId(),
                                Map.of(
                                        "assignmentId", da.assignmentId().toString(),
                                        "publishedStartsAt", baseRecord.startsAt().toString(),
                                        "now", now.toString()
                                )
                        ));
                    }
                }
            }
        }

        // B. Validar turnos base eliminados que ya habían comenzado
        if (context.basePublication() != null) {
            Set<UUID> presentAssignmentIds = assignments.stream()
                    .map(DraftAssignment::assignmentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            for (PublishedShiftRecord baseRecord : context.basePublication().publishedShifts()) {
                if (!baseRecord.startsAt().isAfter(now) && !presentAssignmentIds.contains(baseRecord.assignmentId())) {
                    blockingErrors.add(ValidationBlockingError.of(
                            BlockingErrorCode.PAST_SHIFT_MODIFICATION,
                            "No se puede eliminar un turno publicado que ya ha comenzado",
                            null,
                            baseRecord.employeeId(),
                            baseRecord.positionId(),
                            Map.of(
                                    "assignmentId", baseRecord.assignmentId().toString(),
                                    "publishedStartsAt", baseRecord.startsAt().toString(),
                                    "now", now.toString()
                            )
                    ));
                }
            }
        }
    }

    private static void validateEmployeesAndPositions(
            ValidationContext context,
            List<DraftAssignment> assignments,
            List<ValidationBlockingError> blockingErrors
    ) {
        for (DraftAssignment da : assignments) {
            EmployeeSnapshot employee = context.employeeSnapshots().get(da.employeeId());
            if (employee == null || !employee.isActive()) {
                blockingErrors.add(ValidationBlockingError.of(
                        BlockingErrorCode.EMPLOYEE_INACTIVE,
                        "El colaborador asignado está inactivo o no existe",
                        da.id(),
                        da.employeeId(),
                        da.positionId(),
                        Map.of("employeeId", da.employeeId().toString())
                ));
            } else {
                PositionSnapshot position = context.positionSnapshots().get(da.positionId());
                if (position == null || !position.isActive()) {
                    blockingErrors.add(ValidationBlockingError.of(
                            BlockingErrorCode.POSITION_INACTIVE,
                            "El puesto asignado está inactivo o no existe",
                            da.id(),
                            da.employeeId(),
                            da.positionId(),
                            Map.of("positionId", da.positionId().toString())
                    ));
                } else if (!da.positionId().equals(employee.currentPositionId())) {
                    blockingErrors.add(ValidationBlockingError.of(
                            BlockingErrorCode.POSITION_MISMATCH,
                            "El puesto asignado no coincide con el puesto principal del colaborador",
                            da.id(),
                            da.employeeId(),
                            da.positionId(),
                            Map.of(
                                    "assignedPositionId", da.positionId().toString(),
                                    "employeePositionId", String.valueOf(employee.currentPositionId())
                            )
                    ));
                }
            }
        }
    }

    private static void validateUnavailabilities(
            ValidationContext context,
            List<DraftAssignment> assignments,
            List<ValidationBlockingError> blockingErrors
    ) {
        for (DraftAssignment da : assignments) {
            for (UnavailabilitySnapshot u : context.unavailabilities()) {
                if (u.isCancelled() || !u.employeeId().equals(da.employeeId())) {
                    continue;
                }
                // Intersección de intervalos semiabiertos: da.startsAt < u.endsAt && u.startsAt < da.endsAt
                if (da.startsAt().isBefore(u.endsAt()) && u.startsAt().isBefore(da.endsAt())) {
                    Instant conflictStart = da.startsAt().isAfter(u.startsAt()) ? da.startsAt() : u.startsAt();
                    Instant conflictEnd = da.endsAt().isBefore(u.endsAt()) ? da.endsAt() : u.endsAt();

                    blockingErrors.add(ValidationBlockingError.of(
                            BlockingErrorCode.EMPLOYEE_UNAVAILABLE,
                            "El colaborador presenta una indisponibilidad activa en el intervalo del turno",
                            da.id(),
                            da.employeeId(),
                            da.positionId(),
                            Map.of(
                                    "unavailabilityId", u.unavailabilityId().toString(),
                                    "conflictStartsAt", conflictStart.toString(),
                                    "conflictEndsAt", conflictEnd.toString()
                            )
                    ));
                }
            }
        }
    }

    private record EvaluatedShift(
            UUID draftAssignmentId,
            UUID employeeId,
            UUID positionId,
            Instant startsAt,
            Instant endsAt,
            boolean isDraft
    ) {
    }

    private static void validateOverlapsAndRest(
            ValidationContext context,
            List<DraftAssignment> assignments,
            List<ValidationBlockingError> blockingErrors
    ) {
        // Agrupar asignaciones del borrador por empleado
        Map<UUID, List<EvaluatedShift>> shiftsByEmployee = new HashMap<>();

        for (DraftAssignment da : assignments) {
            shiftsByEmployee.computeIfAbsent(da.employeeId(), k -> new ArrayList<>())
                    .add(new EvaluatedShift(da.id(), da.employeeId(), da.positionId(), da.startsAt(), da.endsAt(), true));
        }

        // Agregar turnos de frontera (W-1 y W+1) relevantes para cada empleado involucrado
        if (context.frontierShifts() != null) {
            for (PublishedShiftRecord fs : context.frontierShifts().previousWeekShifts()) {
                if (shiftsByEmployee.containsKey(fs.employeeId())) {
                    shiftsByEmployee.get(fs.employeeId())
                            .add(new EvaluatedShift(null, fs.employeeId(), fs.positionId(), fs.startsAt(), fs.endsAt(), false));
                }
            }
            for (PublishedShiftRecord fs : context.frontierShifts().nextWeekShifts()) {
                if (shiftsByEmployee.containsKey(fs.employeeId())) {
                    shiftsByEmployee.get(fs.employeeId())
                            .add(new EvaluatedShift(null, fs.employeeId(), fs.positionId(), fs.startsAt(), fs.endsAt(), false));
                }
            }
        }

        Integer minRestMinutes = context.currentPolicy() != null ? context.currentPolicy().minimumRestMinutes() : null;

        for (Map.Entry<UUID, List<EvaluatedShift>> entry : shiftsByEmployee.entrySet()) {
            UUID employeeId = entry.getKey();
            List<EvaluatedShift> shifts = entry.getValue();

            // A. Verificar Solapamientos (todas las parejas)
            for (int i = 0; i < shifts.size(); i++) {
                EvaluatedShift s1 = shifts.get(i);
                for (int j = i + 1; j < shifts.size(); j++) {
                    EvaluatedShift s2 = shifts.get(j);

                    // Si ninguno es borrador (ambos frontera), ignorar
                    if (!s1.isDraft() && !s2.isDraft()) {
                        continue;
                    }

                    // Intersección de intervalos semiabiertos
                    if (s1.startsAt().isBefore(s2.endsAt()) && s2.startsAt().isBefore(s1.endsAt())) {
                        EvaluatedShift draftShift = s1.isDraft() ? s1 : s2;
                        Instant conflictStart = s1.startsAt().isAfter(s2.startsAt()) ? s1.startsAt() : s2.startsAt();
                        Instant conflictEnd = s1.endsAt().isBefore(s2.endsAt()) ? s1.endsAt() : s2.endsAt();

                        blockingErrors.add(ValidationBlockingError.of(
                                BlockingErrorCode.SHIFT_OVERLAP,
                                "El colaborador tiene dos turnos que se solapan en el tiempo",
                                draftShift.draftAssignmentId(),
                                employeeId,
                                draftShift.positionId(),
                                Map.of(
                                        "conflictStartsAt", conflictStart.toString(),
                                        "conflictEndsAt", conflictEnd.toString()
                                )
                        ));
                    }
                }
            }

            // B. Verificar Descanso Mínimo Continuo (entre turnos consecutivos ordenados cronológicamente)
            if (minRestMinutes != null && minRestMinutes > 0) {
                List<EvaluatedShift> sortedShifts = new ArrayList<>(shifts);
                sortedShifts.sort(Comparator.comparing(EvaluatedShift::startsAt).thenComparing(EvaluatedShift::endsAt));

                for (int i = 0; i < sortedShifts.size() - 1; i++) {
                    EvaluatedShift prev = sortedShifts.get(i);
                    EvaluatedShift next = sortedShifts.get(i + 1);

                    // Si ninguno es borrador, ignorar
                    if (!prev.isDraft() && !next.isDraft()) {
                        continue;
                    }

                    // Si se solapan, el error SHIFT_OVERLAP ya fue registrado
                    if (prev.startsAt().isBefore(next.endsAt()) && next.startsAt().isBefore(prev.endsAt())) {
                        continue;
                    }

                    long restMinutes = Duration.between(prev.endsAt(), next.startsAt()).toMinutes();
                    if (restMinutes >= 0 && restMinutes < minRestMinutes) {
                        EvaluatedShift draftTarget = next.isDraft() ? next : prev;
                        blockingErrors.add(ValidationBlockingError.of(
                                BlockingErrorCode.MINIMUM_REST_VIOLATION,
                                String.format("El descanso entre turnos (%d min) no alcanza el mínimo continuo requerido (%d min)",
                                        restMinutes, minRestMinutes),
                                draftTarget.draftAssignmentId(),
                                employeeId,
                                draftTarget.positionId(),
                                Map.of(
                                        "restMinutes", restMinutes,
                                        "requiredRestMinutes", minRestMinutes,
                                        "precedingShiftEnd", prev.endsAt().toString(),
                                        "followingShiftStart", next.startsAt().toString()
                                )
                        ));
                    }
                }
            }
        }
    }

    private static void validateWeeklyHoursAndSummaries(
            ValidationContext context,
            List<DraftAssignment> assignments,
            List<ValidationWarning> warnings,
            List<EmployeeWeeklySummary> employeeSummaries
    ) {
        Integer thresholdMinutes = context.currentPolicy() != null ? context.currentPolicy().weeklyWarningMinutes() : null;

        // Agrupar minutos por colaborador ordenados de forma predecible
        Map<UUID, List<DraftAssignment>> byEmployee = new LinkedHashMap<>();
        for (DraftAssignment da : assignments) {
            byEmployee.computeIfAbsent(da.employeeId(), k -> new ArrayList<>()).add(da);
        }

        // Ordenar colaboradores alfabéticamente por nombre
        List<UUID> sortedEmployeeIds = new ArrayList<>(byEmployee.keySet());
        sortedEmployeeIds.sort((e1, e2) -> {
            String n1 = context.employeeSnapshots().containsKey(e1) ? context.employeeSnapshots().get(e1).displayName() : e1.toString();
            String n2 = context.employeeSnapshots().containsKey(e2) ? context.employeeSnapshots().get(e2).displayName() : e2.toString();
            return n1.compareToIgnoreCase(n2);
        });

        for (UUID employeeId : sortedEmployeeIds) {
            List<DraftAssignment> empAssignments = byEmployee.get(employeeId);
            int plannedMinutes = empAssignments.stream().mapToInt(DraftAssignment::durationMinutes).sum();
            String employeeName = context.employeeSnapshots().containsKey(employeeId)
                    ? context.employeeSnapshots().get(employeeId).displayName()
                    : "Colaborador " + employeeId;

            int effectiveThreshold = thresholdMinutes != null ? thresholdMinutes : Integer.MAX_VALUE;
            boolean exceeded = plannedMinutes > effectiveThreshold;

            employeeSummaries.add(new EmployeeWeeklySummary(
                    employeeId,
                    employeeName,
                    plannedMinutes,
                    effectiveThreshold,
                    exceeded
            ));

            if (exceeded && thresholdMinutes != null) {
                int excessMinutes = plannedMinutes - thresholdMinutes;
                byte[] warningFingerprint = WarningFingerprintCalculator.calculate(
                        context.organizationId(),
                        employeeId,
                        context.weekStart(),
                        context.weekStart().plusDays(7),
                        plannedMinutes,
                        thresholdMinutes
                );

                warnings.add(new ValidationWarning(
                        WarningCode.WEEKLY_PLANNED_MINUTES_EXCEEDED,
                        employeeId,
                        employeeName,
                        plannedMinutes,
                        thresholdMinutes,
                        excessMinutes,
                        warningFingerprint
                ));
            }
        }
    }
}
