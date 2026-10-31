# 5. Pantallas web

El presente capítulo desarrolla el apartado **5. PANTALLAS WEB** del informe académico de **TurnoShift**, documentando las interfaces gráficas reales, funcionales y verificables construidas en la Single Page Application (SPA) en React 19 y TypeScript ([`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx)) e integradas con los servicios REST de Spring Boot 4 (`OpenAPI v0.2.4-i2.4`) y la base de datos relacional Microsoft SQL Server 2022 ([`docs/deliverables/03 - Diseño de la Solución.md`](file:///d:/Proyectos/TurnoShift/docs/deliverables/03%20-%20Dise%C3%B1o%20de%20la%20Soluci%C3%B3n.md), [`docs/deliverables/04 - Prototipos.md`](file:///d:/Proyectos/TurnoShift/docs/deliverables/04%20-%20Prototipos.md) y [`docs/planning/30 - Auditoría Académica de Entrega 2 a 6.md`](file:///d:/Proyectos/TurnoShift/docs/planning/30%20-%20Auditor%C3%ADa%20Acad%C3%A9mica%20de%20Entrega%202%20a%206.md)).

Siguiendo el principio de honestidad técnica y trazabilidad documental del proyecto:
- En la **Sección 5.3** se presentan exclusivamente las **ocho pantallas web funcionales e implementadas** en el código fuente (`F1` a `F4`), acompañadas de sus respectivos marcadores textuales para la inserción manual de evidencias gráficas (`estado de captura: pendiente de evidencia manual`).
- En la **Sección 5.4** se explican de manera explícita las **pantallas no implementadas** (correspondientes a la interfaz frontend de políticas operativas `I2.3` y a los módulos futuros de horarios `I3` e intercambios `I4`), justificando por qué no deben presentarse como pantallas terminadas.

---

## 5.1 Propósito del catálogo de pantallas y relación con el caso Café Andino S.A.C.

Dentro del caso de estudio **Café Andino S.A.C.** —cafetería y restaurante casual en Lima con operación continua de lunes a domingo de `06:30` a `22:30` horas y personal distribuido en los puestos operativos definidos para el piloto—, el catálogo de pantallas web construidas cumple cuatro propósitos operativos directos:

1. **Asegurar el acceso multiempresa y la incorporación segura sin fricción:** Proveer las vistas de establecimiento de primera contraseña (`/setup#token=...`) y de acceso empresarial (`/acceso`), permitiendo que el administrador, los supervisores y los colaboradores ingresen bajo el código de organización correspondiente (`DEMO-2026` / **Café Andino S.A.C.**) con sesiones protegidas en servidor (`TSESSIONID`).
2. **Centralizar la estructura organizacional y los puestos del local (`I2.1`):** Ofrecer al administrador las pantallas de configuración (`/app/configuracion`) y de catálogo de puestos (`/app/puestos`), reemplazando las listas informales en hojas de cálculo por un catálogo único con estados activos/inactivos y control de concurrencia optimista (`expectedLockVersion`).
3. **Administrar el padrón de colaboradores con trazabilidad de licencias y bloqueos (`I2.2`):** Facilitar en `/app/equipo` el alta, edición, activación y evaluación previa de desactivación (`deactivation-preview`) del personal asignado a la sede activa y a su puesto de trabajo.
4. **Gestionar el ciclo de vida real de las indisponibilidades del personal (`I2.4`):** Permitir tanto al `ADMINISTRADOR` como al `SUPERVISOR` registrar, consultar, actualizar y cancelar indisponibilidades en `/app/indisponibilidades` bajo el ciclo de vida **`ACTIVA` / `CANCELADA`**, respetando la zona horaria organizacional (`America/Lima`), la restricción temporal `RN-013` y la reserva de confidencialidad de las notas restringidas (`RN-014`).

---

## 5.2 Convenciones de la interfaz

Las pantallas web construidas comparten cinco convenciones arquitectónicas y de experiencia de usuario verificadas en [`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) y sus componentes asociados:

1. **SPA web responsiva (no aplicación móvil nativa):**
   - **TurnoShift** se entrega como una **Single Page Application (SPA) web responsiva** ejecutada en navegadores modernos de escritorio y dispositivos móviles. No es ni debe denominarse una aplicación móvil nativa (iOS/Android).
   - El contenedor [`AppLayout.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/components/layout/AppLayout.tsx) adapta automáticamente la disposición entre escritorio y pantallas reducidas sin producir desbordamiento horizontal a niveles de zoom de `80 %`, `100 %` y `125 %`.
2. **Navegación lateral (`AppSidebar`) y cabecera contextual (`AppHeader`):**
   - **Cabecera ([`AppHeader.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/components/layout/AppHeader.tsx)):** Despliega la miga de pan (*breadcrumb* `TurnoShift / Operaciones / ...`), la pastilla de la organización con su zona horaria oficial (`timeZone`), la insignia del rol activo y el botón de apertura del menú móvil.
   - **Barra lateral ([`AppSidebar.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/components/layout/AppSidebar.tsx)):** Presenta el distintivo institucional `TS` en tono ámbar (`#b45309`), la sección `OPERACIONES` con enlaces filtrados por rol, el distintivo `"Consulta"` junto a los módulos donde el `SUPERVISOR` solo tiene permiso de lectura (`/app/puestos` y `/app/equipo`), la tarjeta de la organización y el control de cierre de sesión seguro (`POST /api/v1/auth/logout`).
3. **Sesión en memoria y control visual por rol (`RBAC`):**
   - El contexto [`AuthContext.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/context/AuthContext.tsx) mantiene el estado de la sesión (`GET /api/v1/auth/session`) y el token `X-CSRF-TOKEN` exclusivamente en memoria volátil, sin persistir ningún secreto ni dato de sesión en `localStorage` o `sessionStorage`.
   - El enrutador en [`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) redirige a `/acceso` a cualquier usuario no autenticado y presenta [`ForbiddenPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/forbidden/ForbiddenPage.tsx) (`/acceso-restringido`) cuando un rol intenta abrir una ruta no autorizada. En las vistas de solo lectura para `SUPERVISOR` (`/app/puestos` y `/app/equipo`), los botones de mutación se omiten íntegramente del DOM.
4. **Formularios, modales en portal, mensajes de error RFC 9457 y confirmación:**
   - Los diálogos modales (`PositionModal`, `PositionStatusModal`, `EmployeeModal`, `EmployeeStatusModal`, `UnavailabilityModal`, `UnavailabilityCancelModal`) se renderizan mediante portales en `document.body`, con foco inicial accesible, soporte de tecla `Escape` y prevención de envíos duplicados mientras una solicitud está en vuelo.
   - Toda mutación genera una clave `Idempotency-Key` (`crypto.randomUUID()`) que solo se reutiliza ante fallos reales de red (`isNetworkError`) si la intención (`PendingOperationIntent`) permanece idéntica.
   - Los errores devueltos por el backend bajo el formato **RFC 9457 (`Problem Details`)** se presentan en banners o alertas de campo comprensibles (por ejemplo, el conflicto `409 CONCURRENT_MODIFICATION` ofrece un banner contextual con botón de recarga manual sin sobrescribir silenciosamente los datos).

---

## 5.3 Fichas de pantallas funcionales

A continuación se documentan las ocho pantallas funcionales verificadas en `frontend/src/pages/`.

---

### Ficha 01 — Acceso empresarial (`/acceso`)

- **Objetivo:** Autenticar al usuario dentro de su organización mediante credenciales verificadas contra Argon2id y establecer la sesión HTTP persistida en SQL Server (`TSESSIONID`).
- **Rol o condición de acceso verificada:** Ruta pública para usuarios no autenticados (`/acceso`; el alias `/login` redirige automáticamente a `/acceso`). Si el usuario ya posee una sesión autenticada, [`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) (líneas 35–44) lo redirige a `/app/inicio` (`ADMINISTRADOR` y `SUPERVISOR`) o a `/app/mi-horario` (`COLABORADOR`).
- **Acciones principales:**
  - Ingresar `Código de organización` (`organizationCode`), `Correo electrónico` (`email`) y `Contraseña` (`password`).
  - Alternar la visibilidad de la contraseña mediante un botón accesible por teclado con `aria-label` dinámico.
  - Enviar el formulario hacia `POST /api/v1/auth/login` y renovar el token CSRF tras la rotación del ID de sesión (`changeSessionId`).
- **Datos visibles:** Identidad visual de **TurnoShift**, campos de código organizacional, correo y contraseña, y botón de ingreso. (Sin casillas de recordar dispositivo, sin SSO, sin enlaces ficticios de recuperación de contraseña ni certificaciones no verificadas).
- **Validaciones o estados de interfaz verificados:**
  - Estado de envío en curso con bloqueo de botón.
  - Errores de validación de campos (`400 VALIDATION_FAILED`).
  - Mensaje unificado e indistinguible ante credenciales inválidas (`401 AUTHENTICATION_FAILED`), evitando la enumeración de usuarios u organizaciones.
  - Mensaje sobrio ante limitación de tasa por IP (`429 RATE_LIMITED`), error de servicio no disponible (`503 SERVICE_UNAVAILABLE`) o fallo de red, y banner informativo cuando el usuario llega por expiración de sesión.
- **Componente / página fuente:** [`frontend/src/pages/login/LoginPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/login/LoginPage.tsx) y [`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) (líneas 23–72).
- **Estado de captura:** `pendiente de evidencia manual`

> **[MARCADOR DE CAPTURA — `CAP-01`: Pantalla de Acceso Empresarial (`/acceso`)]**
> *Insertar aquí la captura manual de la ruta `/acceso` mostrando el formulario con el código `DEMO-2026`, correo de demostración y contraseña oculta (`••••••••`), sin revelar credenciales en texto plano.*

---

### Ficha 02 — Configuración inicial de contraseña (`/setup#token=...`)

- **Objetivo:** Permitir que un usuario incorporado recientemente establezca su primera contraseña personal consumiendo de manera atómica el token de acceso inicial (`purpose = 'INITIAL_SETUP'`).
- **Rol o condición de acceso verificada:** Ruta pública `/setup` ([`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) líneas 58–61) que recibe el token de 43 caracteres Base64 URL-safe exclusivamente a través del fragmento `window.location.hash` (`#token=...`).
- **Acciones principales:**
  - Leer en memoria el token desde `window.location.hash` sin enviarlo en parámetros de consulta (*query params*) ni guardarlo en `localStorage` o `sessionStorage`.
  - Ingresar la nueva contraseña y su confirmación.
  - Invocar `POST /api/v1/auth/password-reset/complete` con protección `X-CSRF-TOKEN`.
  - Limpiar de inmediato el fragmento hash de la barra de direcciones mediante `history.replaceState` tras completarse la operación y redirigir a `/acceso`.
- **Datos visibles:** Formulario de creación y confirmación de contraseña e indicaciones sobrias de seguridad.
- **Validaciones o estados de interfaz verificados:**
  - Validación local de campos obligatorios y coincidencia entre nueva contraseña y confirmación.
  - Validación delegada al servidor de longitud (15–128 caracteres), complejidad y lista de contraseñas bloqueadas **Have I Been Pwned (`k-anonymity`)** (`400 VALIDATION_FAILED` mapeado al campo `newPassword`).
  - Estado seguro de enlace inválido, expirado, revocado o ya utilizado (`422 RESET_LINK_INVALID`) sin filtrar detalles internos de base de datos, además de manejo de `429 RATE_LIMITED` y `503 SERVICE_UNAVAILABLE`.
- **Componente / página fuente:** [`frontend/src/pages/setup/SetupPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/setup/SetupPage.tsx) y [`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) (líneas 58–61).
- **Estado de captura:** `pendiente de evidencia manual`

> **[MARCADOR DE CAPTURA — `CAP-02`: Pantalla de Configuración Inicial (`/setup`)]**
> *Insertar aquí la captura manual de la ruta `/setup` (con campos de contraseña enmascarados o estado seguro de enlace no disponible `422`), verificando que no aparezca ningún token real en la URL ni en el contenido de la imagen.*

---

### Ficha 03 — Inicio operativo (`/app/inicio`)

- **Objetivo:** Ofrecer el panel de aterrizaje autenticado con el contexto real de la sesión empresarial y accesos directos a los módulos habilitados de la organización.
- **Rol o condición de acceso verificada:** Usuarios autenticados con rol `ADMINISTRADOR` o `SUPERVISOR`. Si un usuario con rol `COLABORADOR` navega a `/app/inicio`, [`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) (líneas 93–98) renderiza `ForbiddenPage`.
- **Acciones principales:**
  - Consultar de un vistazo el resumen de sesión activa (`GET /api/v1/auth/session`).
  - Navegar directamente hacia Configuración (`/app/configuracion`, visible para `ADMINISTRADOR`), Puestos (`/app/puestos`), Equipo (`/app/equipo`) e Indisponibilidades (`/app/indisponibilidades`).
  - Ejecutar el cierre de sesión seguro desde el perfil de usuario en `AppSidebar`.
- **Datos visibles:** Saludo nominal al usuario, rol asignado (`Administrador` o `Supervisor`), nombre y código de la organización, sede activa y zona horaria oficial (`timeZone`, ej. `America/Lima`). Cero métricas ficticias, sin contadores de asistencia en tiempo real ni gráficos simulados.
- **Validaciones o estados de interfaz verificados:**
  - Pantalla de carga inicial de sesión (`app-loading-screen` en `App.tsx`).
  - Renderizado condicional de tarjetas de acceso según los permisos del rol activo.
- **Componente / página fuente:** [`frontend/src/pages/home/HomePage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/home/HomePage.tsx), [`frontend/src/components/layout/AppLayout.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/components/layout/AppLayout.tsx), [`frontend/src/components/layout/AppHeader.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/components/layout/AppHeader.tsx) y [`frontend/src/components/layout/AppSidebar.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/components/layout/AppSidebar.tsx).
- **Estado de captura:** `pendiente de evidencia manual`

> **[MARCADOR DE CAPTURA — `CAP-03`: Pantalla de Inicio Operativo (`/app/inicio`)]**
> *Insertar aquí la captura manual de `/app/inicio` autenticada como `ADMINISTRADOR` en la organización de demostración (`DEMO-2026` / Café Andino S.A.C.), mostrando la cabecera con zona horaria `America/Lima`, la barra lateral y las tarjetas de acceso.*

---

### Ficha 04 — Configuración organizacional (`/app/configuracion`)

- **Objetivo:** Consultar los parámetros generales de la organización y de su sede activa única, y actualizar sus nombres visibles bajo control de concurrencia optimista e idempotencia (`I2.1` / `HU-05`).
- **Rol o condición de acceso verificada:** Exclusivo para el rol `ADMINISTRADOR`. Los roles `SUPERVISOR` y `COLABORADOR` son desviados a `ForbiddenPage` ([`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) líneas 100–106).
- **Acciones principales:**
  - Cargar los datos reales desde `GET /api/v1/organization/configuration`.
  - Actualizar el nombre visible de la organización mediante `PATCH /api/v1/organization/configuration/display-name` enviando `expectedLockVersion` e `Idempotency-Key`.
  - Actualizar de manera independiente el nombre de la sede activa mediante `PATCH /api/v1/organization/configuration/active-site/name` enviando `expectedLockVersion` e `Idempotency-Key`.
- **Datos visibles:** Código y nombre visible de la organización (`displayName`), código y nombre de la sede activa (`activeSite.code`, `activeSite.name`) y zona horaria IANA (`timezoneId`) expuesta estrictamente en **modo de solo lectura**. (No incluye áreas organizativas ficticias, ni pantalla de políticas operativas `I2.3`, ni integraciones simuladas con reloj biométrico o ERP).
- **Validaciones o estados de interfaz verificados:**
  - Estado de carga inicial y estado de error con opción de recarga.
  - Deshabilitación de guardado cuando no existen cambios respecto al valor cargado.
  - Retención de la misma `Idempotency-Key` únicamente ante error de red (`isNetworkError`).
  - Resolución del conflicto `409 CONCURRENT_MODIFICATION` mediante un banner contextual y un botón de recarga manual que preserva los cambios locales no colisionados.
- **Componente / página fuente:** [`frontend/src/pages/configuration/ConfigurationPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/configuration/ConfigurationPage.tsx) y [`frontend/src/api/organizationApi.ts`](file:///d:/Proyectos/TurnoShift/frontend/src/api/organizationApi.ts).
- **Estado de captura:** `pendiente de evidencia manual`

> **[MARCADOR DE CAPTURA — `CAP-04`: Pantalla de Configuración Organizacional (`/app/configuracion`)]**
> *Insertar aquí la captura manual de `/app/configuracion` con sesión `ADMINISTRADOR`, evidenciando la edición del nombre visible de la organización, el nombre de la sede activa y el campo `timezoneId` en modo de solo lectura.*

---

### Ficha 05 — Catálogo de puestos de trabajo (`/app/puestos`)

- **Objetivo:** Administrar el catálogo de puestos operativos definidos para el piloto de **Café Andino S.A.C.** (`I2.1` / `HU-06`).
- **Rol o condición de acceso verificada:** `ADMINISTRADOR` (gestión completa: consulta, creación, edición y activación/desactivación) y `SUPERVISOR` (modo consulta estricto con controles de mutación omitidos del DOM). El rol `COLABORADOR` recibe `ForbiddenPage` ([`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) líneas 108–114).
- **Acciones principales:**
  - Filtrar puestos por estado (`Todos`, `Activos`, `Inactivos`) invocando `GET /api/v1/positions` y buscar en memoria por código o nombre.
  - Para `ADMINISTRADOR`: abrir `PositionModal` para crear un puesto (`POST /api/v1/positions`, con código `^[A-Z0-9-]+$` normalizado en mayúsculas) o editar su nombre descriptivo manteniendo el código inmutable (`PUT /api/v1/positions/{positionId}`).
  - Para `ADMINISTRADOR`: abrir `PositionStatusModal` para activar o desactivar un puesto (`PATCH /api/v1/positions/{positionId}/status`) enviando `expectedLockVersion` e `Idempotency-Key`.
- **Datos visibles:** Código inmutable del puesto, nombre descriptivo, insignia de estado (`Activo` / `Inactivo`) y controles de acción (solo visibles para `ADMINISTRADOR`) o banner informativo de modo supervisión (para `SUPERVISOR`).
- **Validaciones o estados de interfaz verificados:**
  - Estados de carga (`isLoading`), catálogo vacío general, búsqueda sin resultados y error de carga con botón de reintento (`handleReload`).
  - Mapeo del error `409 POSITION_CODE_ALREADY_EXISTS` dentro del modal de creación y manejo de `409 CONCURRENT_MODIFICATION` con recarga manual.
  - Textos de confirmación neutrales y verificables en `PositionStatusModal` (*"El puesto quedará marcado como activo/inactivo"*), sin prometer bloqueos automáticos no ejecutados por dicho endpoint.
- **Componente / página fuente:** [`frontend/src/pages/positions/PositionsPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/positions/PositionsPage.tsx), `PositionModal`, `PositionStatusModal` y [`frontend/src/api/positionApi.ts`](file:///d:/Proyectos/TurnoShift/frontend/src/api/positionApi.ts).
- **Estado de captura:** `pendiente de evidencia manual`

> **[MARCADOR DE CAPTURA — `CAP-05A`: Catálogo de Puestos en modo Gestión (`/app/puestos` — `ADMINISTRADOR`)]**
> *Insertar aquí la captura manual de `/app/puestos` como `ADMINISTRADOR` mostrando los puestos definidos para el piloto y el modal `PositionModal` abierto.*
>
> **[MARCADOR DE CAPTURA — `CAP-05B`: Catálogo de Puestos en modo Consulta (`/app/puestos` — `SUPERVISOR`)]**
> *Insertar aquí la captura manual de `/app/puestos` como `SUPERVISOR` evidenciando el distintivo `"Consulta"` en la barra lateral, el banner de supervisión y la omisión total de botones de mutación.*

---

### Ficha 06 — Directorio y gestión de equipo (`/app/equipo`)

- **Objetivo:** Administrar el directorio de colaboradores de la organización, su vínculo con el puesto de trabajo y la sede activa, y su estado operativo (`I2.2` / `F3`).
- **Rol o condición de acceso verificada:** `ADMINISTRADOR` (gestión completa) y `SUPERVISOR` (modo consulta estricto sin controles de mutación en el DOM). El rol `COLABORADOR` recibe `ForbiddenPage` ([`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) líneas 116–122).
- **Acciones principales:**
  - Carga coordinada en paralelo (`Promise.all`) de colaboradores (`GET /api/v1/employees`), catálogo de puestos (`GET /api/v1/positions`) y configuración de sede activa (`GET /api/v1/organization/configuration`).
  - Filtrar colaboradores por estado (`Todos`, `Activos`, `Inactivos`), por puesto mediante selector *dropdown* y buscar en memoria por código (`code`) o nombre visible (`displayName`).
  - Para `ADMINISTRADOR`: registrar un nuevo colaborador en `EmployeeModal` (`POST /api/v1/employees`) seleccionando un puesto activo; o editar su nombre y puesto (`PUT /api/v1/employees/{employeeId}`).
  - Para `ADMINISTRADOR`: activar un colaborador (`POST .../activate`) o desactivarlo en dos pasos mediante `EmployeeStatusModal`, que primero consulta `POST /api/v1/employees/{employeeId}/deactivation-preview` para verificar si `canDeactivate` es verdadero o si existen `blockers` reales devueltos por el servidor.
- **Datos visibles:** Código inmutable del colaborador (`code`), nombre visible (`displayName`), nombre del puesto resuelto en memoria (`positionId`), nombre de la sede activa (`siteId`) e insignia de estado (`Activo` / `Inactivo`). (Poda contractual estricta: sin fotos de perfil, sin teléfonos y sin correos electrónicos en la tabla de `employees`).
- **Validaciones o estados de interfaz verificados:**
  - Estados de carga (`employees-spinner-large`), error si falla cualquiera de las tres fuentes de `Promise.all` (sin mostrar datos parciales), directorio vacío general y búsqueda sin resultados con botón para limpiar filtros.
  - En `EmployeeModal`: validación de formato de código (`^[A-Z0-9-]+$`, 2 a 32 caracteres sin conversión silenciosa), código bloqueado en edición, e impedimento de guardar sin cambios.
  - En `EmployeeStatusModal`: deshabilitación del botón de confirmación si `deactivation-preview` reporta `canDeactivate = false`, mostrando los `blockers` reales.
  - Mapeo de errores RFC 9457 (`EMPLOYEE_CODE_ALREADY_EXISTS`, `POSITION_INACTIVE`, `EMPLOYEE_LICENSE_LIMIT_REACHED`, `CONCURRENT_MODIFICATION`).
- **Componente / página fuente:** [`frontend/src/pages/employees/EmployeesPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/employees/EmployeesPage.tsx), `EmployeeModal`, `EmployeeStatusModal` y [`frontend/src/api/employeeApi.ts`](file:///d:/Proyectos/TurnoShift/frontend/src/api/employeeApi.ts).
- **Estado de captura:** `pendiente de evidencia manual`

> **[MARCADOR DE CAPTURA — `CAP-06`: Directorio y Gestión de Equipo (`/app/equipo` — `ADMINISTRADOR`)]**
> *Insertar aquí la captura manual de `/app/equipo` mostrando el listado de colaboradores vinculados a sus puestos y sede activa, junto con el modal `EmployeeModal` o `EmployeeStatusModal`.*

---

### Ficha 07 — Gestión de indisponibilidades (`/app/indisponibilidades`)

- **Objetivo:** Registrar, consultar, actualizar y cancelar las indisponibilidades del personal con ciclo de vida **`ACTIVA` / `CANCELADA`** (`I2.4` / `HU-08` / `HU-10` / `F4`).
- **Rol o condición de acceso verificada:** `ADMINISTRADOR` y `SUPERVISOR` con permisos simétricos de consulta y gestión (`RN-012`, `RN-014`). El rol `COLABORADOR` es desviado a `ForbiddenPage` ([`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) líneas 124–130).
- **Acciones principales:**
  - Seleccionar la ventana temporal de consulta (`30`, `60` o `90` días hacia adelante en UTC) y alternar el interruptor `includeCancelled` para consultar `GET /api/v1/unavailabilities` en paralelo con el directorio de colaboradores (`employeeMap`).
  - Filtrar localmente por colaborador (soportando preselección por query param `?employeeId=...`), por motivo (`VACACIONES`, `PERMISO_PERSONAL`, `MEDICO`, `FORMACION`, `OTRO`) y por búsqueda de texto.
  - Crear una indisponibilidad (`POST /api/v1/employees/{employeeId}/unavailabilities`) o actualizar una existente (`PUT /api/v1/unavailabilities/{unavailabilityId}`) mediante `UnavailabilityModal`, eligiendo entre **intervalo parcial UTC** (`startsAt`, `endsAt`) o **día completo organizacional** (`allDay = true`, `startDate`, `endDate` calculadas en la zona horaria IANA de la organización).
  - Cancelar una indisponibilidad activa (`POST /api/v1/unavailabilities/{unavailabilityId}/cancel`) mediante `UnavailabilityCancelModal`, ingresando el motivo de cancelación (`cancellationReason`) y `expectedLockVersion`.
