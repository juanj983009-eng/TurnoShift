# PLAN DE IMPLEMENTACIÓN FRONTEND F1 — INFRAESTRUCTURA, AUTENTICACIÓN Y LAYOUT BASE

**Estado:** Plan de Implementación  
**Fecha:** 2026-09-25  
**Documento de diseño de referencia:** `docs/planning/23 - Diseño Frontend I2 - Configuración, Puestos y Colaboradores.md`  
**Contrato API vigente:** `docs/api/openapi.yaml` (versión 0.2.4-i2.4)  
**Alcance:** F1 — Base técnica de cliente HTTP, sesión en memoria, autenticación real con login/logout, enrutador SPA nativo, layout administrativo responsive y páginas base.  
**Stack frontend:** React 19.0.0, TypeScript 5.7.2, Vite 6.2.0, CSS Tokens nativos (sin dependencias npm adicionales).  

---

## 1. Visión General y Objetivos de F1

El propósito de la fase **F1** es dotar al frontend de TurnoShift de una infraestructura técnica sólida, tipada y segura para conectarse con la API backend existente, gestionar el ciclo de vida de la sesión empresarial y ofrecer un contenedor de navegación tipo SaaS responsive que sirva de base a las pantallas funcionales de **F2** (Configuración y Puestos) y **F3** (Colaboradores).

### Objetivos Principales
1. **Tipado estricto:** Definir los contratos TypeScript para identidad, sesión empresarial y errores estructurados RFC 9457 Problem Details.
2. **Cliente HTTP centralizado (`client.ts`):** Encapsular `fetch` con envío automático de cookies de sesión (`credentials: 'include'`), gestión de tokens CSRF en memoria e inyección en cabeceras `X-CSRF-TOKEN`, e intercepción de respuestas 401, 403 y errores de red.
3. **Autenticación real (`AuthContext.tsx`):**
   - Bootstrap al cargar la aplicación consultando `GET /api/v1/auth/csrf` y `GET /api/v1/auth/session`.
   - Conexión del formulario `LoginPage.tsx` existente con `POST /api/v1/auth/login`.
   - Cierre de sesión seguro con `POST /api/v1/auth/logout`.
   - Manejo de sesión expirada (límite de 8 horas o inactividad de 30 minutos).
   - **Cero almacenamiento en `localStorage`/`sessionStorage`:** Las credenciales y tokens residen exclusivamente en memoria volátil de React; la sesión del servidor se gobierna por la cookie HTTP-only `TSESSIONID`.
4. **Enrutamiento nativo sin dependencias externas:** Implementar un enrutador SPA ligero basado en la Web History API (`popstate`, `pushState`), mapeando las rutas del MVP (`/acceso`, `/acceso-restringido`, `/app/inicio`, `/app/configuracion`, `/app/equipo`, `/app/mi-horario`).
5. **Layout SaaS y diseño visual:** Crear la cabecera (`AppHeader`), barra lateral (`AppSidebar`), envoltura (`AppLayout`), página de acceso restringido (`ForbiddenPage`) e inicio operativo (`HomePage`), reutilizando los tokens CSS (`tokens.css`, `global.css`) y adaptándose a pantallas móviles y de escritorio.

---

## 2. Decisiones Técnicas y Reglas de Diseño

1. **Cero Dependencias NPM Nuevas:**
   - La aplicación utiliza React 19 y Vite 6. No se instalan librerías como `react-router-dom`, `axios` o gestores de estado externos (`redux`, `zustand`).
   - El enrutamiento se resuelve mediante un componente `Router` y hook `useRouter` nativos basados en `window.location.pathname` y `window.history`.
2. **Seguridad y Confidencialidad:**
   - Nunca almacenar contraseñas, hashes, tokens CSRF ni datos de usuario en `localStorage` o `sessionStorage`.
   - Los guards de rol en la interfaz (ocultar enlaces o redirigir a `/acceso-restringido`) son una mejora de experiencia de usuario y **no sustituyen la autorización RBAC del backend**, que siempre valida cada solicitud.
