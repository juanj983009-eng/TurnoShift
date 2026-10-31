/**
 * TurnoShift — Native SPA Router
 * Enrutador declarativo ligero basado en la Web History API.
 * Soporta navegación sin recarga, sincronización con popstate y enlaces declarativos.
 */

import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
  type MouseEvent,
  type AnchorHTMLAttributes
} from 'react';

export interface RouterContextValue {
  readonly currentPath: string;
  navigate(to: string, options?: { replace?: boolean }): void;
}

const RouterContext = createContext<RouterContextValue | null>(null);

export interface RouterProps {
  readonly children: ReactNode;
}

export function Router({ children }: RouterProps) {
  const [currentPath, setCurrentPath] = useState<string>(() => window.location.pathname || '/');

  useEffect(() => {
    const handlePopState = () => {
      setCurrentPath(window.location.pathname || '/');
    };

    window.addEventListener('popstate', handlePopState);
    return () => {
      window.removeEventListener('popstate', handlePopState);
    };
  }, []);

  const navigate = useCallback((to: string, options?: { replace?: boolean }) => {
    if (options?.replace) {
      window.history.replaceState(null, '', to);
    } else {
      window.history.pushState(null, '', to);
    }
    setCurrentPath(to);
    window.scrollTo(0, 0);
  }, []);

  const value: RouterContextValue = {
    currentPath,
    navigate
  };

  return <RouterContext.Provider value={value}>{children}</RouterContext.Provider>;
}

export function useRouter(): RouterContextValue {
  const context = useContext(RouterContext);
  if (!context) {
    throw new Error('useRouter debe ser utilizado dentro de un Router');
  }
  return context;
}

export interface LinkProps extends AnchorHTMLAttributes<HTMLAnchorElement> {
  readonly to: string;
  readonly replace?: boolean;
  readonly className?: string;
  readonly children: ReactNode;
}

export function Link({ to, replace, className, children, onClick, ...rest }: LinkProps) {
  const { navigate } = useRouter();

  const handleClick = (e: MouseEvent<HTMLAnchorElement>) => {
    if (onClick) {
      onClick(e);
    }

    // Permitir comportamiento nativo para ctrl+click, cmd+click, click derecho, etc.
    if (
      !e.defaultPrevented &&
      e.button === 0 &&
      !e.metaKey &&
      !e.ctrlKey &&
      !e.shiftKey &&
      !e.altKey
    ) {
      e.preventDefault();
      navigate(to, { replace });
    }
  };

  return (
    <a href={to} onClick={handleClick} className={className} {...rest}>
      {children}
    </a>
  );
}
