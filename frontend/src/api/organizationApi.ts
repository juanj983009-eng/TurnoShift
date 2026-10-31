/**
 * TurnoShift — Organization Configuration API (HU-05)
 * Métodos para consulta y actualización de la organización y su sede activa.
 * Utiliza exclusivamente apiClient y los contratos OpenAPI 3.1.
 */

import { apiClient } from './client';
import { generateIdempotencyKey } from '../utils/idempotency';
import type {
  OrganizationConfigurationResponse,
  OrganizationView,
  ActiveSiteView,
  UpdateOrganizationDisplayNameRequest,
  UpdateActiveSiteNameRequest
} from '../types/organization';

export const organizationApi = {
  /**
   * Consulta la configuración general de la organización y su sede única activa.
   * GET /api/v1/organization/configuration
   * Accesible para ADMINISTRADOR y SUPERVISOR.
   */
  async getConfiguration(): Promise<OrganizationConfigurationResponse> {
    return apiClient<OrganizationConfigurationResponse>('/organization/configuration', {
      method: 'GET'
    });
  },

  /**
   * Actualiza el nombre visible de la organización del usuario autenticado.
   * PATCH /api/v1/organization/configuration/display-name
   * Requiere rol ADMINISTRADOR, CSRF e Idempotency-Key.
   */
  async updateDisplayName(
    request: UpdateOrganizationDisplayNameRequest,
    idempotencyKey: string = generateIdempotencyKey()
  ): Promise<OrganizationView> {
    return apiClient<OrganizationView>('/organization/configuration/display-name', {
      method: 'PATCH',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: request
    });
  },

  /**
   * Actualiza el nombre de la sede activa única de la organización.
   * PATCH /api/v1/organization/configuration/active-site/name
   * Requiere rol ADMINISTRADOR, CSRF e Idempotency-Key.
   */
  async updateActiveSiteName(
    request: UpdateActiveSiteNameRequest,
    idempotencyKey: string = generateIdempotencyKey()
  ): Promise<ActiveSiteView> {
    return apiClient<ActiveSiteView>('/organization/configuration/active-site/name', {
      method: 'PATCH',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: request
    });
  }
};

export const { getConfiguration, updateDisplayName, updateActiveSiteName } = organizationApi;
