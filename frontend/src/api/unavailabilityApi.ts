/**
 * TurnoShift — Unavailabilities API (HU-08 / HU-10)
 * Métodos para consulta organizacional y por colaborador, registro, modificación y cancelación de indisponibilidades.
 * Utiliza exclusivamente apiClient y los contratos OpenAPI 3.1.
 */

import { apiClient } from './client';
import type {
  EmployeeUnavailabilityResponse,
  UnavailabilityListResponse,
  CreateUnavailabilityRequest,
  UpdateUnavailabilityRequest,
  CancelUnavailabilityRequest
} from '../types/unavailability';

export const unavailabilityApi = {
  /**
   * Consulta las indisponibilidades de toda la organización dentro de una ventana temporal obligatoria.
   * GET /api/v1/unavailabilities?from={from}&to={to}&includeCancelled={includeCancelled}
   * Requiere rol ADMINISTRADOR o SUPERVISOR.
   * Los parámetros 'from' y 'to' son obligatorios (instantes ISO-8601 UTC) con rango <= 92 días.
   */
  async getOrganizationUnavailabilities(
    from: string,
    to: string,
    includeCancelled?: boolean
  ): Promise<UnavailabilityListResponse> {
    const params = new URLSearchParams({
      from,
      to
    });
    if (includeCancelled !== undefined) {
      params.set('includeCancelled', String(includeCancelled));
    }
    return apiClient<UnavailabilityListResponse>(`/unavailabilities?${params.toString()}`, {
      method: 'GET'
    });
  },

  /**
   * Consulta las indisponibilidades de un colaborador específico.
   * GET /api/v1/employees/{employeeId}/unavailabilities
   * Requiere rol ADMINISTRADOR o SUPERVISOR.
   * Parámetros opcionales: from, to, includeCancelled.
   */
  async getEmployeeUnavailabilities(
    employeeId: string,
    from?: string,
    to?: string,
    includeCancelled?: boolean
  ): Promise<UnavailabilityListResponse> {
    const params = new URLSearchParams();
    if (from !== undefined && from.trim() !== '') {
      params.set('from', from);
    }
    if (to !== undefined && to.trim() !== '') {
      params.set('to', to);
    }
    if (includeCancelled !== undefined) {
      params.set('includeCancelled', String(includeCancelled));
    }
    const query = params.toString();
    const endpoint = `/employees/${encodeURIComponent(employeeId)}/unavailabilities${query ? `?${query}` : ''}`;
    return apiClient<UnavailabilityListResponse>(endpoint, {
      method: 'GET'
    });
  },

  /**
   * Consulta el detalle de una indisponibilidad específica por su identificador único.
   * GET /api/v1/unavailabilities/{unavailabilityId}
   * Requiere rol ADMINISTRADOR o SUPERVISOR.
   */
  async getUnavailabilityById(unavailabilityId: string): Promise<EmployeeUnavailabilityResponse> {
    return apiClient<EmployeeUnavailabilityResponse>(
      `/unavailabilities/${encodeURIComponent(unavailabilityId)}`,
      {
        method: 'GET'
      }
    );
  },

  /**
   * Registra un nuevo periodo de indisponibilidad para un colaborador.
   * POST /api/v1/employees/{employeeId}/unavailabilities
   * Requiere rol ADMINISTRADOR o SUPERVISOR, CSRF y cabecera Idempotency-Key obligatoria.
   */
  async createUnavailability(
    employeeId: string,
    data: CreateUnavailabilityRequest,
    idempotencyKey: string
  ): Promise<EmployeeUnavailabilityResponse> {
    return apiClient<EmployeeUnavailabilityResponse>(
      `/employees/${encodeURIComponent(employeeId)}/unavailabilities`,
      {
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey
        },
        body: data
      }
    );
  },

  /**
   * Modifica el intervalo temporal, motivo o nota restringida de una indisponibilidad activa.
   * PUT /api/v1/unavailabilities/{unavailabilityId}
   * Requiere rol ADMINISTRADOR o SUPERVISOR, CSRF, expectedLockVersion y cabecera Idempotency-Key obligatoria.
   */
  async updateUnavailability(
    unavailabilityId: string,
    data: UpdateUnavailabilityRequest,
    idempotencyKey: string
  ): Promise<EmployeeUnavailabilityResponse> {
    return apiClient<EmployeeUnavailabilityResponse>(
      `/unavailabilities/${encodeURIComponent(unavailabilityId)}`,
      {
        method: 'PUT',
        headers: {
          'Idempotency-Key': idempotencyKey
        },
        body: data
      }
    );
  },

  /**
   * Cancela lógicamente una indisponibilidad activa.
   * POST /api/v1/unavailabilities/{unavailabilityId}/cancel
   * Requiere rol ADMINISTRADOR o SUPERVISOR, CSRF, expectedLockVersion y cabecera Idempotency-Key obligatoria.
   */
  async cancelUnavailability(
    unavailabilityId: string,
    data: CancelUnavailabilityRequest,
    idempotencyKey: string
  ): Promise<EmployeeUnavailabilityResponse> {
    return apiClient<EmployeeUnavailabilityResponse>(
      `/unavailabilities/${encodeURIComponent(unavailabilityId)}/cancel`,
      {
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey
        },
        body: data
      }
    );
  }
};

export const {
  getOrganizationUnavailabilities,
  getEmployeeUnavailabilities,
  getUnavailabilityById,
  createUnavailability,
  updateUnavailability,
  cancelUnavailability
} = unavailabilityApi;
