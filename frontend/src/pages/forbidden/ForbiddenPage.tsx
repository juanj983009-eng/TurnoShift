import { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useRouter } from '../../components/routing/Router';
import { IconShieldAlert, IconLogout, IconHome } from '../../components/common/Icons';
import './ForbiddenPage.css';

export default function ForbiddenPage() {
  const { session, logout } = useAuth();
  const { navigate } = useRouter();
  const [isLoggingOut, setIsLoggingOut] = useState<boolean>(false);

  const getRoleLabel = (r?: string): string => {
    switch (r) {
      case 'ADMINISTRADOR':
        return 'Administrador';
      case 'SUPERVISOR':
        return 'Supervisor';
      case 'COLABORADOR':
        return 'Colaborador';
      default:
        return r || 'No especificado';
    }
  };

  const handleGoHome = () => {
    if (!session) {
      navigate('/acceso');
      return;
    }

    if (session.landingPage) {
      navigate(session.landingPage);
      return;
    }

    if (session.user.role === 'COLABORADOR') {
      navigate('/app/mi-horario');
    } else {
      navigate('/app/inicio');
    }
  };

  const handleLogout = async () => {
    if (isLoggingOut) return;
    setIsLoggingOut(true);
    try {
      await logout();
      navigate('/acceso');
    } catch {
      setIsLoggingOut(false);
    }
  };

  return (
    <div className="forbidden-page">
      <div className="forbidden-card">
        <div className="forbidden-card__icon-wrapper" aria-hidden="true">
          <IconShieldAlert size={36} className="forbidden-card__icon" />
        </div>

        <h1 className="forbidden-card__title">Acceso Restringido</h1>
        
        <p className="forbidden-card__message">
          Tu cuenta no tiene privilegios para acceder a esta sección de la plataforma con el rol asignado.
        </p>

        {session && (
          <div className="forbidden-card__details">
            <div className="forbidden-card__detail-row">
              <span className="forbidden-card__detail-label">Colaborador:</span>
              <span className="forbidden-card__detail-value">{session.user.displayName}</span>
            </div>
            <div className="forbidden-card__detail-row">
              <span className="forbidden-card__detail-label">Rol actual:</span>
              <span className="forbidden-card__detail-value forbidden-card__detail-value--role">
                {getRoleLabel(session.user.role)}
              </span>
            </div>
            <div className="forbidden-card__detail-row">
              <span className="forbidden-card__detail-label">Organización:</span>
              <span className="forbidden-card__detail-value">{session.organization.displayName}</span>
            </div>
          </div>
        )}

        <div className="forbidden-card__actions">
          <button
            type="button"
            className="forbidden-btn forbidden-btn--primary"
            onClick={handleGoHome}
          >
            <IconHome size={16} />
            <span>Volver a mi espacio</span>
          </button>

          <button
            type="button"
            className="forbidden-btn forbidden-btn--secondary"
            onClick={handleLogout}
            disabled={isLoggingOut}
          >
            <IconLogout size={16} />
            <span>{isLoggingOut ? 'Cerrando sesión...' : 'Cerrar sesión'}</span>
          </button>
        </div>
      </div>
    </div>
  );
}
