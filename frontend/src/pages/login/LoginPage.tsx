import { useState, useRef, useEffect, type FormEvent } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useRouter } from '../../components/routing/Router';
import { ApiProblemError } from '../../types/api';
import './LoginPage.css';

/* ═══════════════════════════════════════════════════════════════════════════
   Icons — inline SVGs accesibles y lineales sin dependencias externas.
   ═══════════════════════════════════════════════════════════════════════════ */

function BuildingIcon({ className }: { className?: string }) {
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
      <rect x="4" y="2" width="16" height="20" rx="2" ry="2" />
      <path d="M9 22v-4h6v4" />
      <line x1="8" y1="6" x2="10" y2="6" />
      <line x1="14" y1="6" x2="16" y2="6" />
      <line x1="8" y1="10" x2="10" y2="10" />
      <line x1="14" y1="10" x2="16" y2="10" />
      <line x1="8" y1="14" x2="10" y2="14" />
      <line x1="14" y1="14" x2="16" y2="14" />
    </svg>
  );
}

function MailIcon({ className }: { className?: string }) {
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
      <rect width="20" height="16" x="2" y="4" rx="2" />
      <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
    </svg>
  );
}

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
      <path d="m2 2 20 20" />
    </svg>
  );
}

function AlertCircleIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      width="16"
      height="16"
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

function InfoIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="16" x2="12" y2="12" />
      <line x1="12" y1="8" x2="12.01" y2="8" />
    </svg>
  );
}

/* ═══════════════════════════════════════════════════════════════════════════
   LoginPage Component
   ═══════════════════════════════════════════════════════════════════════════ */

interface FieldErrors {
  orgCode?: string;
  email?: string;
  password?: string;
}

