/**
 * TurnoShift — API Types
 * Modelos para respuestas de error estructuradas RFC 9457 Problem Details.
 */

export interface FieldError {
  readonly field: string;
  readonly code: string;
  readonly message: string;
}

export interface ProblemDetails {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly code?: string;
  readonly requestId?: string;
  readonly errors?: readonly FieldError[];
}

/**
 * Excepción controlada para errores de la API HTTP (RFC 9457) y fallos de conectividad.
 * Preserva status, código programático y detalles sin exponer información técnica sensible al usuario.
 */
export class ApiProblemError extends Error {
  public readonly status: number;
  public readonly problem: ProblemDetails;
  public readonly isNetworkError: boolean;

  constructor(problem: ProblemDetails, isNetworkError = false) {
    super(problem.detail || problem.title || 'Error en la solicitud');
    this.name = 'ApiProblemError';
    this.status = problem.status;
    this.problem = problem;
    this.isNetworkError = isNetworkError;
  }

  get code(): string | undefined {
    return this.problem.code;
  }

  get title(): string {
    return this.problem.title;
  }

  get detail(): string | undefined {
    return this.problem.detail;
  }

  get fieldErrors(): readonly FieldError[] {
    return this.problem.errors ?? [];
  }

  /**
   * Genera un error de red seguro y distinguible sin detalles de sockets ni URLs internas.
   */
  public static createNetworkError(): ApiProblemError {
    return new ApiProblemError(
      {
        type: '/problems/network-error',
        title: 'Error de conexión',
        status: 0,
        detail: 'No fue posible conectar con el servidor. Verifica tu conexión a internet o intenta más tarde.',
        code: 'NETWORK_ERROR'
      },
      true
    );
  }
}