3. **Manejo de Errores Problem Details RFC 9457:**
   - Las respuestas de error del backend devuelven `application/problem+json` con estructura `{ type, status, title, detail, code, requestId, errors }`.
   - El cliente HTTP traduce estas respuestas en instancias tipadas de `ApiProblemError`.
   - Los fallos de red se capturan defensivamente y presentan un mensaje comprensible, evitando mostrar excepciones no controladas o URLs de infraestructura.
4. **Configuración de Proxy Vite para Desarrollo:**
   - El backend corre localmente en el puerto `8081` (o `8082`), mientras Vite corre en el puerto `3000`.
   - Se configura `proxy` en `vite.config.ts` para reenviar `/api` hacia `http://localhost:8081`, permitiendo que las peticiones frontend usen rutas relativas `/api/v1/...` con cookies compartidas sin problemas de CORS.
5. **Verificación Estricta:**
   - Dado que el proyecto no cuenta con librerías de prueba unitaria en `package.json`, la verificación técnica estandarizada para F1 es la ejecución limpia de:
     ```powershell
     npm run build
     ```
     que ejecuta `tsc -b && vite build` garantizando cero errores de compilación TypeScript y generación óptima del bundle en `dist/`.

---

## 3. Estructura de Archivos a Crear y Modificar

```
frontend/
├── vite.config.ts                                 # [MODIFICAR] Configurar proxy /api hacia http://localhost:8081
├── src/
│   ├── api/
│   │   ├── client.ts                              # [CREAR] Cliente HTTP centralizado con CSRF y Problem Details
│   │   └── authApi.ts                             # [CREAR] Métodos login, logout, getSessionState, getCsrfToken
│   ├── types/
│   │   ├── api.ts                                 # [CREAR] ProblemDetails, FieldErrorDto, ApiProblemError
│   │   └── auth.ts                                # [CREAR] SessionResponse, SessionUser, SessionOrganization, LoginRequest
│   ├── context/
│   │   └── AuthContext.tsx                        # [CREAR] AuthProvider y useAuth con estado en memoria
│   ├── components/
│   │   ├── routing/
│   │   │   └── Router.tsx                         # [CREAR] Enrutador declarativo nativo y hook useRouter
│   │   └── layout/
│   │       ├── AppHeader.tsx                      # [CREAR] Cabecera con datos de org, usuario, rol y logout
│   │       ├── AppHeader.css                      # [CREAR] Estilos cabecera
│   │       ├── AppSidebar.tsx                     # [CREAR] Menú lateral con visibilidad adaptada por rol
│   │       ├── AppSidebar.css                     # [CREAR] Estilos menú lateral
│   │       ├── AppLayout.tsx                      # [CREAR] Envoltura principal de la aplicación autenticada
│   │       └── AppLayout.css                      # [CREAR] Rejilla de layout responsive
│   ├── pages/
│   │   ├── login/
│   │   │   └── LoginPage.tsx                      # [MODIFICAR] Conectar al AuthContext y authApi real
│   │   ├── forbidden/
│   │   │   ├── ForbiddenPage.tsx                  # [CREAR] Pantalla UI-04 Acceso Restringido
│   │   │   └── ForbiddenPage.css                  # [CREAR] Estilos acceso restringido
│   │   └── home/
│   │       ├── HomePage.tsx                       # [CREAR] Vista UI-05 Inicio Operativo
│   │       └── HomePage.css                       # [CREAR] Estilos inicio operativo
│   └── App.tsx                                    # [MODIFICAR] Conectar AuthProvider y Router
```

---

## 4. Tareas Detalladas de Implementación

### Tarea F1.1: Configuración de Entorno y Capa de Tipos TypeScript

#### 1. Configurar Proxy en `frontend/vite.config.ts`
Añadir la sección `server.proxy` para que las llamadas relativas `/api` se redirijan automáticamente al backend de Spring Boot:
```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
});
```