- **Datos visibles:** Colaborador asignado, intervalo horario formateado en la zona horaria del tenant (`America/Lima`), etiqueta semántica del motivo (`Vacaciones`, `Permiso personal`, `Médico`, `Formación`, `Otro motivo`), insignia de estado (`ACTIVA` o `CANCELADA`, sin estados `APPROVED` ni `REJECTED`) y acciones de edición/cancelación. **Por cumplimiento de `RN-014`, el campo `restricted_note` nunca se renderiza en la tabla principal, filtros ni búsquedas; solo está disponible dentro del modal autorizado.**
- **Validaciones o estados de interfaz verificados:**
  - **Comportamiento de superposición:** Los intervalos superpuestos están permitidos por diseño en `I2.4` (no existe error `UNAVAILABILITY_OVERLAP` en esta pantalla); la compatibilidad con los turnos se evaluará en `I3`.
  - **Restricción temporal (`RN-013`):** En la creación se permite registrar un intervalo en curso si termina en el futuro (`startsAt <= now` y `endsAt > now`) y se rechazan intervalos ya terminados (`endsAt <= now`). En cambio, para cualquier registro cuyo periodo ya haya iniciado (`startsAt <= now`), la pantalla **bloquea la actualización y la cancelación** (deshabilitando los botones en la fila con *tooltip* explicativo y validándolo en el modal y en el servidor), impidiendo además mover el inicio al pasado (`newStartsAt <= now`).
  - **Edición honesta de formato temporal:** Al abrir `UnavailabilityModal` en modo edición, se presenta en modo parcial UTC por defecto con la aclaración explícita: *"El formato original no se conserva. Para guardar como día completo, selecciona las fechas locales."*
