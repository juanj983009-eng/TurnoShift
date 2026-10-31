import { useEffect } from 'react';
import { useAuth } from './context/AuthContext';
import { useRouter } from './components/routing/Router';
import LoginPage from './pages/login/LoginPage';
import SetupPage from './pages/setup/SetupPage';
import { AppLayout } from './components/layout/AppLayout';
import HomePage from './pages/home/HomePage';
import ForbiddenPage from './pages/forbidden/ForbiddenPage';
import PlaceholderPage from './pages/placeholders/PlaceholderPage';
import ConfigurationPage from './pages/configuration/ConfigurationPage';
import PositionsPage from './pages/positions/PositionsPage';
import EmployeesPage from './pages/employees/EmployeesPage';
import UnavailabilitiesPage from './pages/unavailabilities/UnavailabilitiesPage';
import './App.css';

export default function App() {
  const { status, session } = useAuth();
  const { currentPath, navigate } = useRouter();

  const normalizedPath = currentPath.split('?')[0].split('#')[0];

  // Redirección para usuarios sin sesión activa
  useEffect(() => {
    if (
      status === 'unauthenticated' &&
      normalizedPath !== '/acceso' &&
      normalizedPath !== '/setup' &&
      normalizedPath !== '/login'
    ) {
      navigate('/acceso', { replace: true });
    }
  }, [status, normalizedPath, navigate]);

  // Redirección para usuarios autenticados que visitan la raíz o la pantalla de login/acceso
  useEffect(() => {
    if (status === 'authenticated' && session) {
      if (normalizedPath === '/' || normalizedPath === '/acceso' || normalizedPath === '/login') {
        const defaultDestination =
          session.landingPage ||
          (session.user.role === 'COLABORADOR' ? '/app/mi-horario' : '/app/inicio');
        navigate(defaultDestination, { replace: true });
      }
    }
  }, [status, session, normalizedPath, navigate]);

  // 1. Estado de carga de bootstrap inicial
  if (status === 'loading') {
    return (
      <div className="app-loading-screen grid-pattern-bg">
        <div className="app-loading-card">
          <span className="app-loading-spinner" aria-hidden="true" />
          <span className="app-loading-text">Cargando sesión de TurnoShift...</span>
        </div>
      </div>
    );
  }

  // Ruta pública para configuración inicial de contraseña (HU-04.1 / F1.5)
  if (normalizedPath === '/setup') {
    return <SetupPage />;
  }

  // Alias /login -> Redirige a la pantalla oficial /acceso
  if (normalizedPath === '/login') {
    navigate('/acceso', { replace: true });
    return <LoginPage />;
  }

  // 2. Estado no autenticado -> Renderizar página de acceso
  if (status === 'unauthenticated' || !session) {
    return <LoginPage />;
  }

  // 3. Estado autenticado -> Enrutar con layout base y guardias de rol
  const { role } = session.user;

  const renderContent = () => {
    // Si aún se está redirigiendo desde /, /acceso o /login, mostrar carga breve
    if (normalizedPath === '/' || normalizedPath === '/acceso' || normalizedPath === '/login') {
      return (
        <div className="app-loading-screen">
          <span className="app-loading-spinner" aria-hidden="true" />
        </div>
      );
    }

    // Ruta de acceso restringido explícito
    if (normalizedPath === '/acceso-restringido') {
      return <ForbiddenPage />;
    }

    // Ruta /app/inicio: Solo para Administradores y Supervisores
    if (normalizedPath === '/app/inicio') {
      if (role === 'COLABORADOR') {
        return <ForbiddenPage />;
      }
      return <HomePage />;
    }

    // Ruta /app/configuracion: Exclusiva para Administradores
    if (normalizedPath === '/app/configuracion') {
      if (role !== 'ADMINISTRADOR') {
        return <ForbiddenPage />;
      }
      return <ConfigurationPage />;
    }

    // Ruta /app/puestos: Para Administradores (gestión) y Supervisores (consulta)
    if (normalizedPath === '/app/puestos') {
      if (role !== 'ADMINISTRADOR' && role !== 'SUPERVISOR') {
        return <ForbiddenPage />;
      }
      return <PositionsPage />;
    }

    // Ruta /app/equipo: Para Administradores y Supervisores
    if (normalizedPath === '/app/equipo') {
      if (role !== 'ADMINISTRADOR' && role !== 'SUPERVISOR') {
        return <ForbiddenPage />;
      }
      return <EmployeesPage />;
    }

    // Ruta /app/indisponibilidades: Para Administradores y Supervisores (HU-08 / HU-10)
    if (normalizedPath === '/app/indisponibilidades') {
      if (role !== 'ADMINISTRADOR' && role !== 'SUPERVISOR') {
        return <ForbiddenPage />;
      }
      return <UnavailabilitiesPage />;
    }

    // Ruta /app/mi-horario: Para Colaboradores o usuarios con employeeId
    if (normalizedPath === '/app/mi-horario') {
      if (role === 'COLABORADOR' || session.user.employeeId) {
        return (
          <PlaceholderPage
            title="Mi Horario Personal"
            phaseBadge="Portal del Colaborador"
            description="Consulta tu programación de turnos y registra tus indisponibilidades laborales."
            plannedFeatures={[
              'Calendario de turnos asignados por semana y mes',
              'Registro y seguimiento de solicitudes de indisponibilidad',
              'Visualización sincronizada con la zona horaria de la organización'
            ]}
          />
        );
      }
      return <ForbiddenPage />;
    }

    // Ruta no reconocida -> Acceso restringido / 404
    return <ForbiddenPage />;
  };

  return <AppLayout>{renderContent()}</AppLayout>;
}