#### 2. Crear `frontend/src/types/api.ts`
Modelar los errores estructurados RFC 9457 Problem Details y la clase de error personalizada:
```ts
export interface FieldErrorDto {
  field: string;
  code: string;
  message: string;
}

export interface ProblemDetails {
  type: string;
  status: number;
  title: string;
  detail?: string;
  instance?: string;
  code?: string;
  requestId?: string;
  errors?: FieldErrorDto[];
}

export class ApiProblemError extends Error {
  public readonly status: number;
  public readonly problem: ProblemDetails;

  constructor(problem: ProblemDetails) {
    super(problem.detail || problem.title || 'Error en la solicitud');
    this.name = 'ApiProblemError';
    this.status = problem.status;
    this.problem = problem;
  }

  get code(): string | undefined {
    return this.problem.code;
  }

  get fieldErrors(): FieldErrorDto[] {
    return this.problem.errors || [];
  }
}
```

#### 3. Crear `frontend/src/types/auth.ts`
Modelar los esquemas de sesión y autenticación según `openapi.yaml`:
```ts
export type UserRole = 'ADMINISTRADOR' | 'SUPERVISOR' | 'COLABORADOR';

export interface SessionUser {
  id: string;
  displayName: string;
  role: UserRole;
  employeeId?: string | null;
}

export interface SessionOrganization {
  id: string;
  displayName: string;
  timeZone: string;
}

export interface SessionResponse {
  user: SessionUser;
  organization: SessionOrganization;
  landingPage: string;
}

export interface CsrfTokenResponse {
  token: string;
}

export interface LoginRequest {
  orgCode: string;
  email: string;
  password: string;
}

export interface AuthState {
  status: 'loading' | 'authenticated' | 'unauthenticated';
  session: SessionResponse | null;
  csrfToken: string | null;
  sessionExpiredNotice: boolean;
}
```

---

### Tarea F1.2: Cliente HTTP Centralizado y Servicio de Autenticación

#### 1. Crear `frontend/src/api/client.ts`
- Administra la variable de memoria `activeCsrfToken` y los callbacks `onUnauthorized` y `onCsrfInvalid`.
- Función genérica `apiFetch<T>(endpoint: string, options?: RequestInit): Promise<T>`:
  - Configura `credentials: 'include'`.
  - Configura `headers['Accept'] = 'application/json, application/problem+json'`.
  - Si el método es `POST`, `PUT`, `PATCH` o `DELETE`, añade `headers['X-CSRF-TOKEN'] = activeCsrfToken`.
  - Si lleva cuerpo y no se especificó `Content-Type`, añade `headers['Content-Type'] = 'application/json'`.
  - Ante respuesta HTTP `>= 400`:
    - Intenta parsear el cuerpo JSON como `ProblemDetails`.
    - Si falla o viene en blanco, construye un `ProblemDetails` sintético con el status y statusText.
    - Si `status === 401`, dispara `onUnauthorized()`.
    - Si `status === 403` y `problem.code === 'CSRF_INVALID'`, dispara `onCsrfInvalid()`.
    - Lanza `new ApiProblemError(problem)`.
  - Ante error de red o timeout (`TypeError: Failed to fetch`):
    - Lanza `new ApiProblemError({ status: 0, type: '/problems/network-error', title: 'Error de conexión', detail: 'No se pudo conectar con el servidor. Verifica tu conexión.' })`.

#### 2. Crear `frontend/src/api/authApi.ts`
Implementar los cuatro endpoints de autenticación:
- `getCsrfToken(): Promise<CsrfTokenResponse>`: ejecuta `GET /api/v1/auth/csrf`.
- `getSessionState(): Promise<SessionResponse>`: ejecuta `GET /api/v1/auth/session`.
- `login(request: LoginRequest): Promise<SessionResponse>`: ejecuta `POST /api/v1/auth/login` con payload JSON.
- `logout(): Promise<void>`: ejecuta `POST /api/v1/auth/logout` con `{}` como cuerpo.

