/**
 * TurnoShift — Types: Directorio y Gestión de Colaboradores (HU-07)
 * Contratos alineados estrictamente con OpenAPI 3.1 (v0.2.4-i2.4).
 */

/**
 * Representación completa de un colaborador en la organización.
 * No contiene credenciales de cuenta, datos de autenticación ni linkedUserId.
 */
export interface EmployeeResponse {
  readonly id: string;
  readonly code: string;
  readonly displayName: string;
  readonly positionId: string;
  readonly siteId: string;
  readonly active: boolean;
  readonly lockVersion: number;
}

/**
 * Datos requeridos para registrar un nuevo colaborador en la organización.
 * - code: 2..32 caracteres, alfanumérico en mayúsculas con guiones (^[A-Z0-9-]+$). Inmutable.
 * - displayName: 1..100 caracteres.
 * - positionId: UUID de un puesto existente y activo en la organización.
 * POST /api/v1/employees
 */
export interface CreateEmployeeRequest {
  readonly code: string;
  readonly displayName: string;
  readonly positionId: string;
}

/**
 * Datos para actualizar los campos mutables de un colaborador.
 * Se debe especificar al menos displayName o positionId.
 * code y siteId son inmutables tras la creación.
 * PATCH /api/v1/employees/{employeeId}
 */
export interface UpdateEmployeeRequest {
  readonly displayName?: string;
  readonly positionId?: string;
  readonly expectedLockVersion: number;
}

/**
 * Solicitud para cambiar el estado de activación de un colaborador.
 * POST /api/v1/employees/{employeeId}/activate
 * POST /api/v1/employees/{employeeId}/deactivate
 */
export interface ChangeEmployeeStatusRequest {
  readonly expectedLockVersion: number;
}

/**
 * Resultado de la evaluación previa de precondiciones para desactivar a un colaborador.
 * POST /api/v1/employees/{employeeId}/deactivation-preview
 */
export interface DeactivationPreviewResponse {
  readonly employeeId: string;
  readonly displayName: string;
  readonly canDeactivate: boolean;
  readonly blockers: readonly string[];
}

/**
 * Filtro de estado para la consulta del catálogo de colaboradores en la interfaz.
 * - 'all': omite el parámetro active (devuelve activos e inactivos).
 * - 'active': active=true.
 * - 'inactive': active=false.
 */
export type EmployeeStatusFilter = 'all' | 'active' | 'inactive';

/**
 * Códigos de error y conflicto RFC 9457 específicos del módulo de colaboradores.
 */
export type EmployeeConflictCode =
  | 'EMPLOYEE_CODE_ALREADY_EXISTS'
  | 'POSITION_NOT_FOUND'
  | 'POSITION_INACTIVE'
  | 'NO_ACTIVE_SITE'
  | 'EMPLOYEE_LICENSE_LIMIT_REACHED'
  | 'CONCURRENT_MODIFICATION'
  | 'EMPLOYEE_NOT_FOUND'
  | 'IDEMPOTENCY_KEY_REUSED'
  | 'IDEMPOTENCY_KEY_EXPIRED';

/**
 * Representación de una intención de mutación idempotente de colaborador retenida ante errores de red.
 */
export type PendingEmployeeIntent =
  | {
      readonly operation: 'create';
      readonly key: string;
      readonly code: string;
      readonly displayName: string;
      readonly positionId: string;
    }
  | {
      readonly operation: 'edit';
      readonly key: string;
      readonly employeeId: string;
      readonly displayName?: string;
      readonly positionId?: string;
      readonly expectedLockVersion: number;
    }
  | {
      readonly operation: 'activate';
      readonly key: string;
      readonly employeeId: string;
      readonly expectedLockVersion: number;
    }
  | {
      readonly operation: 'deactivate';
      readonly key: string;
      readonly employeeId: string;
      readonly expectedLockVersion: number;
    };

