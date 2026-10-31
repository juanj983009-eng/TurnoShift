/**
 * TurnoShift — Employees API (HU-07)
 * Métodos para consulta, alta, edición, conmutación de estado y pre-evaluación de colaboradores.
 * Utiliza exclusivamente apiClient y los contratos OpenAPI 3.1.
 */

import { apiClient } from './client';
import { generateIdempotencyKey } from '../utils/idempotency';
import type {
  EmployeeResponse,
  CreateEmployeeRequest,
  UpdateEmployeeRequest,
  ChangeEmployeeStatusRequest,
  DeactivationPreviewResponse
} from '../types/employee';

export const employeeApi = {
  /**
   * Consulta el catálogo de colaboradores de la organización.
   * GET /api/v1/employees(?active=true|false)
   * Accesible para ADMINISTRADOR y SUPERVISOR.
   */
  async getEmployees(active?: boolean): Promise<EmployeeResponse[]> {
    let endpoint = '/employees';
    if (active !== undefined) {
      endpoint += `?active=${active}`;
    }
    return apiClient<EmployeeResponse[]>(endpoint, {
      method: 'GET'
    });
  },

  /**
   * Consulta el detalle de un colaborador por su identificador único.
   * GET /api/v1/employees/{employeeId}
   * Accesible para ADMINISTRADOR y SUPERVISOR.
   */
  async getEmployeeById(employeeId: string): Promise<EmployeeResponse> {
    return apiClient<EmployeeResponse>(`/employees/${encodeURIComponent(employeeId)}`, {
      method: 'GET'
    });
  },

  /**
   * Registra un nuevo colaborador dentro de la organización.
   * POST /api/v1/employees
   * Requiere rol ADMINISTRADOR, CSRF e Idempotency-Key.
   */
  async createEmployee(
    data: CreateEmployeeRequest,
    idempotencyKey: string = generateIdempotencyKey()
  ): Promise<EmployeeResponse> {
    return apiClient<EmployeeResponse>('/employees', {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: data
    });
  },

  /**
   * Modifica los datos mutables de un colaborador (displayName, positionId o ambos).
   * PATCH /api/v1/employees/{employeeId}
   * Requiere rol ADMINISTRADOR, CSRF, expectedLockVersion e Idempotency-Key.
   */
  async updateEmployee(
    employeeId: string,
    data: UpdateEmployeeRequest,
    idempotencyKey: string = generateIdempotencyKey()
  ): Promise<EmployeeResponse> {
    return apiClient<EmployeeResponse>(`/employees/${encodeURIComponent(employeeId)}`, {
      method: 'PATCH',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: data
    });
  },

  /**
   * Pasa al colaborador al estado activo (active: true).
   * POST /api/v1/employees/{employeeId}/activate
   * Requiere rol ADMINISTRADOR, CSRF, expectedLockVersion e Idempotency-Key.
   */
  async activateEmployee(
    employeeId: string,
    data: ChangeEmployeeStatusRequest,
    idempotencyKey: string = generateIdempotencyKey()
  ): Promise<EmployeeResponse> {
    return apiClient<EmployeeResponse>(`/employees/${encodeURIComponent(employeeId)}/activate`, {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: data
    });
  },

  /**
   * Pasa al colaborador al estado inactivo (active: false).
   * POST /api/v1/employees/{employeeId}/deactivate
   * Requiere rol ADMINISTRADOR, CSRF, expectedLockVersion e Idempotency-Key.
   */
  async deactivateEmployee(
    employeeId: string,
    data: ChangeEmployeeStatusRequest,
    idempotencyKey: string = generateIdempotencyKey()
  ): Promise<EmployeeResponse> {
    return apiClient<EmployeeResponse>(`/employees/${encodeURIComponent(employeeId)}/deactivate`, {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: data
    });
  },

  /**
   * Evalúa las precondiciones de desactivación de un colaborador sin mutar datos.
   * POST /api/v1/employees/{employeeId}/deactivation-preview
   * Requiere rol ADMINISTRADOR y CSRF. Sin Idempotency-Key ni cuerpo.
   */
  async previewDeactivation(employeeId: string): Promise<DeactivationPreviewResponse> {
    return apiClient<DeactivationPreviewResponse>(
      `/employees/${encodeURIComponent(employeeId)}/deactivation-preview`,
      {
        method: 'POST'
      }
    );
  }
};

export const {
  getEmployees,
  getEmployeeById,
  createEmployee,
  updateEmployee,
  activateEmployee,
  deactivateEmployee,
  previewDeactivation
} = employeeApi;