---

### Tarea F1.3: Contexto de Autenticación (`AuthContext`)

#### Crear `frontend/src/context/AuthContext.tsx`
- **Hook exportado:** `useAuth(): AuthContextValue`.
- **Estado React (en memoria):**
  - `status: 'loading' | 'authenticated' | 'unauthenticated'`
  - `session: SessionResponse | null`
  - `csrfToken: string | null`
  - `sessionExpiredNotice: boolean`
- **Ciclo de Bootstrap (`useEffect` al montar):**
  ```ts
  useEffect(() => {
    async function bootstrap() {
      try {
        const csrf = await authApi.getCsrfToken();
        setCsrfToken(csrf.token);
        setClientCsrfToken(csrf.token);

        try {
          const sessionData = await authApi.getSessionState();
          setSession(sessionData);
          setStatus('authenticated');
        } catch (err) {
          if (err instanceof ApiProblemError && err.status === 401) {
            setStatus('unauthenticated');
          } else {
            setStatus('unauthenticated');
          }
        }
      } catch {
        setStatus('unauthenticated');
      }
    }
    bootstrap();
  }, []);
  ```
- **Función `login(credentials: LoginRequest): Promise<SessionResponse>`:**
  1. Si no hay `csrfToken`, llama previamente a `getCsrfToken()`.
  2. Ejecuta `authApi.login(credentials)`.
  3. Actualiza el estado con la sesión devuelta y `status = 'authenticated'`.
  4. Llama inmediatamente a `getCsrfToken()` para renovar el token CSRF sobre la nueva sesión rotada.
  5. Retorna la sesión.
- **Función `logout(): Promise<void>`:**
  1. Ejecuta `authApi.logout()`.
  2. Limpia el estado de sesión: `setSession(null)`, `setStatus('unauthenticated')`.
  3. Llama a `getCsrfToken()` para obtener el nuevo token de la sesión anónima.
- **Manejo de sesión expirada:**
  - Registra el listener `onUnauthorized`: si el usuario estaba en estado `authenticated` y recibe un 401, cambia a `unauthenticated`, `session = null`, `sessionExpiredNotice = true`.

---

### Tarea F1.4: Enrutador Ligero y Navegación SPA

#### Crear `frontend/src/components/routing/Router.tsx`
- Implementar un enrutador declarativo sin librerías npm basado en la Web History API.
- Proveer el contexto `RouterContext` con:
  - `currentPath: string` (inicializado con `window.location.pathname`).
  - `navigate(to: string, replace?: boolean): void`: invoca `pushState` / `replaceState` y actualiza `currentPath`.
- Hook exportado: `useRouter()`.
- Soporte para enlaces declarativos: `<Link to="/..." className="...">`.
- Manejo del evento `window.addEventListener('popstate', ...)` para sincronizar la ruta con los botones de adelante/atrás del navegador.

---

### Tarea F1.5: Componentes de Layout y Páginas Base

#### 1. Crear `frontend/src/components/layout/AppHeader.tsx` y `AppHeader.css`
- Renderiza la cabecera superior persistente (56px de alto):
  - Identidad visual TurnoShift: glyph `TS` ámbar (`--accent`) y wordmark en fuente `DM Sans`.
  - Separador sutil.
  - Nombre de la organización (`session.organization.displayName`).
  - Identificador de zona horaria (`session.organization.timeZone`).
  - Bloque de usuario: `session.user.displayName` y badge pill con el rol formateado (`Administrador`, `Supervisor`, `Colaborador`).
  - Botón de cierre de sesión (`Cerrar sesión`) que invoca `logout()` de `AuthContext` y navega a `/acceso`.

