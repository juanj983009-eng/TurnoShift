/**
 * TurnoShift — Types: Indisponibilidades de Colaboradores (HU-08 / HU-10)
 * Modelos alineados estrictamente con OpenAPI 3.1 (v0.2.4-i2.4).
 */

/**
 * Catálogo predefinido de motivos de indisponibilidad.
 */
export type UnavailabilityReason =
  | 'VACACIONES'
  | 'PERMISO_PERSONAL'
  | 'MEDICO'
  | 'FORMACION'
  | 'OTRO';

/**
 * Representación completa de un periodo de indisponibilidad de un colaborador.
 */
export interface EmployeeUnavailabilityResponse {
  readonly id: string;
  readonly employeeId: string;
  readonly startsAt: string;
  readonly endsAt: string;
  readonly reasonCode: UnavailabilityReason | null;
  readonly restrictedNote: string | null;
  readonly cancelled: boolean;
  readonly cancelledAt: string | null;
  readonly cancelledByUserId: string | null;
  readonly createdByUserId: string;
  readonly lockVersion: number;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/**
 * Contenedor de lista para respuestas de consulta de indisponibilidades.
 */
export interface UnavailabilityListResponse {
  readonly items: readonly EmployeeUnavailabilityResponse[];
}

/**
 * Solicitud de registro de una nueva indisponibilidad.
 * Admite exactamente uno de dos formatos mutuamente excluyentes:
 * - Parcial: startsAt y endsAt (instantes UTC, startsAt < endsAt).
 * - Día completo: allDay: true, startDate (inclusiva YYYY-MM-DD) y endDate (exclusiva YYYY-MM-DD).
 * POST /api/v1/employees/{employeeId}/unavailabilities
 */
export interface CreateUnavailabilityRequest {
  readonly startsAt?: string | null;
  readonly endsAt?: string | null;
  readonly allDay?: boolean | null;
  readonly startDate?: string | null;
  readonly endDate?: string | null;
  readonly reasonCode?: UnavailabilityReason | null;
  readonly restrictedNote?: string | null;
}

/**
 * Solicitud de modificación de una indisponibilidad existente.
 * Requiere expectedLockVersion para control de concurrencia optimista.
 * PUT /api/v1/unavailabilities/{unavailabilityId}
 */
export interface UpdateUnavailabilityRequest {
  readonly startsAt?: string | null;
  readonly endsAt?: string | null;
  readonly allDay?: boolean | null;
  readonly startDate?: string | null;
  readonly endDate?: string | null;
  readonly reasonCode?: UnavailabilityReason | null;
  readonly restrictedNote?: string | null;
  readonly expectedLockVersion: number;
}

/**
 * Solicitud para cancelar lógicamente una indisponibilidad activa.
 * Requiere expectedLockVersion para control de concurrencia optimista.
 * POST /api/v1/unavailabilities/{unavailabilityId}/cancel
 */
export interface CancelUnavailabilityRequest {
  readonly expectedLockVersion: number;
}

/**
 * Filtro de estado para la visualización en la interfaz.
 * - 'active': solo indisponibilidades vigentes (cancelled: false).
 * - 'all': todas las indisponibilidades de la ventana (activas y canceladas).
 */
export type UnavailabilityStatusFilter = 'active' | 'all';

/**
 * Códigos de error y conflicto RFC 9457 específicos de indisponibilidades.
 * Nota: los intervalos superpuestos están permitidos por diseño en la base de datos (RN-017);
 * la compatibilidad temporal contra turnos pertenece a I3; no existe UNAVAILABILITY_OVERLAP.
 */
export type UnavailabilityConflictCode =
  | 'CONCURRENT_MODIFICATION'
  | 'PAST_INTERVAL_MODIFICATION_PROHIBITED'
  | 'UNAVAILABILITY_ALREADY_CANCELLED'
  | 'UNAVAILABILITY_NOT_FOUND'
  | 'EMPLOYEE_NOT_FOUND'
  | 'IDEMPOTENCY_KEY_REUSED'
  | 'IDEMPOTENCY_KEY_EXPIRED'
  | 'VALIDATION_FAILED';

/**
 * Intención de mutación idempotente retenida en caso de error de red.
 * Garantiza reintento con la misma clave únicamente cuando la operación y el payload son idénticos.
 */
export type PendingUnavailabilityIntent =
  | {
      readonly operation: 'create';
      readonly key: string;
      readonly employeeId: string;
      readonly startsAt?: string | null;
      readonly endsAt?: string | null;
      readonly allDay?: boolean | null;
      readonly startDate?: string | null;
      readonly endDate?: string | null;
      readonly reasonCode?: UnavailabilityReason | null;
      readonly restrictedNote?: string | null;
    }
  | {
      readonly operation: 'update';
      readonly key: string;
      readonly unavailabilityId: string;
      readonly startsAt?: string | null;
      readonly endsAt?: string | null;
      readonly allDay?: boolean | null;
      readonly startDate?: string | null;
      readonly endDate?: string | null;
      readonly reasonCode?: UnavailabilityReason | null;
      readonly restrictedNote?: string | null;
      readonly expectedLockVersion: number;
    }
  | {
      readonly operation: 'cancel';
      readonly key: string;
      readonly unavailabilityId: string;
      readonly expectedLockVersion: number;
    };