export default function LoginPage() {
  const { login, sessionExpired, clearSessionExpired } = useAuth();
  const { navigate } = useRouter();

  const [orgCode, setOrgCode] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [loading, setLoading] = useState(false);

  const orgCodeRef = useRef<HTMLInputElement>(null);

  // Auto-foco en el primer campo al terminar la animación de entrada
  useEffect(() => {
    const timer = setTimeout(() => orgCodeRef.current?.focus(), 320);
    return () => clearTimeout(timer);
  }, []);

  const canSubmit = orgCode.trim() !== '' && email.trim() !== '' && password.trim() !== '';

  const handleOrgChange = (val: string) => {
    setOrgCode(val);
    if (fieldErrors.orgCode) setFieldErrors((prev) => ({ ...prev, orgCode: undefined }));
    if (generalError) setGeneralError(null);
    if (sessionExpired) clearSessionExpired();
  };

  const handleEmailChange = (val: string) => {
    setEmail(val);
    if (fieldErrors.email) setFieldErrors((prev) => ({ ...prev, email: undefined }));
    if (generalError) setGeneralError(null);
    if (sessionExpired) clearSessionExpired();
  };

  const handlePasswordChange = (val: string) => {
    setPassword(val);
    if (fieldErrors.password) setFieldErrors((prev) => ({ ...prev, password: undefined }));
    if (generalError) setGeneralError(null);
    if (sessionExpired) clearSessionExpired();
  };

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!canSubmit || loading) return;

    setGeneralError(null);
    setFieldErrors({});
    setLoading(true);

    try {
      const sessionResponse = await login({
        orgCode: orgCode.trim(),
        email: email.trim(),
        password
      });

      // Navegación segura tras éxito: a la landingPage de sesión o fallback por rol
      const target =
        sessionResponse.landingPage ||
        (sessionResponse.user.role === 'COLABORADOR' ? '/app/mi-horario' : '/app/inicio');
      navigate(target, { replace: true });
    } catch (err) {
      if (err instanceof ApiProblemError) {
        if (err.status === 401 || err.code === 'AUTHENTICATION_FAILED') {
          setGeneralError('Credenciales no válidas. Revisa el código de organización, correo y contraseña.');
        } else if (err.status === 429 || err.code === 'RATE_LIMITED') {
          setGeneralError('Demasiados intentos de acceso fallidos. Por favor, espera unos minutos antes de volver a intentarlo.');
        } else if (err.status === 503 || err.code === 'SERVICE_UNAVAILABLE' || err.isNetworkError || err.status === 0) {
          setGeneralError('El servicio no está disponible en este momento. Por favor, reintenta en unos instantes.');
        } else if (err.status === 403) {
          if (err.code === 'ACCESS_DENIED') {
            setGeneralError('La organización no tiene acceso habilitado o se encuentra suspendida.');
          } else {
            setGeneralError('No fue posible validar la solicitud de seguridad. Por favor, intenta de nuevo.');
          }
        } else if (err.status === 400) {
          const errors: FieldErrors = {};
          for (const fe of err.fieldErrors) {
            if (fe.field === 'organizationCode' || fe.field === 'orgCode') {
              errors.orgCode = fe.message || 'Código de organización no válido.';
            } else if (fe.field === 'email') {
              errors.email = fe.message || 'Correo electrónico no válido.';
            } else if (fe.field === 'password') {
              errors.password = fe.message || 'Contraseña requerida.';
            }
          }
          setFieldErrors(errors);
          if (!errors.orgCode && !errors.email && !errors.password) {
            setGeneralError('Revisa que todos los campos requeridos estén completos y con formato válido.');
          }
        } else {
          setGeneralError('Ocurrió un error al procesar el inicio de sesión. Por favor, reintenta.');
        }
      } else {
        setGeneralError('El servicio no está disponible en este momento. Por favor, reintenta en unos instantes.');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page grid-pattern-bg">
      <div className="login-card">
        {/* ── Logo e Identidad TurnoShift ────────────────────────── */}
        <div className="login-logo">
          <div className="login-logo__glyph" aria-hidden="true">TS</div>
          <span className="login-logo__wordmark">TurnoShift</span>
        </div>
        <p className="login-subtitle">
          Plataforma operativa de gestión de turnos y personal
        </p>

        {/* ── Aviso amigable de sesión expirada ───────────────────── */}
        {sessionExpired && (
          <div className="login-notice" role="status">
            <InfoIcon className="login-notice__icon" />
            <span className="login-notice__message">
              Tu sesión ha expirado por inactividad. Por favor, inicia sesión nuevamente.
            </span>
          </div>
        )}

        {/* ── Banner de Error General ────────────────────────────── */}
        {generalError && (
          <div className="login-error" role="alert">
            <AlertCircleIcon className="login-error__icon" />
            <span className="login-error__message">{generalError}</span>
          </div>
        )}

        {/* ── Formulario de Acceso ───────────────────────────────── */}
        <form className="login-form" onSubmit={handleSubmit} noValidate>
          {/* Código de Organización */}
          <div className="login-field">
            <label className="login-field__label" htmlFor="login-org">
              Código de organización
            </label>
            <div className="login-field__input-wrapper">
              <span className="login-field__lead-icon" aria-hidden="true">
                <BuildingIcon />
              </span>
              <input
                ref={orgCodeRef}
                id="login-org"
                className={`login-field__input login-field__input--with-icon ${fieldErrors.orgCode ? 'login-field__input--error' : ''}`}
                type="text"
                value={orgCode}
                onChange={(e) => handleOrgChange(e.target.value)}
                placeholder="Ej. CAFE-DEMO"
                autoCapitalize="characters"
                autoComplete="organization"
                spellCheck={false}
                disabled={loading}
                aria-describedby={fieldErrors.orgCode ? 'login-org-error' : 'login-org-hint'}
                aria-invalid={!!fieldErrors.orgCode}
              />
            </div>
            {fieldErrors.orgCode ? (
              <span id="login-org-error" className="login-field__error-text" role="alert">
                {fieldErrors.orgCode}
              </span>
            ) : (
              <span id="login-org-hint" className="login-field__hint">
                Código alfanumérico proporcionado por el administrador de sede.
              </span>
            )}
          </div>

          {/* Correo Corporativo */}
          <div className="login-field">
            <label className="login-field__label" htmlFor="login-email">
              Correo corporativo
            </label>
            <div className="login-field__input-wrapper">
              <span className="login-field__lead-icon" aria-hidden="true">
                <MailIcon />
              </span>
              <input
                id="login-email"
                className={`login-field__input login-field__input--with-icon ${fieldErrors.email ? 'login-field__input--error' : ''}`}
                type="email"
                value={email}
                onChange={(e) => handleEmailChange(e.target.value)}
                placeholder="nombre@empresa.com"
                autoComplete="email"
                disabled={loading}
                aria-describedby={fieldErrors.email ? 'login-email-error' : undefined}
                aria-invalid={!!fieldErrors.email}
              />
            </div>
            {fieldErrors.email && (
              <span id="login-email-error" className="login-field__error-text" role="alert">
                {fieldErrors.email}
              </span>
            )}
          </div>

          {/* Contraseña */}
          <div className="login-field">
            <label className="login-field__label" htmlFor="login-password">
              Contraseña
            </label>
            <div className="login-field__input-wrapper">
              <span className="login-field__lead-icon" aria-hidden="true">
                <LockIcon />
              </span>
              <input
                id="login-password"
                className={`login-field__input login-field__input--with-icon login-field__input--password ${fieldErrors.password ? 'login-field__input--error' : ''}`}
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => handlePasswordChange(e.target.value)}
                placeholder="••••••••••••"
                autoComplete="current-password"
                disabled={loading}
                aria-describedby={fieldErrors.password ? 'login-password-error' : undefined}
                aria-invalid={!!fieldErrors.password}
              />
              <button
                type="button"
                className={`login-field__toggle ${showPassword ? 'login-field__toggle--active' : ''}`}
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
              >
                {showPassword ? <EyeOffIcon /> : <EyeIcon />}
              </button>
            </div>
            {fieldErrors.password && (
              <span id="login-password-error" className="login-field__error-text" role="alert">
                {fieldErrors.password}
              </span>
            )}
          </div>

          {/* Botón de Envío */}
          <button
            type="submit"
            className={`login-submit ${loading ? 'login-submit--loading' : ''}`}
            disabled={!canSubmit || loading}
          >
            {loading ? (
              <>
                <span className="spinner" aria-hidden="true" />
                <span className="login-submit__text">Ingresando...</span>
              </>
            ) : (
              'Ingresar a TurnoShift'
            )}
          </button>
        </form>
      </div>

      {/* ── Pie de Página Sobrio y Honesto ─────────────────────── */}
      <footer className="login-footer">
        <span>TurnoShift · Plataforma Operativa</span>
      </footer>
    </div>
  );
}
