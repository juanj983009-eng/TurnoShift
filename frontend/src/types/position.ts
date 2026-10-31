/**
 * TurnoShift — Types: Catálogo de Puestos de Trabajo (HU-06)
 * Contratos alineados estrictamente con OpenAPI 3.1 (v0.2.4-i2.4).
 */

/**
 * Representación completa de un puesto de trabajo en la organización.
 */
export interface PositionResponse {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly isActive: boolean;
  readonly lockVersion: number;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/**
 * Petición para registrar un nuevo puesto de trabajo en la organización.
 * code: 2..32 caracteres, mayúsculas alfanuméricas con guiones (^[A-Z0-9-]+$). Inmutable.
 * name: 1..100 caracteres.
 * POST /api/v1/positions
 */
export interface CreatePositionRequest {
  readonly code: string;
  readonly name: string;
}

/**
 * Petición para actualizar el nombre de un puesto de trabajo existente.
 * Nota: el código del puesto es inmutable y no se puede modificar.
 * PUT /api/v1/positions/{positionId}
 */
export interface UpdatePositionRequest {
  readonly name: string;
  readonly expectedLockVersion: number;
}

/**
 * Petición para cambiar el estado de activación de un puesto de trabajo.
 * PATCH /api/v1/positions/{positionId}/status
 */
export interface ChangePositionStatusRequest {
  readonly isActive: boolean;
  readonly expectedLockVersion: number;
}

/**
 * Filtro de estado para la consulta del catálogo de puestos en la interfaz.
 * - 'all': omite el parámetro active (devuelve activos e inactivos).
 * - 'active': active=true.
 * - 'inactive': active=false.
 */
export type PositionStatusFilter = 'all' | 'active' | 'inactive';
