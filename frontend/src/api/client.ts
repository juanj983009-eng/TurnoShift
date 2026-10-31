/**
 * TurnoShift — API Client
 * Cliente HTTP centralizado con soporte para CSRF en memoria, cookies de sesión
 * y parseo estructurado de respuestas RFC 9457 Problem Details.
 */

import { ApiProblemError, type ProblemDetails } from '../types/api';

const BASE_API_URL = '/api/v1';

/**
 * Token CSRF almacenado exclusivamente en memoria volátil de JavaScript.
 * Nunca se almacena en localStorage, sessionStorage ni cookies de script.
 */
let inMemoryCsrfToken: string | null = null;

/**
 * Registra o actualiza el token CSRF activo en memoria.
 */
export function setCsrfToken(token: string | null): void {
  inMemoryCsrfToken = token && token.trim().length > 0 ? token.trim() : null;
}

/**
 * Obtiene el token CSRF activo en memoria, si existe.
 */
export function getCsrfToken(): string | null {
  return inMemoryCsrfToken;
}

/**
 * Limpia el token CSRF activo en memoria.
 */
export function clearCsrfToken(): void {
  inMemoryCsrfToken = null;
}

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
}

/**
 * Ejecuta una petición HTTP a la API empresarial de TurnoShift.
 *
 * Invariantes de seguridad:
 * - credentials: 'include' en todas las peticiones (envío automático de TSESSIONID).
 * - Accept: application/json, application/problem+json.
 * - Inyección automática de cabecera X-CSRF-TOKEN en métodos de mutación (POST, PUT, PATCH, DELETE).
 * - Distinción estricta de errores de red vs. respuestas HTTP Problem Details.
 */
export async function apiClient<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();

  const url = endpoint.startsWith('http') || endpoint.startsWith('/api')
    ? endpoint
    : `${BASE_API_URL}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;

  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json, application/problem+json');

  const mutationMethods = ['POST', 'PUT', 'PATCH', 'DELETE'];
  if (mutationMethods.includes(method) && inMemoryCsrfToken) {
    headers.set('X-CSRF-TOKEN', inMemoryCsrfToken);
  }

  let body: BodyInit | null | undefined;
  if (options.body !== undefined && options.body !== null) {
    if (typeof options.body === 'string' || options.body instanceof FormData || options.body instanceof Blob) {
      body = options.body;
    } else {
      headers.set('Content-Type', 'application/json');
      body = JSON.stringify(options.body);
    }
  }

  let response: Response;
  try {
    response = await fetch(url, {
      ...options,
      method,
      headers,
      body,
      credentials: 'include'
    });
  } catch {
    throw ApiProblemError.createNetworkError();
  }

  if (!response.ok) {
    let problem: ProblemDetails;
    const contentType = response.headers.get('content-type') ?? '';

    if (contentType.includes('application/problem+json') || contentType.includes('application/json')) {
      try {
        const json = await response.json();
        problem = {
          type: typeof json.type === 'string' ? json.type : '/problems/http-error',
          title: typeof json.title === 'string' ? json.title : (response.statusText || 'Error en la solicitud'),
          status: typeof json.status === 'number' ? json.status : response.status,
          detail: typeof json.detail === 'string' ? json.detail : undefined,
          instance: typeof json.instance === 'string' ? json.instance : undefined,
          code: typeof json.code === 'string' ? json.code : undefined,
          requestId: typeof json.requestId === 'string' ? json.requestId : undefined,
          errors: Array.isArray(json.errors) ? json.errors : undefined
        };
      } catch {
        problem = {
          type: '/problems/http-error',
          title: response.statusText || 'Error en la solicitud',
          status: response.status,
          detail: 'No se pudo interpretar el cuerpo de error devuelto por el servidor.'
        };
      }
    } else {
      problem = {
        type: '/problems/http-error',
        title: response.statusText || 'Error en la solicitud',
        status: response.status,
        detail: 'El servidor devolvió una respuesta de error no estructurada.'
      };
    }

    throw new ApiProblemError(problem);
  }

  if (response.status === 204) {
    return undefined as unknown as T;
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    return (await response.json()) as T;
  }

  return (await response.text()) as unknown as T;
}
