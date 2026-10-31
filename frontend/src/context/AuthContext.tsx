/**
 * TurnoShift — Auth Context
 * Gestión centralizada del estado de autenticación y sesión empresarial en memoria.
 *
 * Invariantes de seguridad:
 * - Ningún secreto, credencial, token CSRF ni dato de sesión se almacena en localStorage o sessionStorage.
 * - Toda la persistencia descansa exclusivamente en la cookie HTTP-only TSESSIONID administrada por el servidor.
 * - El token CSRF reside únicamente en la memoria volátil del módulo client.ts.
 */

import React, { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import { authApi } from '../api/authApi';
import { setCsrfToken, getCsrfToken, clearCsrfToken } from '../api/client';
import { ApiProblemError } from '../types/api';
import type { LoginRequest, SessionResponse } from '../types/auth';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

export interface AuthContextValue {
  readonly status: AuthStatus;
  readonly session: SessionResponse | null;
  readonly sessionExpired: boolean;
  login(credentials: LoginRequest): Promise<SessionResponse>;
  logout(): Promise<void>;
  clearSessionExpired(): void;
  recheckSession(): Promise<SessionResponse | null>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export interface AuthProviderProps {
  readonly children: React.ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [session, setSession] = useState<SessionResponse | null>(null);
  const [sessionExpired, setSessionExpired] = useState<boolean>(false);

  const isMountedRef = useRef<boolean>(true);
  const sessionRef = useRef<SessionResponse | null>(session);

  // Mantener sessionRef sincronizado para evaluar si había sesión previa ante 401
  useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  // Protección contra actualizaciones tras desmontar
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  /**
   * Bootstrap inicial al montar la aplicación:
   * 1. Obtener token CSRF inicial (establece la sesión anónima en el servidor).
   * 2. Consultar el estado de sesión activa (GET /api/v1/auth/session).
   * 3. Si responde 401 SESSION_REQUIRED, pasa a estado unauthenticated sin error visible.
   */
  useEffect(() => {
    async function bootstrap() {
      try {
        const csrfResponse = await authApi.getCsrfToken();
        setCsrfToken(csrfResponse.token);

        try {
          const currentSession = await authApi.getSessionState();
          if (isMountedRef.current) {
            setSession(currentSession);
            setStatus('authenticated');
            setSessionExpired(false);
          }
        } catch (err) {
          if (!isMountedRef.current) return;

          // 401 durante bootstrap significa ausencia de sesión activa
          if (err instanceof ApiProblemError && err.status === 401) {
            setSession(null);
            setStatus('unauthenticated');
            setSessionExpired(false);
          } else {
            // Error de red o indisponibilidad temporal
            setSession(null);
            setStatus('unauthenticated');
            setSessionExpired(false);
          }
        }
      } catch {
        if (!isMountedRef.current) return;
        setSession(null);
        setStatus('unauthenticated');
        setSessionExpired(false);
      }
    }

    bootstrap();
  }, []);

  /**
   * Inicio de sesión empresarial:
   * 1. Requiere token CSRF en memoria (lo solicita si estuviera ausente).
   * 2. Ejecuta POST /api/v1/auth/login.
   * 3. Tras éxito, refresca el token CSRF y consulta la sesión definitiva
   *    para sincronizar el contexto tras la rotación de sesión en el servidor.
   */
  const login = useCallback(async (credentials: LoginRequest): Promise<SessionResponse> => {
    if (!getCsrfToken()) {
      const csrfResponse = await authApi.getCsrfToken();
      setCsrfToken(csrfResponse.token);
    }

    try {
      await authApi.login(credentials);

      // Tras login exitoso, Spring Security rota la sesión (changeSessionId)
      // Recuperamos el nuevo token CSRF asociado a la sesión autenticada
      try {
        const newCsrf = await authApi.getCsrfToken();
        setCsrfToken(newCsrf.token);
      } catch {
        // Si falla la renovación inmediata, el siguiente fetch lo intentará
      }

      // Consultar la sesión activa autenticada
      const freshSession = await authApi.getSessionState();

      if (isMountedRef.current) {
        setSession(freshSession);
        setStatus('authenticated');
        setSessionExpired(false);
      }

      return freshSession;
    } catch (err) {
      // Si la solicitud devuelve 401 habiendo una sesión previa, limpiar y marcar expiración
      if (err instanceof ApiProblemError && err.status === 401 && sessionRef.current !== null) {
        if (isMountedRef.current) {
          setSession(null);
          setStatus('unauthenticated');
          setSessionExpired(true);
        }
      }
      throw err;
    }
  }, []);

  /**
   * Cierre de sesión empresarial:
   * 1. Invoca POST /api/v1/auth/logout con cabecera CSRF.
   * 2. Limpia sesión local tras éxito o respuesta 401 (sesión ya invalidada en servidor).
   * 3. Si hay error de red u otro error no-401, conserva la sesión local y devuelve un error seguro para permitir reintento.
   */
  const logout = useCallback(async (): Promise<void> => {
    let shouldClearLocal = false;
    try {
      await authApi.logout();
      shouldClearLocal = true;
    } catch (err) {
      if (err instanceof ApiProblemError && err.status === 401) {
        // La sesión ya había expirado o fue invalidada en el servidor
        shouldClearLocal = true;
      } else {
        // Error de red, 503 u otro error no-401: conservar sesión local y relanzar para permitir reintento
        throw err;
      }
    }

    if (shouldClearLocal) {
      if (isMountedRef.current) {
        setSession(null);
        setStatus('unauthenticated');
        setSessionExpired(false);
      }
      clearCsrfToken();

      // Solicitar nuevo token CSRF para sesión anónima
      try {
        const anonCsrf = await authApi.getCsrfToken();
        setCsrfToken(anonCsrf.token);
      } catch {
        // Fallo no bloqueante en background
      }
    }
  }, []);

  /**
   * Revalida la sesión activa en el servidor:
   * Si responde 401 y existía sesión previa, marca sesión expirada.
   */
  const recheckSession = useCallback(async (): Promise<SessionResponse | null> => {
    try {
      const currentSession = await authApi.getSessionState();
      if (isMountedRef.current) {
        setSession(currentSession);
        setStatus('authenticated');
        setSessionExpired(false);
      }
      return currentSession;
    } catch (err) {
      if (err instanceof ApiProblemError && err.status === 401) {
        const hadSession = sessionRef.current !== null;
        if (isMountedRef.current) {
          setSession(null);
          setStatus('unauthenticated');
          if (hadSession) {
            setSessionExpired(true);
          }
        }
        return null;
      }
      throw err;
    }
  }, []);

  /**
   * Limpia la bandera de notificación de sesión expirada (ej. al interactuar con el formulario de login).
   */
  const clearSessionExpired = useCallback(() => {
    if (isMountedRef.current) {
      setSessionExpired(false);
    }
  }, []);

  const value: AuthContextValue = {
    status,
    session,
    sessionExpired,
    login,
    logout,
    clearSessionExpired,
    recheckSession
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * Hook para consumir el contexto de autenticación en componentes React.
 */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth debe ser utilizado dentro de un AuthProvider');
  }
  return context;
}