- **Componente / página fuente:** [`frontend/src/pages/unavailabilities/UnavailabilitiesPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/unavailabilities/UnavailabilitiesPage.tsx), [`UnavailabilityModal.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/unavailabilities/UnavailabilityModal.tsx), [`UnavailabilityCancelModal.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/unavailabilities/UnavailabilityCancelModal.tsx) y [`frontend/src/api/unavailabilityApi.ts`](file:///d:/Proyectos/TurnoShift/frontend/src/api/unavailabilityApi.ts).
- **Estado de captura:** `pendiente de evidencia manual`

> **[MARCADOR DE CAPTURA — `CAP-07A`: Listado y Filtros de Indisponibilidades (`/app/indisponibilidades`)]**
> *Insertar aquí la captura manual de `/app/indisponibilidades` mostrando registros en estado `ACTIVA` y `CANCELADA`, la ventana de 60 días y los motivos tipados, sin exponer ninguna `restricted_note`.*
>
> **[MARCADOR DE CAPTURA — `CAP-07B`: Modal de Creación/Edición o Cancelación de Indisponibilidad (`/app/indisponibilidades`)]**
> *Insertar aquí la captura manual con `UnavailabilityModal` o `UnavailabilityCancelModal` abierto (manteniendo vacío o con texto genérico de prueba el campo de nota restringida).*

---

### Ficha 08 — Acceso restringido (`/acceso-restringido`)

- **Objetivo:** Informar de manera clara y segura que el rol del usuario autenticado no cuenta con autorización para visualizar la sección solicitada, ofreciendo retorno inmediato a su página de inicio permitida.
- **Rol o condición de acceso verificada:** Renderizada automáticamente por [`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) (líneas 88–152) cuando:
  - Se navega directamente a `/acceso-restringido`.
  - Un `COLABORADOR` intenta acceder a `/app/inicio`, `/app/configuracion`, `/app/puestos`, `/app/equipo` o `/app/indisponibilidades`.
  - Un `SUPERVISOR` intenta acceder a `/app/configuracion`.
  - Un usuario sin `employeeId` ni rol `COLABORADOR` intenta abrir `/app/mi-horario`, o se introduce una ruta no reconocida.
