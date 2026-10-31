import { useState, useRef, useEffect, type FormEvent } from 'react';
import { useRouter } from '../../components/routing/Router';
import { authApi } from '../../api/authApi';
import { ApiProblemError } from '../../types/api';
import './SetupPage.css';

/* ═══════════════════════════════════════════════════════════════════════════
   Icons — SVGs accesibles y lineales sin dependencias externas
   ═══════════════════════════════════════════════════════════════════════════ */

function LockIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  );
}

function EyeIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

function EyeOffIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49" />
      <path d="M14.084 14.158a3 3 0 0 1-4.242-4.242" />
      <path d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143" />
      <line x1="2" y1="2" x2="22" y2="22" />
    </svg>
  );
}

function AlertCircleIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="8" x2="12" y2="12" />
      <line x1="12" y1="16" x2="12.01" y2="16" />
    </svg>
  );
}

function CheckCircleIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  );
}

/* ═══════════════════════════════════════════════════════════════════════════
   SetupPage Component
   ═══════════════════════════════════════════════════════════════════════════ */

interface FieldErrors {
  newPassword?: string;
  confirmPassword?: string;
}

export default function SetupPage() {
  const { navigate } = useRouter();

  // El token se almacena exclusivamente en memoria volátil en un ref
  // Nunca se persiste en localStorage, sessionStorage, cookies ni se imprime
  const tokenRef = useRef<string | null>(null);

  const [hasToken, setHasToken] = useState<boolean | null>(null);
  const [invalidLink, setInvalidLink] = useState(false);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPasswords, setShowPasswords] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const passwordInputRef = useRef<HTMLInputElement>(null);

  // Extracción segura del token exclusivamente desde window.location.hash
  useEffect(() => {
    const hash = window.location.hash;
    let extractedToken: string | null = null;

    if (hash && hash.length > 1) {
      const raw = hash.startsWith('#') ? hash.substring(1) : hash;
      const params = new URLSearchParams(raw);
      const val = params.get('token');
      if (val && val.trim().length > 0) {
        extractedToken = val.trim();
      }
    }

    let timer: ReturnType<typeof setTimeout> | undefined;

    if (extractedToken) {
      tokenRef.current = extractedToken;
      setHasToken(true);
      // Foco accesible automático en el primer campo
      timer = setTimeout(() => passwordInputRef.current?.focus(), 320);
    } else {
      setHasToken(false);
    }

    return () => {
      if (timer) clearTimeout(timer);
    };
  }, []);

  const handlePasswordChange = (val: string) => {
    setNewPassword(val);
    if (fieldErrors.newPassword) setFieldErrors((prev) => ({ ...prev, newPassword: undefined }));
    if (generalError) setGeneralError(null);
  };

  const handleConfirmChange = (val: string) => {
    setConfirmPassword(val);
    if (fieldErrors.confirmPassword) setFieldErrors((prev) => ({ ...prev, confirmPassword: undefined }));
    if (generalError) setGeneralError(null);
  };

  const canSubmit = newPassword.trim() !== '' && confirmPassword.trim() !== '';

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!canSubmit || loading || success) return;

    const token = tokenRef.current;
    if (!token) {
      setInvalidLink(true);
      return;
    }

    // Validación local: campos requeridos y coincidencia
    const errors: FieldErrors = {};
    if (!newPassword.trim()) {
      errors.newPassword = 'La contraseña es obligatoria.';
    }
    if (!confirmPassword.trim()) {
      errors.confirmPassword = 'Debes confirmar la contraseña.';
    } else if (newPassword !== confirmPassword) {
      errors.confirmPassword = 'Las contraseñas no coinciden.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setGeneralError(null);
    setFieldErrors({});
    setLoading(true);

    try {
      await authApi.completePasswordSetup({
        token,
        newPassword
      });

      // Éxito: borrar inmediatamente el token del hash en la URL del navegador
      window.history.replaceState(null, '', window.location.pathname);
      tokenRef.current = null;
      setSuccess(true);

      // Redirigir de forma controlada a /acceso tras 2.5 segundos
      setTimeout(() => {
        navigate('/acceso', { replace: true });
      }, 2500);
    } catch (err) {
      if (err instanceof ApiProblemError) {
        if (err.status === 422 || err.code === 'RESET_LINK_INVALID') {
          // Token inválido, vencido, consumido o revocado: mensaje genérico sin filtrar estado interno
          setInvalidLink(true);
          window.history.replaceState(null, '', window.location.pathname);
          tokenRef.current = null;
        } else if (err.status === 400) {
          // Error de validación del backend (ej. longitud mínima, lista de bloqueo HIBP)
          const serverErrors: FieldErrors = {};
          for (const fe of err.fieldErrors) {
            if (fe.field === 'newPassword') {
              serverErrors.newPassword = fe.message || 'La contraseña no cumple los requisitos.';
            } else if (fe.field === 'token') {
              setInvalidLink(true);
              return;
            }
          }
          setFieldErrors(serverErrors);
          if (!serverErrors.newPassword) {
            setGeneralError(err.detail || 'La contraseña no cumple los requisitos de seguridad.');
          }
        } else if (err.status === 429 || err.code === 'RATE_LIMITED') {
          setGeneralError('Demasiados intentos. Por favor, espera unos minutos antes de volver a intentarlo.');
        } else if (err.status === 503 || err.code === 'SERVICE_UNAVAILABLE' || err.isNetworkError || err.status === 0) {
          setGeneralError('El servicio no está disponible en este momento. Por favor, reintenta en unos instantes.');
        } else if (err.status === 403) {
          setGeneralError('No fue posible validar la solicitud de seguridad. Por favor, recarga la página e intenta de nuevo.');
        } else {
          setGeneralError('Ocurrió un error al establecer la contraseña. Por favor, reintenta.');
        }
      } else {
        setGeneralError('El servicio no está disponible en este momento. Por favor, reintenta en unos instantes.');
      }
    } finally {
      setLoading(false);
    }
  }

  // 1. Estado de enlace inválido o token ausente en hash
  if (hasToken === false || invalidLink) {
    return (
      <div className="setup-page grid-pattern-bg">
        <div className="setup-card">
          <div className="setup-logo">
            <div className="setup-logo__glyph" aria-hidden="true">TS</div>
            <span className="setup-logo__wordmark">TurnoShift</span>
          </div>

          <div className="setup-state-card" role="alert">
            <div className="setup-state-icon setup-state-icon--warning">
              <AlertCircleIcon />
            </div>
            <h1 className="setup-state-title">Enlace no disponible</h1>
            <p className="setup-state-description">
              Este enlace de configuración no es válido, ha caducado o ya fue utilizado.
              Por motivos de seguridad, los enlaces son de un solo uso y tienen una vigencia limitada.
              Por favor, contacta al operador de la plataforma para solicitar un nuevo enlace de acceso.
            </p>
            <button
              type="button"
              className="setup-submit setup-submit--secondary"
              onClick={() => navigate('/acceso', { replace: true })}
            >
              Ir a la pantalla de acceso
            </button>
          </div>
        </div>
        <footer className="setup-footer">
          <span>TurnoShift · Plataforma Operativa</span>
        </footer>
      </div>
    );
  }

  // 2. Estado de éxito
  if (success) {
    return (
      <div className="setup-page grid-pattern-bg">
        <div className="setup-card">
          <div className="setup-logo">
            <div className="setup-logo__glyph" aria-hidden="true">TS</div>
            <span className="setup-logo__wordmark">TurnoShift</span>
          </div>

          <div className="setup-state-card" role="status">
            <div className="setup-state-icon setup-state-icon--success">
              <CheckCircleIcon />
            </div>
            <h1 className="setup-state-title">Contraseña configurada con éxito</h1>
            <p className="setup-state-description">
              Tu contraseña inicial ha sido establecida correctamente. Ya puedes iniciar sesión con tus credenciales
              y comenzar a configurar tu organización.
            </p>
            <p className="setup-state-redirect">
              Redirigiendo a la pantalla de acceso...
            </p>
            <button
              type="button"
              className="setup-submit"
              onClick={() => navigate('/acceso', { replace: true })}
            >
              Iniciar sesión ahora
            </button>
          </div>
        </div>
        <footer className="setup-footer">
          <span>TurnoShift · Plataforma Operativa</span>
        </footer>
      </div>
    );
  }

  // 3. Formulario principal para definir contraseña
  return (
    <div className="setup-page grid-pattern-bg">
      <div className="setup-card">
        <div className="setup-logo">
          <div className="setup-logo__glyph" aria-hidden="true">TS</div>
          <span className="setup-logo__wordmark">TurnoShift</span>
        </div>

        <h1 className="setup-title">Establecer contraseña</h1>
        <p className="setup-subtitle">
          Define tu contraseña inicial para activar tu cuenta de administrador en TurnoShift.
        </p>

        {generalError && (
          <div className="setup-error" role="alert">
            <AlertCircleIcon className="setup-error__icon" />
            <span className="setup-error__message">{generalError}</span>
          </div>
        )}

        <form className="setup-form" onSubmit={handleSubmit} noValidate>
          {/* Nueva Contraseña */}
          <div className="setup-field">
            <label className="setup-field__label" htmlFor="setup-new-password">
              Nueva contraseña
            </label>
            <div className="setup-field__input-wrapper">
              <span className="setup-field__lead-icon" aria-hidden="true">
                <LockIcon />
              </span>
              <input
                ref={passwordInputRef}
                id="setup-new-password"
                className={`setup-field__input setup-field__input--password ${fieldErrors.newPassword ? 'setup-field__input--error' : ''}`}
                type={showPasswords ? 'text' : 'password'}
                value={newPassword}
                onChange={(e) => handlePasswordChange(e.target.value)}
                placeholder="••••••••••••"
                autoComplete="new-password"
                disabled={loading}
                aria-describedby={fieldErrors.newPassword ? 'setup-new-password-error' : 'setup-new-password-hint'}
                aria-invalid={!!fieldErrors.newPassword}
              />
              <button
                type="button"
                className={`setup-field__toggle ${showPasswords ? 'setup-field__toggle--active' : ''}`}
                onClick={() => setShowPasswords(!showPasswords)}
                aria-label={showPasswords ? 'Ocultar contraseñas' : 'Mostrar contraseñas'}
              >
                {showPasswords ? <EyeOffIcon /> : <EyeIcon />}
              </button>
            </div>
            {fieldErrors.newPassword ? (
              <span id="setup-new-password-error" className="setup-field__error-text" role="alert">
                {fieldErrors.newPassword}
              </span>
            ) : (
              <span id="setup-new-password-hint" className="setup-field__hint">
                Mínimo 15 caracteres. Elige una contraseña segura y no utilizada en otros sitios.
              </span>
            )}
          </div>

          {/* Confirmar Contraseña */}
          <div className="setup-field">
            <label className="setup-field__label" htmlFor="setup-confirm-password">
              Confirmar contraseña
            </label>
            <div className="setup-field__input-wrapper">
              <span className="setup-field__lead-icon" aria-hidden="true">
                <LockIcon />
              </span>
              <input
                id="setup-confirm-password"
                className={`setup-field__input setup-field__input--password ${fieldErrors.confirmPassword ? 'setup-field__input--error' : ''}`}
                type={showPasswords ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => handleConfirmChange(e.target.value)}
                placeholder="••••••••••••"
                autoComplete="new-password"
                disabled={loading}
                aria-describedby={fieldErrors.confirmPassword ? 'setup-confirm-error' : undefined}
                aria-invalid={!!fieldErrors.confirmPassword}
              />
              <button
                type="button"
                className={`setup-field__toggle ${showPasswords ? 'setup-field__toggle--active' : ''}`}
                onClick={() => setShowPasswords(!showPasswords)}
                aria-label={showPasswords ? 'Ocultar contraseñas' : 'Mostrar contraseñas'}
              >
                {showPasswords ? <EyeOffIcon /> : <EyeIcon />}
              </button>
            </div>
            {fieldErrors.confirmPassword && (
              <span id="setup-confirm-error" className="setup-field__error-text" role="alert">
                {fieldErrors.confirmPassword}
              </span>
            )}
          </div>

          {/* Botón de Envío */}
          <button
            type="submit"
            className="setup-submit"
            disabled={!canSubmit || loading}
          >
            {loading ? (
              <>
                <span className="setup-spinner" aria-hidden="true" />
                <span>Estableciendo contraseña...</span>
              </>
            ) : (
              'Establecer contraseña y activar cuenta'
            )}
          </button>
        </form>
      </div>

      <footer className="setup-footer">
        <span>TurnoShift · Plataforma Operativa</span>
      </footer>
    </div>
  );
}
