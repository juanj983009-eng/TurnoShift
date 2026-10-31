/**
 * TurnoShift — Auth Types
 * Contratos de datos de sesión, autenticación y CSRF alineados con OpenAPI 3.1.
 */

export type UserRole = 'ADMINISTRADOR' | 'SUPERVISOR' | 'COLABORADOR';

export interface SessionUser {
  readonly id: string;
  readonly displayName: string;
  readonly role: UserRole;
  readonly employeeId: string | null;
}

export interface SessionOrganization {
  readonly id: string;
  readonly displayName: string;
  readonly timeZone: string;
}

export interface SessionResponse {
  readonly user: SessionUser;
  readonly organization: SessionOrganization;
  readonly landingPage: string;
}

export interface CsrfTokenResponse {
  readonly token: string;
}

export interface LoginRequest {
  readonly orgCode?: string;
  readonly organizationCode?: string;
  readonly email: string;
  readonly password: string;
}

export interface CompletePasswordSetupRequest {
  readonly token: string;
  readonly newPassword: string;
}