#### 2. Crear `frontend/src/components/layout/AppSidebar.tsx` y `AppSidebar.css`
- Menú vertical de navegación lateral (240px de ancho):
  - Elementos de navegación según el rol del usuario:
    - **Inicio** (`/app/inicio`): icono de inicio. Visible para `ADMINISTRADOR` y `SUPERVISOR`.
    - **Equipo** (`/app/equipo`): icono de usuarios. Visible para `ADMINISTRADOR` y `SUPERVISOR`.
    - **Configuración** (`/app/configuracion`): icono de engranaje. Visible para `ADMINISTRADOR` y `SUPERVISOR`.
    - **Mi horario** (`/app/mi-horario`): icono de calendario. Visible para `COLABORADOR` (y usuarios vinculados con `employeeId`).
  - Estilo de enlace activo: fondo sutil `var(--stone-100)`, borde izquierdo `3px solid var(--accent)`, texto `var(--accent)`.

#### 3. Crear `frontend/src/components/layout/AppLayout.tsx` y `AppLayout.css`
- Componente envoltorio de la aplicación:
  - Estructura flexbox/grid: cabecera superior fija, contenedor con sidebar lateral y contenido principal (`<main class="app-content">`).
  - Responsive: en pantallas menores a `768px`, sidebar adaptado o colapsable.

#### 4. Crear `frontend/src/pages/forbidden/ForbiddenPage.tsx` y `ForbiddenPage.css`
- Vista UI-04 para código 403 o accesos a rutas no permitidas para el rol actual:
  - Tarjeta sobria con icono de escudo / alerta.
  - Título: *"Acceso restringido"*.
  - Detalle: *"Tu cuenta no tiene privilegios para acceder a esta sección con el rol actual ([ROL])."*
  - Botón principal *"Volver al inicio"* que redirige a su `landingPage`.

#### 5. Crear `frontend/src/pages/home/HomePage.tsx` y `HomePage.css`
- Vista UI-05 de inicio operativo para F1:
  - Título: *"Inicio Operativo"*.
  - Mensaje de bienvenida contextualizado con la organización y el rol.
  - Tarjetas informativas de acceso rápido a Configuración (F2) y Colaboradores (F3).
  - Placeholders informativos claros para las rutas `/app/configuracion` y `/app/equipo`, indicando *"Módulo programado para el siguiente subincremento (F2/F3)"*, permitiendo verificar la navegación sin errores.

---

### Tarea F1.6: Integración en `LoginPage.tsx` y `App.tsx`

#### 1. Actualizar `frontend/src/pages/login/LoginPage.tsx`
- Conectar con `useAuth()` y `useRouter()`:
  - Al enviar el formulario: invoca `login({ orgCode, email, password })`.
  - Ante éxito: redirige automáticamente a `session.landingPage` o a la ruta previa solicitada (`returnTo`).
  - Ante error (`ApiProblemError`):
    - Si el código es `AUTHENTICATION_FAILED` (401): *"Credenciales incorrectas o cuenta inactiva. Verifica el código de empresa, correo y contraseña."*
    - Si el código es `ACCESS_DENIED` (403): *"La organización se encuentra suspendida o no tiene acceso activo."*
    - Si el código es `RATE_LIMITED` (429): *"Demasiados intentos fallidos. Por favor, espera un minuto antes de reintentar."*
    - Si el status es 0 (error de red): *"No se pudo conectar con el servidor. Revisa tu conexión."*
  - Mostrar banner de aviso si `sessionExpiredNotice` es verdadero: *"Tu sesión ha caducado por inactividad o límite de tiempo. Por favor, inicia sesión de nuevo."*

