# TurnoShift UI Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modernizar visualmente las pantallas funcionales actuales para una demostración universitaria coherente, sin alterar comportamientos.

**Architecture:** El rediseño se apoya en tokens CSS compartidos y en CSS modular ya existente por página. Los componentes TypeScript solo se modifican si requieren una clase semántica adicional; no se cambian llamadas API, permisos, rutas ni contratos.

**Tech Stack:** React 19, TypeScript, CSS modular existente, Vite 6.

**Spec:** `docs/design/2026-10-29-ui-refresh-design.md`

## Global Constraints

- No modificar backend, OpenAPI, migraciones, dependencias npm ni reglas de negocio.
- Mantener ámbar para acciones principales y violeta solo como acento complementario.
- No crear datos, métricas, certificaciones, enlaces o funciones ficticias.
- Conservar accesibilidad: foco visible, contraste, teclado, semántica y estados de error.
- No introducir overflow horizontal global; validar a 80 %, 100 % y 125 % de zoom.

## Review Focus

- Pantallas estrechas: la tabla puede desplazarse dentro de su contenedor, nunca el documento completo.
- Modales: backdrop a viewport completo y scroll contenido en el cuerpo del modal.
- Acciones destructivas y confirmaciones: conservar contraste y jerarquía semántica.
- RBAC: los controles ocultos por permiso deben seguir ausentes, no solo atenuados.
- Estados loading/error/vacío: conservar mensajes reales y botones de reintento existentes.

---

### Task 1: Consolidar tokens y estilos globales

**Files:**
- Modify: `frontend/src/styles/tokens.css`
- Modify: `frontend/src/styles/global.css`
- Test: `frontend` production build and visual inspection.

**Interfaces:**
- Produces: tokens `--violet-*`, `--canvas`, `--surface`, `--ink`, `--muted`, radios y sombras usados por el shell y las páginas.

- [ ] Añadir tokens complementarios violeta/lila sin reemplazar `--accent` ni tokens semánticos existentes.
- [ ] Ajustar fondo global, scrollbars y foco para que utilicen los nuevos tokens con contraste suficiente.
- [ ] Ejecutar `npm run build` y comprobar que no existen errores TypeScript o Vite.

### Task 2: Renovar shell de aplicación y navegación

**Files:**
- Modify: `frontend/src/components/layout/AppLayout.css`
- Modify: `frontend/src/components/layout/AppHeader.css`
- Modify: `frontend/src/components/layout/AppSidebar.css`
- Test: visual inspection desktop/mobile and production build.

**Interfaces:**
- Consumes: tokens de Task 1.
- Produces: shell uniforme para todas las rutas `/app/*`.

- [ ] Aplicar canvas, superficies, bordes, radios y jerarquía visual de la especificación.
- [ ] Usar violeta suave para contexto activo y conservar ámbar en identidad/acciones principales donde ya corresponda.
- [ ] Verificar drawer móvil, breadcrumb truncado y cabecera sticky sin overflow horizontal.
- [ ] Ejecutar `npm run build`.

### Task 3: Unificar inicio y configuración organizacional

**Files:**
- Modify: `frontend/src/pages/home/HomePage.css`
- Modify: `frontend/src/pages/configuration/ConfigurationPage.css`
- Modify: componentes TSX solo si se necesita una clase semántica sin cambiar contenido o comportamiento.
- Test: visual inspection with real session data and production build.

**Interfaces:**
- Consumes: shell y tokens renovados.
- Produces: tarjetas, encabezados, formularios y mensajes de estado consistentes.

- [ ] Reorganizar únicamente presentación: cards, títulos, ayuda, botones y estados de lectura/edición.
- [ ] Mantener campos, permisos, llamadas API y textos reales.
- [ ] Comprobar foco, errores de formulario y estado de guardado.
- [ ] Ejecutar `npm run build`.

### Task 4: Modernizar catálogo y directorio operativo

**Files:**
- Modify: `frontend/src/pages/positions/PositionsPage.css`
- Modify: `frontend/src/pages/employees/EmployeesPage.css`
- Modify: CSS de modales de puestos y colaboradores si están separados.
- Test: visual inspection of filters, tables, empty/error/loading states and production build.

**Interfaces:**
- Consumes: estilos compartidos de Tasks 1–2.
- Produces: filtros, tablas, badges y acciones coherentes.

- [ ] Estilizar filtros, búsqueda, tablas y acciones respetando las clases y permisos existentes.
- [ ] Conservar scroll interno de tablas en pantallas pequeñas y evitar overflow de página.
- [ ] Ajustar modales para mantener pie de acciones visible y foco accesible.
- [ ] Ejecutar `npm run build`.

### Task 5: Afinar indisponibilidades, acceso y setup

**Files:**
- Modify: `frontend/src/pages/unavailabilities/UnavailabilitiesPage.css`
- Modify: `frontend/src/pages/login/LoginPage.css`
- Modify: `frontend/src/pages/setup/SetupPage.css`
- Test: visual inspection of modal, confidentiality banner, login/setup and production build.

**Interfaces:**
- Consumes: tokens y componentes visuales de Tasks 1–4.
- Produces: cierre visual coherente de todos los flujos demostrables.

- [ ] Mantener el portal de modales y cobertura total de viewport ya corregidos.
- [ ] Diferenciar advertencias, error, confidencialidad y cancelación mediante texto, icono y color.
- [ ] Mejorar acceso y setup sin añadir afirmaciones técnicas inexistentes.
- [ ] Ejecutar `npm run build`.

### Task 6: Verificación final de demo

**Files:**
- No code required unless se detecta una regresión visual concreta.

- [ ] Ejecutar `npm run build` desde `frontend/`.
- [ ] Revisar `/acceso`, `/app/inicio`, `/app/configuracion`, `/app/puestos`, `/app/equipo` y `/app/indisponibilidades` con datos reales.
- [ ] Comprobar zoom 80 %, 100 % y 125 %, navegación por teclado, modales y ausencia de scroll horizontal global.
- [ ] Registrar evidencia visual solo después de completar la verificación.
