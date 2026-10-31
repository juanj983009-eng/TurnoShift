package com.turnoshift.schedule.domain.ports;

import com.turnoshift.schedule.domain.PublishedShiftRecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Puerto para consultar compromisos de planificación vigentes (publicaciones oficiales)
 * desde otros módulos del sistema (I2.2 colaboradores, I2.4 indisponibilidades).
 */
public interface PublishedAssignmentCommitmentQueryPort {

    /**
     * Verifica si existe un turno publicado vigente del colaborador que finalice en el futuro (ends_at > from).
     *
     * @param organizationId identificador de la organización
     * @param employeeId     identificador del colaborador
     * @param from           instante de referencia a partir del cual se evalúa la vigencia futura
     * @return {@code true} si existe al menos un turno publicado vigente con ends_at > from
     */
    boolean hasFuturePublishedAssignmentsForEmployee(UUID organizationId, UUID employeeId, Instant from);

    /**
     * Verifica si existe un turno publicado vigente futuro del colaborador con un puesto específico (starts_at > from).
     * Utilizado para bloquear cambio de puesto según RN-010 / RN-011.
     *
     * @param organizationId identificador de la organización
     * @param employeeId     identificador del colaborador
     * @param positionId     identificador del puesto (generalmente el puesto anterior a modificar)
     * @param from           instante de referencia a partir del cual se evalúa que el turno sea futuro
     * @return {@code true} si existe al menos un turno publicado vigente con el puesto y starts_at > from
     */
    boolean hasFuturePublishedAssignmentsForEmployeeAndPosition(
            UUID organizationId, UUID employeeId, UUID positionId, Instant from);

    /**
     * Devuelve los turnos publicados vigentes del colaborador que se solapen usando intervalo semiabierto
     * con {@code [startsAt, endsAt)}, finalicen en el futuro y pertenezcan a la organización.
     *
     * @param organizationId identificador de la organización
     * @param employeeId     identificador del colaborador
     * @param startsAt       inicio del intervalo a evaluar (inclusivo)
     * @param endsAt         fin del intervalo a evaluar (exclusivo)
     * @return lista de turnos publicados vigentes ordenados cronológicamente por starts_at
     */
    List<PublishedShiftRecord> findFuturePublishedAssignmentsOverlapping(
            UUID organizationId, UUID employeeId, Instant startsAt, Instant endsAt);
}