#### 2. Actualizar `frontend/src/App.tsx`
- Envolver la aplicación con `AuthProvider` y `Router`.
- Lógica de enrutamiento raíz:
  - Si `status === 'loading'`: renderiza un spinner de carga centrado en la pantalla con el estilo `.spinner` y fondo `grid-pattern-bg`.
  - Si `status === 'unauthenticated'`: renderiza `LoginPage`.
  - Si `status === 'authenticated'`:
    - Si la ruta es `/acceso`: redirige a `session.landingPage`.
    - Si la ruta es `/acceso-restringido`: renderiza dentro de `AppLayout` la `ForbiddenPage`.
    - Si la ruta es `/app/inicio`: renderiza dentro de `AppLayout` la `HomePage`.
    - Si la ruta es `/app/configuracion`: si el rol es `COLABORADOR`, redirige a `/acceso-restringido`; si es `ADMINISTRADOR` o `SUPERVISOR`, renderiza la vista placeholder de configuración.
    - Si la ruta es `/app/equipo`: si el rol es `COLABORADOR`, redirige a `/acceso-restringido`; si es `ADMINISTRADOR` o `SUPERVISOR`, renderiza la vista placeholder de equipo.
    - Cualquier otra ruta: redirige a `session.landingPage`.

---

## 5. Criterios de Aceptación y Verificación

### Criterios de Aceptación Funcionales
1. **Bootstrap y Detección de Sesión:**
   - Si no hay sesión en el navegador, la aplicación carga en `/acceso` mostrando el formulario de login.
   - Si hay sesión válida, la aplicación carga directamente en `/app/inicio` (o su `landingPage`) sin parpadeos ni pedir credenciales nuevamente.
2. **Inicio de Sesión Real:**
   - El formulario envía `POST /api/v1/auth/login` con cabecera `X-CSRF-TOKEN` y credenciales.
   - Los errores de login (401, 403, 429) se presentan con mensajes comprensibles y amigables, sin alertar con popups ni filtrar trazas de stack trace.
3. **Cierre de Sesión:**
   - El botón "Cerrar sesión" en la cabecera ejecuta `POST /api/v1/auth/logout`, limpia el estado en memoria y devuelve al usuario a `/acceso`.
4. **Protección de Rutas y Guards de Rol:**
   - Un usuario con rol `COLABORADOR` no puede ver enlaces a `/app/configuracion` ni `/app/equipo`; si navega manualmente a esas URLs, se muestra `/acceso-restringido`.
   - Un usuario con rol `ADMINISTRADOR` o `SUPERVISOR` puede navegar libremente entre `/app/inicio`, `/app/configuracion` y `/app/equipo`.
5. **Sesión Expirada:**
   - Si la sesión caduca (401 en cualquier llamada subsecuente), el usuario es redirigido a `/acceso` con el banner informativo de sesión expirada.
6. **Seguridad en Almacenamiento:**
   - Verificación de que `window.localStorage` y `window.sessionStorage` permanecen 100% vacíos en todo momento.

### Verificación Técnica Única
Ejecución del comando de construcción en la raíz del frontend:
```powershell
npm run build
```
- **Requisitos de éxito:**
  - `tsc -b` completa sin errores de tipo TypeScript (`0 errors`).
  - `vite build` genera correctamente los bundles en `frontend/dist/`.
  - Cero dependencias rotas o advertencias bloqueantes.

---

## 6. Límites y Restricciones del Subincremento F1

- **No implementar pantallas funcionales de F2 ni F3:** Las rutas `/app/configuracion` y `/app/equipo` solo contendrán tarjetas explicativas de placeholder para verificar el enrutador y la navegación por roles.
- **No instalar librerías de prueba (Vitest/Jest/Cypress/Playwright):** No existen en `package.json` y la directriz prohíbe añadir dependencias no justificadas en este paso.
- **No modificar código backend, migraciones, base de datos ni contratos OpenAPI:** El backend ya funciona al 100% y se respeta en su totalidad.
- **No realizar operaciones Git (commits, push, pull, reset o tags):** Todos los cambios deben mantenerse en el entorno local.

---

## 7. Próximo Paso Inmediato

Iniciar la ejecución de **F1** implementando la **Tarea F1.1**:
- Configurar el proxy de desarrollo `/api` en `frontend/vite.config.ts`.
- Crear los archivos de tipos TypeScript `frontend/src/types/api.ts` y `frontend/src/types/auth.ts`.
- Crear el cliente HTTP centralizado `frontend/src/api/client.ts`.
