/**
 * TurnoShift — Positions API (HU-06)
 * Métodos para consulta, alta, edición y conmutación de estado de puestos de trabajo.
 * Utiliza exclusivamente apiClient y los contratos OpenAPI 3.1.
 */

import { apiClient } from './client';
import { generateIdempotencyKey } from '../utils/idempotency';
import type {
  PositionResponse,
  CreatePositionRequest,
  UpdatePositionRequest,
  ChangePositionStatusRequest
} from '../types/position';

export const positionApi = {
  /**
   * Consulta el catálogo de puestos de trabajo de la organización.
   * GET /api/v1/positions(?active=true|false)
   * Accesible para ADMINISTRADOR y SUPERVISOR.
   */
  async getPositions(active?: boolean): Promise<PositionResponse[]> {
    let endpoint = '/positions';
    if (active !== undefined) {
      endpoint += `?active=${active}`;
    }
    return apiClient<PositionResponse[]>(endpoint, {
      method: 'GET'
    });
  },

  /**
   * Registra un nuevo puesto de trabajo en la organización.
   * POST /api/v1/positions
   * Requiere rol ADMINISTRADOR, CSRF e Idempotency-Key.
   */
  async createPosition(
    request: CreatePositionRequest,
    idempotencyKey: string = generateIdempotencyKey()
  ): Promise<PositionResponse> {
    return apiClient<PositionResponse>('/positions', {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: request
    });
  },

  /**
   * Actualiza el nombre descriptivo de un puesto de trabajo existente.
   * PUT /api/v1/positions/{positionId}
   * Requiere rol ADMINISTRADOR, CSRF e Idempotency-Key.
   */
  async updatePosition(
    positionId: string,
    request: UpdatePositionRequest,
    idempotencyKey: string = generateIdempotencyKey()
  ): Promise<PositionResponse> {
    return apiClient<PositionResponse>(`/positions/${encodeURIComponent(positionId)}`, {
      method: 'PUT',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: request
    });
  },

  /**
   * Cambia el estado de activación (activo/inactivo) de un puesto de trabajo.
   * PATCH /api/v1/positions/{positionId}/status
   * Requiere rol ADMINISTRADOR, CSRF e Idempotency-Key.
   */
  async changePositionStatus(
    positionId: string,
    request: ChangePositionStatusRequest,
    idempotencyKey: string = generateIdempotencyKey()
  ): Promise<PositionResponse> {
    return apiClient<PositionResponse>(`/positions/${encodeURIComponent(positionId)}/status`, {
      method: 'PATCH',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: request
    });
  }
};

export const {
  getPositions,
  createPosition,
  updatePosition,
  changePositionStatus
} = positionApi;