- **Acciones principales:** Volver a la vista principal autorizada para el rol de la sesión activa.
- **Datos visibles:** Aviso institucional de acceso restringido dentro del `AppLayout` sin filtrar información de la pantalla bloqueada.
- **Validaciones o estados de interfaz verificados:** Evaluación síncrona e inmediata del rol de sesión en el cliente complementando el bloqueo `403 ACCESS_DENIED` del backend.
- **Componente / página fuente:** [`frontend/src/pages/forbidden/ForbiddenPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/forbidden/ForbiddenPage.tsx) y [`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) (líneas 88–152).
- **Estado de captura:** `pendiente de evidencia manual`

> **[MARCADOR DE CAPTURA — `CAP-08`: Pantalla de Acceso Restringido (`/acceso-restringido`)]**
> *Insertar aquí la captura manual mostrando `ForbiddenPage` cuando un rol sin privilegios administrativos intenta abrir una ruta restringida.*

---

## 5.4 Pantallas no implementadas y justificación de honestidad académica

En coherencia con [`docs/planning/30 - Auditoría Académica de Entrega 2 a 6.md`](file:///d:/Proyectos/TurnoShift/docs/planning/30%20-%20Auditor%C3%ADa%20Acad%C3%A9mica%20de%20Entrega%202%20a%206.md) y [`docs/context/STATE.md`](file:///d:/Proyectos/TurnoShift/docs/context/STATE.md), las siguientes cinco vistas **no deben presentarse como pantallas web terminadas** en la entrega actual:

| Vista / Interfaz | Clasificación exacta | Estado real en el repositorio | Por qué no debe presentarse como pantalla terminada |
| :--- | :--- | :--- | :--- |
| **1. Interfaz de Políticas Operativas (`I2.3`)** | **Pendiente en Frontend (`IMPLEMENTADO SOLO BACKEND/API`)** | El backend expone `PolicyController.java` (`/api/v1/policies/current`, `/history`, `/preview`, `/activate`) y las tablas `V15`–`V16` (`planning_policy_versions`), pero no existe ruta ni componente en `frontend/src/App.tsx`. | Porque aunque sus reglas, persistencia y endpoints REST existen y están probados en el servidor, el usuario aún no dispone de una pantalla gráfica en React para operarlas desde el navegador. |
| **2. Mi horario publicado (`/app/mi-horario` — `I3`)** | **Futura (`PROTOTIPO/ALCANCE FUTURO` con contenedor placeholder)** | La ruta `/app/mi-horario` existe en [`frontend/src/App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx) (líneas 132–149) y es la página de aterrizaje del rol `COLABORADOR`, pero renderiza [`PlaceholderPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/placeholders/PlaceholderPage.tsx). | Porque actualmente muestra un componente informativo de marcador de posición (*"Portal del Colaborador"*) sin calendario real ni consulta de turnos publicados, dado que el backend de `I3` aún no está implementado. |
| **3. Planificación semanal de turnos (`I3`)** | **Futura (`PROTOTIPO/ALCANCE FUTURO`, no implementada)** | Especificada a nivel de diseño técnico en [`Nota 28`](file:///d:/Proyectos/TurnoShift/docs/planning/28%20-%20Dise%C3%B1o%20T%C3%A9cnico%20I3%20-%20Planificaci%C3%B3n%20de%20Turnos.md) y [`Nota 29`](file:///d:/Proyectos/TurnoShift/docs/planning/29%20-%20Plan%20de%20Implementaci%C3%B3n%20I3.1-a%20-%20Persistencia%20y%20Migraciones.md) y como wireframe en [`Nota 08`](file:///d:/Proyectos/TurnoShift/docs/planning/08%20-%20Navegaci%C3%B3n,%20Pantallas%20y%20Flujos%20de%20Usuario.md) y [`Nota 09`](file:///d:/Proyectos/TurnoShift/docs/planning/09%20-%20Design%20System%20y%20Wireframes.md). | No existen aún las migraciones `V18`–`V19`, los endpoints de horarios ni la cuadrícula semanal en `frontend/`. Presentarla como terminada vulneraría la veracidad del informe. |
| **4. Publicación de horario (`I3`)** | **Futura (`PROTOTIPO/ALCANCE FUTURO`, no implementada)** | Diseñada técnicamente dentro del flujo de publicación e instantáneas inmutables de [`Nota 28`](file:///d:/Proyectos/TurnoShift/docs/planning/28%20-%20Dise%C3%B1o%20T%C3%A9cnico%20I3%20-%20Planificaci%C3%B3n%20de%20Turnos.md). | Depende de la construcción del motor de validación y persistencia de horarios semanales (`I3`). Su lugar documental corresponde al Capítulo 3 (Diseño) y Capítulo 4 (Prototipos). |
| **5. Intercambios de turno (`I4`)** | **Futura (`PROTOTIPO/ALCANCE FUTURO`, no implementada)** | Definida como alcance objetivo del MVP en `Notas 04, 05, 07, 08 y 10`, sin diseño técnico de implementación aprobado ni código. | Depende de que existan primero horarios publicados (`I3`) sobre los cuales solicitar el cambio entre colaboradores elegibles. |

---

## 5.5 Secuencia recomendada de capturas para la entrega

Para tomar las evidencias visuales en un único recorrido ordenado y reproducible sobre el entorno local integrado (`SQL Server 2022` + `Backend :8081` + `Frontend :3000`), se establece la siguiente secuencia operativa:

| Orden | Cuenta / Rol a utilizar | Ruta en navegador | Interacción a demostrar en la captura | Datos que deben ocultarse o protegerse obligatoriamente |
| :--- | :--- | :--- | :--- | :--- |
| **Paso 1** | Público (sin sesión) | `/setup` | Mostrar la pantalla de configuración inicial de contraseña (`SetupPage.tsx`) en estado limpio o de enlace inválido (`422`), explicando el flujo de primer acceso. | **Ocultar por completo cualquier token real (`#token=...`) en la URL, consola o portapapeles, y mantener las contraseñas con máscara (`••••••••`).** |
| **Paso 2** | Público → `ADMINISTRADOR` (`admin.demo@turnoshift.local`) | `/acceso` | Ingresar el código de organización `DEMO-2026` y el correo de demostración con la contraseña oculta antes de pulsar el botón de ingreso. | **Mantener el conmutador de contraseña en modo oculto (`type="password"`); no mostrar contraseñas reales ni correos personales.** |
| **Paso 3** | `ADMINISTRADOR` | `/app/inicio` | Mostrar la vista de aterrizaje (`HomePage.tsx`) con la cabecera indicando `Café Andino S.A.C.`, zona horaria `America/Lima`, sede `PRINCIPAL` y tarjetas operativas. | Ningún secreto expuesto; verificar que solo aparezcan nombres ficticios del caso de estudio. |
| **Paso 4** | `ADMINISTRADOR` | `/app/configuracion` | Mostrar el formulario de `ConfigurationPage.tsx` con el nombre de la organización, el nombre de la sede activa y el `timezoneId` en solo lectura, y guardar una edición con confirmación exitosa. | No abrir el inspector de red mostrando cookies `TSESSIONID` ni cabeceras `X-CSRF-TOKEN`. |
| **Paso 5** | `ADMINISTRADOR` | `/app/puestos` | Mostrar el listado de puestos operativos definidos para el piloto y abrir `PositionModal` creando o editando un puesto. | Usar únicamente códigos y nombres operativos del caso **Café Andino S.A.C.** |
| **Paso 6** | `ADMINISTRADOR` | `/app/equipo` | Mostrar el directorio de colaboradores (`EmployeesPage.tsx`) filtrando por puesto activo, y abrir `EmployeeModal` o `EmployeeStatusModal` (`deactivation-preview`). | **Usar solo nombres y códigos ficticios (ej. `COL-001`); recordar que la vista ya excluye correos, teléfonos y fotos.** |
| **Paso 7** | `ADMINISTRADOR` o `SUPERVISOR` | `/app/indisponibilidades` | Mostrar la tabla de indisponibilidades (`UnavailabilitiesPage.tsx`) con registros `ACTIVA` y `CANCELADA`, el bloqueo `RN-013` en periodos ya iniciados y el modal `UnavailabilityModal` o `UnavailabilityCancelModal`. | **Ocultar o dejar en blanco cualquier contenido en el campo `restricted_note` (`RN-014`) si se captura el modal abierto.** |
| **Paso 8** | `SUPERVISOR` | `/app/puestos` o `/app/equipo` | Demostrar el modo consulta del supervisor: distintivo `"Consulta"` en `AppSidebar`, banner de supervisión y ausencia de botones de mutación. | Mantener datos ficticios del entorno `DEMO-2026`. |
| **Paso 9** | `COLABORADOR` (o `SUPERVISOR` navegando a `/app/configuracion`) | `/app/mi-horario` y `/acceso-restringido` | Mostrar primero el contenedor placeholder honesto en `/app/mi-horario` (`PlaceholderPage.tsx`) y luego la pantalla `ForbiddenPage.tsx` al intentar acceder a `/app/configuracion`. | No presentar `/app/mi-horario` como horario real publicado, sino como vista placeholder del portal del colaborador. |

---

## 5.6 Matriz pantalla → requisito/flujo → estado → evidencia

| Pantalla Web y Ruta (`frontend/src/App.tsx`) | Requisito (`1.9` / `1.10`) y Flujo (`Cap. 3`) | Clasificación de Estado | Evidencia de Código Fuente + Código de Captura |
| :--- | :--- | :--- | :--- |
| **1. Acceso empresarial**<br>`/acceso` | `RF-01`, `RNF-01`, `RNF-02`, `RNF-06`, `RNF-07`<br>*Flujo 2: Inicio de sesión* | **Funcional** (`IMPLEMENTADO CON FRONTEND` — `F1.4`) | [`LoginPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/login/LoginPage.tsx), [`authApi.ts`](file:///d:/Proyectos/TurnoShift/frontend/src/api/authApi.ts)<br>Captura: `CAP-01` (*pendiente de evidencia manual*) |
| **2. Configuración inicial**<br>`/setup#token=...` | `RF-01`, `RF-04`, `RNF-01`, `RNF-03`<br>*Flujo 1: Incorporación inicial* | **Funcional** (`IMPLEMENTADO CON FRONTEND` — `F1.5`) | [`SetupPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/setup/SetupPage.tsx), [`authApi.ts`](file:///d:/Proyectos/TurnoShift/frontend/src/api/authApi.ts)<br>Captura: `CAP-02` (*pendiente de evidencia manual*) |
| **3. Inicio operativo**<br>`/app/inicio` | `RF-01`, `RNF-02`, `RNF-03`<br>*Flujo 2: Aterrizaje de sesión* | **Funcional** (`IMPLEMENTADO CON FRONTEND` — `F1.3`) | [`HomePage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/home/HomePage.tsx), [`AppLayout.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/components/layout/AppLayout.tsx)<br>Captura: `CAP-03` (*pendiente de evidencia manual*) |
| **4. Configuración organizacional**<br>`/app/configuracion` | `RF-02 (a)`, `RNF-04`, `RNF-08`<br>*Flujo 3: Administración de organización (`I2.1`)* | **Funcional** (`IMPLEMENTADO CON FRONTEND` — `F2.2`) | [`ConfigurationPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/configuration/ConfigurationPage.tsx), [`organizationApi.ts`](file:///d:/Proyectos/TurnoShift/frontend/src/api/organizationApi.ts)<br>Captura: `CAP-04` (*pendiente de evidencia manual*) |
| **5. Catálogo de puestos**<br>`/app/puestos` | `RF-03`, `RNF-03`, `RNF-04`, `RNF-08`<br>*Flujo 4: Gestión de puestos (`I2.1`)* | **Funcional** (`IMPLEMENTADO CON FRONTEND` — `F2.3`) | [`PositionsPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/positions/PositionsPage.tsx), [`positionApi.ts`](file:///d:/Proyectos/TurnoShift/frontend/src/api/positionApi.ts)<br>Capturas: `CAP-05A`, `CAP-05B` (*pendientes de evidencia manual*) |
| **6. Directorio de equipo**<br>`/app/equipo` | `RF-04`, `RNF-03`, `RNF-04`, `RNF-08`<br>*Flujo 4: Gestión de colaboradores (`I2.2`)* | **Funcional** (`IMPLEMENTADO CON FRONTEND` — `F3.2`–`F3.3`) | [`EmployeesPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/employees/EmployeesPage.tsx), [`employeeApi.ts`](file:///d:/Proyectos/TurnoShift/frontend/src/api/employeeApi.ts)<br>Captura: `CAP-06` (*pendiente de evidencia manual*) |
| **7. Gestión de indisponibilidades**<br>`/app/indisponibilidades` | `RF-05` (`ACTIVA`/`CANCELADA`, `RN-012`, `RN-013`, `RN-014`)<br>*Flujo 5: Indisponibilidades (`I2.4`)* | **Funcional** (`IMPLEMENTADO CON FRONTEND` — `F4.2`–`F4.3`) | [`UnavailabilitiesPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/unavailabilities/UnavailabilitiesPage.tsx), [`unavailabilityApi.ts`](file:///d:/Proyectos/TurnoShift/frontend/src/api/unavailabilityApi.ts)<br>Capturas: `CAP-07A`, `CAP-07B` (*pendientes de evidencia manual*) |
| **8. Acceso restringido**<br>`/acceso-restringido` | `RF-01`, `RNF-03`<br>*Control transversal RBAC en SPA* | **Funcional** (`IMPLEMENTADO CON FRONTEND` — `F1.3`) | [`ForbiddenPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/forbidden/ForbiddenPage.tsx), [`App.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/App.tsx)<br>Captura: `CAP-08` (*pendiente de evidencia manual*) |
| **9. Interfaz de políticas operativas**<br>*(Sin ruta en `App.tsx`)* | `RF-02 (b)` (`I2.3` Políticas Operativas)<br>*Flujo 3 (solo vía API `/api/v1/policies/*`)* | **Pendiente de pantalla frontend** (`IMPLEMENTADO SOLO BACKEND/API`) | `PolicyController.java`, migraciones `V15`–`V16`<br>*Sin captura de UI (documentado como brecha frontend)* |
| **10. Mi horario publicado, Planificación semanal, Publicación e Intercambios**<br>`/app/mi-horario` (Placeholder) | `RF-06` a `RF-12` (`I3` e `I4`)<br>*Flujos 6 y 7 (Alcance objetivo del MVP)* | **Futuras** (`PROTOTIPO/ALCANCE FUTURO`, no implementadas) | [`PlaceholderPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/placeholders/PlaceholderPage.tsx), [`Nota 28`](file:///d:/Proyectos/TurnoShift/docs/planning/28%20-%20Dise%C3%B1o%20T%C3%A9cnico%20I3%20-%20Planificaci%C3%B3n%20de%20Turnos.md), [`Nota 08`](file:///d:/Proyectos/TurnoShift/docs/planning/08%20-%20Navegaci%C3%B3n,%20Pantallas%20y%20Flujos%20de%20Usuario.md)<br>Captura placeholder opcional: `CAP-09` |

---

## 5.7 Checklist final para insertar las capturas reales posteriormente

Antes de exportar la versión final del informe académico con las imágenes integradas en los marcadores `[MARCADOR DE CAPTURA — CAP-01 a CAP-08]`, se debe verificar el cumplimiento de la siguiente lista de comprobación:

- [ ] **1. Entorno real levantado:** Todas las capturas fueron tomadas sobre la SPA real en ejecución (`http://localhost:3000`) conectada al backend Spring Boot (`:8081`) y a SQL Server 2022 (`turnoshift_dev`), sin usar montajes gráficos externos.
- [ ] **2. Rutas exactas verificadas:** Las capturas corresponden exactamente a `/acceso`, `/setup`, `/app/inicio`, `/app/configuracion`, `/app/puestos`, `/app/equipo`, `/app/indisponibilidades` y `/acceso-restringido`.
- [ ] **3. Cero exposición de tokens o contraseñas:** En `/acceso` y `/setup`, los campos de contraseña muestran únicamente puntos (`••••••••`) y en ninguna barra de direcciones ni texto aparece un token real `#token=...`.
- [ ] **4. Protección de `restricted_note` (`RN-014`):** En las capturas de `/app/indisponibilidades`, la tabla no muestra notas restringidas y, si aparece abierto `UnavailabilityModal`, el campo de nota restringida está vacío o contiene un texto neutro de prueba.
- [ ] **5. Exactitud funcional en Indisponibilidades (`I2.4`):** Los pies de figura de `/app/indisponibilidades` describen el ciclo real **`ACTIVA` / `CANCELADA`** y la regla temporal `RN-013`, sin mencionar estados `APPROVED`/`REJECTED` ni bloqueo de traslapes.
- [ ] **6. Distinción honesta de `I2.3`, `I3` e `I4`:** No se incluye ninguna imagen simulada como si existiera pantalla terminada de Políticas Operativas (`I2.3`), Planificación Semanal (`I3`), Publicación (`I3`) o Intercambios (`I4`). Si se incluye `/app/mi-horario`, su pie de figura indica expresamente que corresponde a [`PlaceholderPage.tsx`](file:///d:/Proyectos/TurnoShift/frontend/src/pages/placeholders/PlaceholderPage.tsx).
- [ ] **7. Terminología correcta:** En todos los títulos y descripciones de las capturas se denomina a la interfaz **"SPA web responsiva"** (en vista de escritorio o navegador móvil) y nunca *"aplicación móvil nativa"*.
