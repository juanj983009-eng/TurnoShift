import { useAuth } from '../../context/AuthContext';
import { useRouter } from '../routing/Router';
import { IconBuilding, IconClock, IconMenu } from '../common/Icons';
import './AppHeader.css';

interface AppHeaderProps {
  readonly onToggleSidebar?: () => void;
}

export function AppHeader({ onToggleSidebar }: AppHeaderProps) {
  const { session } = useAuth();
  const { currentPath } = useRouter();

  if (!session) return null;

  const getSectionTitle = (path: string): string => {
    if (path.startsWith('/app/inicio')) return 'Inicio';
    if (path.startsWith('/app/configuracion')) return 'Configuración';
    if (path.startsWith('/app/puestos')) return 'Puestos';
    if (path.startsWith('/app/equipo')) return 'Equipo';
    if (path.startsWith('/app/indisponibilidades')) return 'Indisponibilidades';
    if (path.startsWith('/app/mi-horario')) return 'Mi Horario';
    if (path.startsWith('/acceso-restringido')) return 'Acceso Restringido';
    return 'Panel';
  };

  const getRoleLabel = (role: string): string => {
    switch (role) {
      case 'ADMINISTRADOR':
        return 'Administrador';
      case 'SUPERVISOR':
        return 'Supervisor';
      case 'COLABORADOR':
        return 'Colaborador';
      default:
        return role;
    }
  };

  return (
    <header className="app-header">
      <div className="app-header__left">
        <button
          type="button"
          className="app-header__mobile-toggle"
          onClick={onToggleSidebar}
          aria-label="Alternar menú de navegación"
        >
          <IconMenu size={20} />
        </button>

        <nav aria-label="Ruta de navegación" className="app-header__breadcrumbs">
          <span className="app-header__brand-symbol" aria-hidden="true"><i /><i /><i /><i /></span>
          <span className="app-header__breadcrumb-item app-header__breadcrumb-item--brand">TurnoShift</span>
          <span className="app-header__breadcrumb-sep" aria-hidden="true">›</span>
          <span className="app-header__breadcrumb-item">Operaciones</span>
          <span className="app-header__breadcrumb-sep" aria-hidden="true">›</span>
          <span className="app-header__breadcrumb-item app-header__breadcrumb-item--current" aria-current="page">
            {getSectionTitle(currentPath)}
          </span>
        </nav>
      </div>

      <div className="app-header__right">
        {/* Organización y Zona Horaria reales */}
        <div className="app-header__org-pill" title={`Organización: ${session.organization.displayName}`}>
          <IconBuilding size={14} className="app-header__pill-icon" />
          <span className="app-header__org-name">{session.organization.displayName}</span>
          <div className="app-header__tz-pill" title={`Zona horaria configurada: ${session.organization.timeZone}`}>
            <IconClock size={12} className="app-header__pill-icon" />
            <span>{session.organization.timeZone}</span>
          </div>
        </div>

        {/* Rol y Usuario */}
        <div className="app-header__user-block">
          <span className="app-header__avatar" aria-hidden="true">
            <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><circle cx="12" cy="8" r="3.5" /><path d="M5 21v-2a7 7 0 0 1 14 0v2" /></svg>
          </span>
          <div className="app-header__user-copy">
          <span className="app-header__user-name" title={session.user.displayName}>
            {session.user.displayName}
          </span>
          <span className="app-header__user-role">{getRoleLabel(session.user.role)}</span>
          </div>
        </div>
      </div>
    </header>
  );
}
