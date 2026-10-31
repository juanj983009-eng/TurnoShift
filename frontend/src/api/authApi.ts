/**
 * TurnoShift — Auth API
 * Métodos de autenticación y sesión empresarial alineados con OpenAPI 3.1.
 * Utiliza exclusivamente apiClient y los tipos existentes.
 */

import { apiClient, getCsrfToken as getActiveCsrfToken, setCsrfToken } from './client';
import type { CompletePasswordSetupRequest, CsrfTokenResponse, LoginRequest, SessionResponse } from '../types/auth';

export const authApi = {
  /**
   * Obtiene un token CSRF activo (GET /api/v1/auth/csrf).
   * Funciona tanto para sesiones anónimas como autenticadas.
   */
  async getCsrfToken(): Promise<CsrfTokenResponse> {
    return apiClient<CsrfTokenResponse>('/auth/csrf', {
      method: 'GET'
    });
  },

  /**
   * Consulta la identidad y el contexto de la sesión en curso (GET /api/v1/auth/session).
   * Si no existe sesión activa, la API responde HTTP 401 SESSION_REQUIRED.
   */
  async getSessionState(): Promise<SessionResponse> {
    return apiClient<SessionResponse>('/auth/session', {
      method: 'GET'
    });
  },

  /**
   * Inicia sesión con las credenciales de la empresa (POST /api/v1/auth/login).
   * Requiere token CSRF en cabecera (inyectado automáticamente por apiClient si existe en memoria).
   */
  async login(request: LoginRequest): Promise<SessionResponse> {
    const organizationCode = (request.organizationCode ?? request.orgCode ?? '').trim();
    return apiClient<SessionResponse>('/auth/login', {
      method: 'POST',
      body: {
        organizationCode,
        email: request.email.trim(),
        password: request.password
      }
    });
  },

  /**
   * Invalida la sesión activa en el servidor y limpia la cookie (POST /api/v1/auth/logout).
   * Requiere token CSRF en cabecera.
   */
  async logout(): Promise<void> {
    return apiClient<void>('/auth/logout', {
      method: 'POST',
      body: {}
    });
  },

  /**
   * Establece la contraseña inicial del administrador mediante el token del enlace
   * y consume dicho enlace (POST /api/v1/auth/password-reset/complete).
   * Requiere token CSRF en cabecera (se solicita automáticamente si no está en memoria).
   */
  async completePasswordSetup(request: CompletePasswordSetupRequest): Promise<void> {
    if (!getActiveCsrfToken()) {
      const csrfResponse = await authApi.getCsrfToken();
      setCsrfToken(csrfResponse.token);
    }
    return apiClient<void>('/auth/password-reset/complete', {
      method: 'POST',
      body: {
        token: request.token,
        newPassword: request.newPassword
      }
    });
  }
};

export const { getCsrfToken, getSessionState, login, logout, completePasswordSetup } = authApi;
