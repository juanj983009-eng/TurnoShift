import { useState, type ReactNode } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useRouter, Link } from '../routing/Router';
import {
  IconHome,
  IconUsers,
  IconSettings,
  IconBriefcase,
  IconCalendar,
  IconLogout,
  IconClose
} from '../common/Icons';
import './AppSidebar.css';

interface AppSidebarProps {
  readonly isOpen?: boolean;
  readonly onClose?: () => void;
}

export function AppSidebar({ isOpen = false, onClose }: AppSidebarProps) {
  const { session, logout } = useAuth();
  const { currentPath, navigate } = useRouter();
  const [isLoggingOut, setIsLoggingOut] = useState<boolean>(false);
  const [logoutError, setLogoutError] = useState<string | null>(null);

  if (!session) return null;

  const { role } = session.user;

  const handleLogout = async () => {
    if (isLoggingOut) return;
    setLogoutError(null);
    setIsLoggingOut(true);

    try {
      await logout();
      navigate('/acceso');
    } catch {
      setLogoutError('Error al cerrar sesión. Reintente.');
      setIsLoggingOut(false);
    }
  };

  // Definir elementos de menú según el rol estricto del usuario
  interface NavItem {
    to: string;
    label: string;
    icon: ReactNode;
    tag?: string;
  }

  const navItems: NavItem[] = [];

  if (role === 'ADMINISTRADOR') {
    navItems.push(
      { to: '/app/inicio', label: 'Inicio', icon: <IconHome size={18} /> },
      { to: '/app/configuracion', label: 'Configuración', icon: <IconSettings size={18} /> },
      { to: '/app/puestos', label: 'Puestos', icon: <IconBriefcase size={18} /> },
      { to: '/app/equipo', label: 'Equipo', icon: <IconUsers size={18} /> },
      { to: '/app/indisponibilidades', label: 'Indisponibilidades', icon: <IconCalendar size={18} /> }
    );
  } else if (role === 'SUPERVISOR') {
    navItems.push(
      { to: '/app/inicio', label: 'Inicio', icon: <IconHome size={18} /> },
      { to: '/app/puestos', label: 'Puestos', icon: <IconBriefcase size={18} />, tag: 'Consulta' },
      { to: '/app/equipo', label: 'Equipo', icon: <IconUsers size={18} />, tag: 'Consulta' },
      { to: '/app/indisponibilidades', label: 'Indisponibilidades', icon: <IconCalendar size={18} /> }
    );
  } else if (role === 'COLABORADOR') {
    navItems.push(
      { to: '/app/mi-horario', label: 'Mi horario', icon: <IconCalendar size={18} /> }
    );
  }

  const isItemActive = (path: string): boolean => {
    return currentPath === path || currentPath.startsWith(path + '/');
  };

  return (
    <aside className={`app-sidebar ${isOpen ? 'app-sidebar--open' : ''}`}>
      {/* Cabecera / Marca */}
      <div className="app-sidebar__header">
        <div className="app-sidebar__brand">
          <span className="app-sidebar__glyph" aria-hidden="true">TS</span>
          <span className="app-sidebar__wordmark">TurnoShift</span>
        </div>

        {onClose && (
          <button
            type="button"
            className="app-sidebar__close-btn"
            onClick={onClose}
            aria-label="Cerrar menú"
          >
            <IconClose size={20} />
          </button>
        )}
      </div>

      {/* Navegación */}
      <nav className="app-sidebar__nav" aria-label="Navegación principal">
        <div className="app-sidebar__section-title">OPERACIONES</div>
        <ul className="app-sidebar__menu">
          {navItems.map((item) => {
            const active = isItemActive(item.to);
            return (
              <li key={item.to} className="app-sidebar__menu-item">
                <Link
                  to={item.to}
                  className={`app-sidebar__link ${active ? 'app-sidebar__link--active' : ''}`}
                  onClick={onClose}
                  aria-current={active ? 'page' : undefined}
                >
                  <span className="app-sidebar__link-icon" aria-hidden="true">
                    {item.icon}
                  </span>
                  <span className="app-sidebar__link-label">{item.label}</span>
                  {item.tag && (
                    <span className="app-sidebar__link-tag">{item.tag}</span>
                  )}
                </Link>
              </li>
            );
          })}
          <li className="app-sidebar__menu-item">
            <button
              type="button"
              className="app-sidebar__link app-sidebar__logout-link"
              onClick={handleLogout}
              disabled={isLoggingOut}
              aria-label="Cerrar sesión"
              title="Cerrar sesión"
            >
              <span className="app-sidebar__link-icon" aria-hidden="true">
                <IconLogout size={18} />
              </span>
              <span className="app-sidebar__link-label">Cerrar sesión</span>
            </button>
          </li>
        </ul>
        {logoutError && (
          <div className="app-sidebar__error-banner" role="alert">
            {logoutError}
          </div>
        )}
      </nav>
    </aside>
  );
}
