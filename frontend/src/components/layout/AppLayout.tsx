import { useState, type ReactNode } from 'react';
import { AppSidebar } from './AppSidebar';
import { AppHeader } from './AppHeader';
import './AppLayout.css';

interface AppLayoutProps {
  readonly children: ReactNode;
}

export function AppLayout({ children }: AppLayoutProps) {
  const [isSidebarOpen, setIsSidebarOpen] = useState<boolean>(false);

  const toggleSidebar = () => {
    setIsSidebarOpen((prev) => !prev);
  };

  const closeSidebar = () => {
    setIsSidebarOpen(false);
  };

  return (
    <div className="app-layout">
      {/* Sidebar de navegación */}
      <AppSidebar isOpen={isSidebarOpen} onClose={closeSidebar} />

      {/* Backdrop para cerrar el menú en dispositivos móviles */}
      {isSidebarOpen && (
        <div
          className="app-layout__backdrop"
          onClick={closeSidebar}
          aria-hidden="true"
        />
      )}

      {/* Área principal */}
      <div className="app-layout__main">
        <AppHeader onToggleSidebar={toggleSidebar} />
        <main className="app-layout__content">
          {children}
        </main>
      </div>
    </div>
  );
}
