
## 1. Propósito

Definir el acuerdo entre frontend y backend de TurnoShift:

- Operaciones disponibles.
    
- Permisos.
    
- Datos de entrada y salida.
    
- Validaciones.
    
- Errores.
    
- Control de versiones.
    
- Reconocimiento de advertencias.
    
- Prevención de duplicados.
    
- Recuperación de resultados.
    
- Integraciones incluidas y excluidas.
    

Esta especificación corresponde al MVP utilizado por la aplicación web. No constituye una API pública para terceros.

**Estado:** diseño consolidado. OpenAPI, implementación y pruebas pendientes.

### 1.1. Cambios de esta revisión

Se integran:

1. Diferencia entre línea de borrador y asignación publicada.
    
2. Publicación de semanas vacías.
    
3. Motivos obligatorios por acción.
    
4. Reconocimiento de advertencias concretas.
    
5. Vista previa de aprobación de intercambios.
    
6. Activación controlada de políticas.
    
7. Resultados asociados a conjuntos de cambio.
    
8. Registro idempotente definido en el modelo de datos.
    
9. Comportamiento ante respuestas inciertas.
    
10. Correspondencia explícita entre contratos y persistencia.
    

Esta versión sustituye la anterior.

## 2. Documentos relacionados

- [[03 - Alcance MVP y Roadmap Comercial]]
    
- [[04 - Roles, Procesos y Reglas de Negocio]]
    
- [[05 - Requisitos y Casos de Uso]]
    
- [[06 - Arquitectura y Stack Tecnológico]]
    
- [[07 - Modelo de Datos y DER]]
    
- [[08 - Navegación, Pantallas y Flujos de Usuario]]
    
- [[09 - Design System y Wireframes]]
    
- [[11 - Backlog MVP y Plan de Implementación]]
    
- [[12 - Pruebas, Seguridad y Operación]]
    

Las reglas consolidadas del documento 04 definen el comportamiento. El documento 07 define su persistencia.

Un contrato no permite eludir ninguna de esas reglas.

## 3. Decisiones generales

|Aspecto|Decisión|
|---|---|
|Estilo|API HTTP con recursos y acciones explícitas|
|Prefijo|`/api/v1`|
|Formato habitual|JSON|
|Propiedades|Inglés, `camelCase`|
|Mensajes|Español|
|Identificadores de negocio|UUID representados como cadenas opacas|
|Sesión|Cookie y estado en servidor|
|Organización|Derivada de la sesión|
|Instantes|Fecha, hora y desplazamiento explícitos|
|Errores|Problem Details y códigos de aplicación|
|Concurrencia|Versiones esperadas y transacciones|
|Reintentos|Idempotencia persistente para comandos de negocio|
|Exportaciones|CSV autorizado|
|Facturación automática|Fuera del MVP|

Los identificadores y huellas abreviados de los ejemplos son ilustrativos. Las pruebas ejecutables utilizarán valores que cumplan sus formatos reales.

### 3.1. Alcance

Incluye acceso, configuración, usuarios, empleados, indisponibilidad, planificación, publicaciones, intercambios, reportes, auditoría y avisos.

Excluye asistencia, nómina, pagos automáticos, WhatsApp, optimización automática y API para integradores.

### 3.2. API y base de datos

La API no expone tablas directamente.

Ejemplos:

|   |   |
|---|---|
|API|Modelo|
|`employee`|`employees`|
|`templateId`|`shift_type_id`|
|`version`|`lock_version`|
|`draftVersion`|`schedule_drafts.lock_version`|
|`item.id`|`draft_assignments.id`|
|`assignmentId`|`assignments.id`|
|`revisionId`|`assignment_revisions.id`|
|`changeSetId`|`planning_change_sets.id`|

Los nombres de tabla no obligan a utilizar esos mismos nombres en rutas.

## 4. Convenciones

### 4.1. Rutas

Todas las rutas de esta nota se entienden bajo `/api/v1`.

Las rutas de pantalla, como `/app/mi-horario`, no son endpoints.

### 4.2. Métodos

|   |   |
|---|---|
|Método|Uso|
|GET|Consultar sin modificar el negocio|
|POST|Crear o ejecutar una acción|
|PATCH|Modificar campos expresamente permitidos|
|DELETE|Retirar elementos editables autorizados|

Las decisiones se expresan mediante acciones como `/publish`, `/approve` o `/cancel`.

No existe un cambio libre de estado mediante `PATCH status`.

Las convenciones se apoyan en la semántica HTTP; los contratos concretos son decisiones de TurnoShift. [Referencia: RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html).

### 4.3. Cabeceras

Para JSON:

```
Content-Type: application/json
Accept: application/json, application/problem+json
```

Para métodos protegidos contra CSRF:

```
X-CSRF-TOKEN: <token vigente>
```

Para comandos de negocio sujetos a idempotencia:

```
Idempotency-Key: <UUID de la intención>
```

En respuestas:

```
X-Request-ID: <identificador de diagnóstico>
```

No se utiliza `Authorization: Bearer`.

### 4.4. Tres clases de operación

|   |   |   |   |
|---|---|---|---|
|Clase|Ejemplos|CSRF|Idempotency-Key|
|Consulta GET|Listar empleados, consultar horario|No se exige para leer|No|
|Evaluación POST sin escritura|Validar, obtener vista previa|Sí|No|
|Comando de negocio|Crear, editar, publicar, aprobar|Sí|Sí|
|Acceso o credenciales|Login, logout, recuperación|Sí|No utiliza el registro genérico|

Marcar un aviso como leído es un comando de negocio. También debe ser seguro si se repite con otra clave: no cambia la primera fecha de lectura.

Las vistas previas no generan publicaciones, reservas ni decisiones confirmadas.

### 4.5. Respuestas individuales

Se devuelve el recurso o resultado directamente:

```
{
  "id": "employee_demo_ana",
  "code": "EMP-001",
  "displayName": "Ana",
  "positionId": "position_demo_barista",
  "active": true,
  "version": 3
}
```

No se añade `success: true` a todas las respuestas.

Las modificaciones deben devolver las versiones necesarias para continuar trabajando.

### 4.6. Listados

```
{
  "items": [],
  "page": 0,
  "size": 25,
  "totalItems": 0,
  "totalPages": 0
}
```

Reglas:

- Páginas desde cero.
    
- Tamaño predeterminado: 25.
    
- Máximo inicial: 100.
    
- Orden estable con identificador como desempate.
    
- Filtros y ordenamientos permitidos por contrato.
    
- Parámetros desconocidos o inválidos: error.
    

Las consultas semanales pueden devolver un conjunto completo acotado a la semana.

### 4.7. Campos permitidos

En `PATCH`:

- Omitido: conservar.
    
- `null`: borrar solo si está permitido.
    
- Desconocido o no editable: rechazar.
    

No se aceptan como autoridad del cliente:

- Organización.
    
- Actor.
    
- Autor de publicación.
    
- Totales.
    
- Estado interno.
    
- Huellas calculadas como si fueran datos de negocio.
    
- Permisos arbitrarios.
    

Las huellas y confirmaciones enviadas se contrastan con cálculos del servidor.

### 4.8. Versiones e identificadores

Las versiones son enteros no negativos dentro del rango seguro acordado para frontend y backend.

Las identidades se tratan como cadenas opacas.

Una versión no es una fecha y un identificador no expresa orden temporal.

## 5. Fechas, horas y periodos

### 5.1. Instantes

Ejemplo:

```
{
  "startsAt": "2026-08-31T08:00:00-05:00",
  "endsAt": "2026-08-31T16:00:00-05:00"
}
```

El backend almacena UTC y devuelve instantes normalizados:

```
{
  "startsAt": "2026-08-31T13:00:00Z",
  "endsAt": "2026-08-31T21:00:00Z",
  "timeZone": "America/Lima"
}
```

Se admite enviar un instante en UTC. No se rechaza un valor con `Z` por tener una representación distinta de la zona de la organización.

Cuando se envía una hora local con desplazamiento, se comprueba que esa combinación corresponda a la zona configurada.

No se aceptan horas locales ambiguas sin resolución explícita.

### 5.2. Precisión

La edición de asignaciones e indisponibilidades tendrá precisión de minutos en el MVP.

Segundos o fracciones no admitidos se rechazan, no se redondean silenciosamente.

Las fechas técnicas, como creación, vencimiento de sesión o auditoría, pueden conservar mayor precisión.

### 5.3. Semanas

```
weekStart=2026-08-31
```

Debe ser lunes en el calendario de la organización.

Una asignación pertenece a la semana en que comienza, aunque termine en otra.

### 5.4. Intervalos y duraciones

Intervalo: **inicio incluido y fin excluido**, `[inicio, fin)`.

Duraciones oficiales:

```
{
  "plannedMinutes": 480
}
```

El frontend presenta los valores, pero no reemplaza el cálculo del servidor.

No se descuentan pausas no modeladas ni se presentan minutos programados como trabajados.

## 6. Autenticación y autorización

### 6.1. Sesión

Cookie propuesta:

- `HttpOnly`.
    
- `Secure` en producción.
    
- `SameSite=Lax`.
    
- Alcance restringido.
    

El frontend no almacena la credencial de sesión en `localStorage`.

Organización y usuario se determinan desde la sesión.

### 6.2. Permisos

Abreviaturas:

- **A:** administrador.
    
- **S:** supervisor.
    
- **Personal:** usuario activo vinculado a empleado activo.
    
- **Participante:** solicitante o destinatario.
    
- **Independiente:** responsable que no participa en el intercambio.
    

Cada operación comprueba:

1. Sesión.
    
2. Organización activa.
    
3. Usuario activo.
    
4. Permiso vigente.
    
5. Pertenencia del recurso.
    
6. Condiciones de negocio.
    

Las facultades personales de A/S no eliminan las restricciones de independencia.

### 6.3. CSRF

Secuencia propuesta:

1. Obtener token con `GET /auth/csrf`.
    
2. Mantenerlo en memoria.
    
3. Enviarlo en las operaciones correspondientes.
    
4. Obtener uno nuevo tras iniciar o cerrar sesión.
    

La integración SPA debe contemplar la renovación del token después de autenticación y logout. JSON no es un motivo para desactivar la protección. [Referencia: Spring Security, CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

### 6.4. Acceso restringido

- `401`: autenticación ausente o inválida.
    
- `403`: acción no permitida o protección CSRF inválida.
    
- `404`: recurso inexistente o no revelable para ese actor.
    

No se revela la existencia de recursos ajenos mediante mensajes distintos.

## 7. Acceso y recuperación

### 7.1. Endpoints

|   |   |
|---|---|
|Método y ruta|Resultado|
|`GET /auth/csrf`|Token CSRF|
|`POST /auth/login`|Inicia sesión|
|`GET /auth/session`|Identidad y contexto vigentes|
|`POST /auth/logout`|Cierra sesión|
|`POST /auth/change-password`|Cambia contraseña e invalida sesiones|
|`POST /auth/password-reset/inspect`|Inspecciona enlace sin consumir|
|`POST /auth/password-reset/complete`|Establece contraseña y consume enlace|

La ayuda de recuperación es contenido de interfaz. No incorpora un sistema de tickets.

### 7.2. Login

Entrada:

```
{
  "organizationCode": "CAFE-DEMO",
  "email": "ana@example.com",
  "password": "<contraseña>"
}
```

Salida:

```
{
  "user": {
    "id": "user_demo_ana",
    "displayName": "Ana",
    "role": "COLABORADOR",
    "employeeId": "employee_demo_ana"
  },
  "organization": {
    "id": "organization_demo",
    "displayName": "Café Demo",
    "timeZone": "America/Lima"
  },
  "landingPage": "/app/mi-horario"
}
```

La credencial se entrega mediante cookie, no en el JSON.

Una cuenta sin contraseña establecida no inicia sesión con una contraseña temporal inventada.

### 7.3. Contraseña

Cambio autenticado:

```
{
  "currentPassword": "<contraseña actual>",
  "newPassword": "<nueva contraseña>"
}
```

Establecimiento mediante enlace:

```
{
  "token": "<secreto del enlace>",
  "newPassword": "<nueva contraseña>"
}
```

La confirmación repetida de contraseña puede resolverse en la interfaz; no necesita persistirse.

Después del cambio se invalida el acceso anterior y se solicita nueva autenticación según el flujo definido.

### 7.4. Recuperación y alta inicial

El enlace:

- Tiene un solo uso.
    
- Caduca según la política, inicialmente 30 minutos.
    
- Se almacena como hash.
    
- No se consume al inspeccionarlo.
    
- Se revoca al emitir uno nuevo.
    
- No aparece en registros ni analítica.
    

El token puede viajar en el fragmento del enlace y enviarse después en el cuerpo al backend.

`inspect` devuelve únicamente validez y propósito necesarios para presentar el formulario. No expone perfiles ni datos empresariales.

Un enlace inválido, utilizado, revocado o vencido no habilita ningún cambio.

### 7.5. Respuestas inciertas de seguridad

Estas operaciones no guardan secretos en `api_operations`.

Ante incertidumbre:

- Login: consultar sesión.
    
- Logout: comprobar sesión antes de continuar.
    
- Cambio de contraseña: autenticar nuevamente.
    
- Recuperación: inspeccionar el enlace o iniciar un nuevo procedimiento asistido.
    
- Emisión de enlace: si se necesita otro, emitirlo conscientemente y revocar el anterior.
    

No se promete recuperar del historial una contraseña o un enlace secreto perdido.

## 8. Organización, usuarios y equipo

### 8.1. Configuración

|   |   |   |
|---|---|---|
|Método y ruta|Permiso|Uso|
|`GET /organization`|A|Configuración|
|`PATCH /organization`|A|Modificar campos permitidos|
|`POST /organization/timezone-preview`|A|Revisar cambio de zona|
|`GET /site`|A/S|Consultar sede|
|`PATCH /site`|A|Modificar datos permitidos|
|`GET /positions`|A/S|Consultar puestos|
|`POST /positions`|A|Crear puesto|
|`PATCH /positions/{id}`|A|Modificar o desactivar|
|`GET /shift-templates`|A/S|Consultar plantillas|
|`POST /shift-templates`|A|Crear plantilla|
|`PATCH /shift-templates/{id}`|A|Modificar o desactivar|

No existe alta de una segunda sede desde la API del cliente.

La información mínima de empresa para el colaborador se obtiene de su sesión.

### 8.2. Cambio de zona

La vista previa recibe:

```
{
  "expectedVersion": 2,
  "timeZone": "America/Lima"
}
```

Devuelve:

- Si el cambio está permitido.
    
- Existencia de publicaciones.
    
- Borradores afectados.
    
- Bloqueos por pertenencia semanal.
    
- Huella del impacto.
    

Cuando `PATCH /organization` cambia realmente la zona, exige versión y huella de revisión:

```
{
  "expectedVersion": 2,
  "timeZone": "America/Lima",
  "validationFingerprint": "<huella>"
}
```

Reglas:

- Después de la primera publicación: bloqueado.
    
- No se cambian silenciosamente los instantes.
    
- Si una línea quedaría fuera de su semana: bloqueado.
    
- Cambios concurrentes obligan a revisar.
    
- Se actualizan coherentemente las versiones afectadas.
    

Esta evaluación utiliza el patrón de huellas, pero no crea un `planning_change_sets`: ese registro se reserva a los tipos definidos en el documento 07.

### 8.3. Usuarios

|   |   |   |
|---|---|---|
|Método y ruta|Permiso|Uso|
|`GET /users`|A|Listado|
|`GET /users/{id}`|A|Detalle|
|`POST /users`|A|Crear cuenta|
|`PATCH /users/{id}`|A|Editar datos permitidos|
|`POST /users/{id}/activate`|A|Activar|
|`POST /users/{id}/deactivate`|A|Desactivar|
|`POST /users/{id}/recovery-links`|A|Emitir enlace asistido|

Creación:

```
{
  "email": "ana@example.com",
  "displayName": "Ana",
  "role": "COLABORADOR",
  "employeeId": "employee_demo_ana"
}
```

No incluye contraseña ni crea automáticamente un empleado.

La creación de cuenta es idempotente. La emisión posterior del enlace secreto utiliza el procedimiento específico de seguridad.

`recovery-links` recibe versión y propósito:

- `INITIAL_SETUP`.
    
- `PASSWORD_RESET`.
    

Devuelve el enlace únicamente al administrador autorizado, con política de no almacenamiento en caché. Su emisión no se registra como respuesta recuperable con el token en `api_operations`.

### 8.4. Cambios administrativos

`PATCH /users/{id}` admite únicamente los campos autorizados de identidad, rol y vínculo, además de `expectedVersion`.

El estado se modifica mediante `/activate` y `/deactivate`.

Se comprueba:

- Último administrador activo.
    
- Vínculo dentro de la organización.
    
- Una cuenta por empleado.
    
- Ausencia de solicitudes pendientes para cambiar el vínculo.
    
- Invalidación de solicitudes afectadas al desactivar la cuenta.
    
- Aplicación de permisos actuales en las siguientes peticiones.
    

### 8.5. Empleados

|   |   |   |
|---|---|---|
|Método y ruta|Permiso|Uso|
|`GET /employees`|A/S|Listado autorizado|
|`GET /employees/{id}`|A/S|Detalle|
|`POST /employees`|A|Crear|
|`PATCH /employees/{id}`|A|Modificar|
|`POST /employees/{id}/deactivation-preview`|A|Consultar compromisos|
|`POST /employees/{id}/activate`|A|Activar|
|`POST /employees/{id}/deactivate`|A|Desactivar|

La ruta `deactivation-preview` sustituye la denominación ambigua `activation-check` del borrador anterior.

Creación:

```
{
  "code": "EMP-001",
  "displayName": "Ana",
  "positionId": "position_demo_barista"
}
```

La sede se obtiene del contexto autorizado del MVP.

El empleado puede planificarse sin cuenta.

La vista previa de desactivación no elimina compromisos ni garantiza que sigan iguales al confirmar.

### 8.6. Versiones de edición

Salvo excepciones expresas, editar, activar, desactivar o cancelar un recurso mutable requiere `expectedVersion`.

Crear un recurso no requiere una versión de ese recurso todavía inexistente, pero sí idempotencia y comprobación de las condiciones actuales de la organización.

El supervisor recibe una proyección limitada; no los mismos campos administrativos que A.

## 9. Indisponibilidad, políticas y advertencias

### 9.1. Indisponibilidad

|   |   |
|---|---|
|Método y ruta|Permiso|
|`GET /unavailability`|A/S|
|`POST /unavailability`|A/S|
|`PATCH /unavailability/{id}`|A/S|
|`POST /unavailability/{id}/cancel`|A/S|

Entrada:

```
{
  "employeeId": "employee_demo_ana",
  "startsAt": "2026-09-04T08:00:00-05:00",
  "endsAt": "2026-09-04T12:00:00-05:00",
  "reason": "Motivo privado autorizado"
}
```

`reason` corresponde a la nota restringida del modelo. No se comparte con compañeros.

Crear o modificar se rechaza si contradice turnos publicados.

Cancelar conserva historial.

### 9.2. Advertencia común

Las vistas previas de publicación, aprobación y política utilizan:

```
{
  "code": "WEEKLY_PLANNED_MINUTES_EXCEEDED",
  "warningFingerprint": "<huella de esta advertencia>",
  "employeeId": "employee_demo_ana",
  "employeeName": "Ana",
  "periodStart": "2026-08-31",
  "periodEndExclusive": "2026-09-07",
  "timeZone": "America/Lima",
  "plannedMinutes": 2520,
  "thresholdMinutes": 2400,
  "message": "Se supera el umbral semanal configurado."
}
```

Los valores son un ejemplo de cálculo, no parámetros legales ni valores aprobados para el piloto.

El cliente reconoce la advertencia enviando:

```
{
  "warningAcknowledgements": [
    {
      "warningFingerprint": "<huella de esta advertencia>"
    }
  ]
}
```

No devuelve minutos, umbrales o actores como autoridad.

El servidor:

- Recalcula.
    
- Comprueba que las huellas correspondan al resultado actual.
    
- Rechaza faltantes, duplicadas o desconocidas.
    
- Registra sus propios valores calculados.
    
- Exige justificación cuando existen advertencias.
    

Un array vacío es correcto únicamente si no hay advertencias que reconocer.

### 9.3. Políticas

|   |   |   |
|---|---|---|
|Método y ruta|Permiso|Uso|
|`GET /policies/current`|A/S|Política vigente|
|`GET /policies/history`|A|Versiones|
|`POST /policies/preview`|A|Evaluar propuesta|
|`POST /policies/activate`|A|Activar versión|

Sin política configurada, `GET /policies/current` devuelve `200` con:

```
{
  "policy": null
}
```

La ausencia es un estado de configuración conocido, no un error de conexión.

### 9.4. Vista previa de política

Entrada:

```
{
  "expectedCurrentPolicyVersionId": "policy_demo_1",
  "minimumRestMinutes": 660,
  "weeklyWarningMinutes": 2400
}
```

En la configuración inicial, la referencia esperada es `null`.

Salida:

- Política actual y valores propuestos.
    
- `canProceed`.
    
- `blockers`.
    
- `warnings`.
    
- `validationFingerprint`.
    
- `requirements.reasonRequired`.
    

`canProceed` indica ausencia de bloqueos en ese momento. No significa que el comando ya esté autorizado a omitir confirmaciones.

La política candidata todavía no tiene que existir en la base. Su huella utiliza los valores propuestos y la referencia de origen, no un identificador futuro inventado.

### 9.5. Activación

```
{
  "expectedCurrentPolicyVersionId": "policy_demo_1",
  "minimumRestMinutes": 660,
  "weeklyWarningMinutes": 2400,
  "validationFingerprint": "<huella de la propuesta>",
  "reason": "Ajuste operativo revisado",
  "warningAcknowledgements": []
}
```

La activación:

- Es inmediata.
    
- Exige motivo.
    
- Bloquea descansos incompatibles con compromisos futuros.
    
- Permite advertencias de umbral reconocidas.
    
- Revalida bajo coordinación transaccional.
    
- Crea versión y conjunto de cambio.
    
- Actualiza la política vigente.
    
- Registra evidencia e idempotencia.
    

Respuesta:

```
{
  "changeSetId": "change_demo_policy_2",
  "policy": {
    "id": "policy_demo_2",
    "number": 2,
    "minimumRestMinutes": 660,
    "weeklyWarningMinutes": 2400,
    "effectiveAt": "2026-08-30T15:00:00Z"
  }
}
```

No modifica publicaciones históricas ni revalida automáticamente como aprobados los borradores pendientes.

## 10. Planificación semanal

### 10.1. Endpoints

|   |   |   |
|---|---|---|
|Método y ruta|Permiso|Uso|
|`GET /schedules?weekStart=...`|A/S|Estado semanal|
|`GET /schedules/{id}/publications`|A/S|Historial|
|`GET /publications/{id}`|A/S|Publicación de solo lectura|
|`POST /schedule-drafts`|A/S|Crear borrador|
|`GET /schedule-drafts/{id}`|A/S|Consultar|
|`POST /schedule-drafts/{id}/assignments`|A/S|Agregar línea|
|`PATCH /schedule-drafts/{id}/assignments/{itemId}`|A/S|Editar línea|
|`DELETE /schedule-drafts/{id}/assignments/{itemId}`|A/S|Retirar línea|
|`POST /schedule-drafts/{id}/validate`|A/S|Evaluar reglas|
|`POST /schedule-drafts/{id}/publication-preview`|A/S|Revisar publicación|
|`POST /schedule-drafts/{id}/publish`|A/S|Publicar|
|`POST /schedule-drafts/{id}/discard`|A/S|Descartar|

### 10.2. Estado semanal

La consulta devuelve:

- `weekStart`.
    
- `timeZone`.
    
- `scheduleId`, nulo si todavía no existe.
    
- Publicación vigente, si existe.
    
- Resumen del borrador abierto, si existe.
    

No crea una planificación mediante GET.

Si no hay datos para una semana válida, devuelve un estado vacío, no un error genérico.

### 10.3. Crear borrador

```
{
  "weekStart": "2026-08-31",
  "basePublicationId": "publication_demo_3"
}
```

Para la primera publicación, `basePublicationId` es `null`.

Se rechaza:

- Segundo borrador abierto.
    
- Base que ya no es vigente.
    
- Publicación de otra empresa o semana.
    
- Semana inválida.
    

La respuesta `201` incluye el borrador y su versión inicial.

### 10.4. Identidad de las líneas

- `item.id` identifica `draft_assignments`.
    
- `assignmentId` identifica una asignación publicada.
    
- `baseRevisionId` identifica la revisión de origen.
    
- Una línea nueva tiene `assignmentId` y `baseRevisionId` nulos.
    

El cliente no convierte una línea nueva en una asignación existente enviando esos campos.

Las referencias se asignan por el servidor.

### 10.5. Agregar turno

```
{
  "expectedDraftVersion": 4,
  "employeeId": "employee_demo_ana",
  "startsAt": "2026-08-31T08:00:00-05:00",
  "endsAt": "2026-08-31T16:00:00-05:00",
  "templateId": null
}
```

El puesto se deriva y valida en servidor.

Respuesta:

```
{
  "draftId": "draft_demo_1",
  "draftVersion": 5,
  "item": {
    "id": "draft_item_demo_1",
    "assignmentId": null,
    "baseRevisionId": null,
    "employeeId": "employee_demo_ana",
    "positionId": "position_demo_barista",
    "startsAt": "2026-08-31T13:00:00Z",
    "endsAt": "2026-08-31T21:00:00Z",
    "plannedMinutes": 480
  }
}
```

La plantilla es opcional. No reemplaza los instantes explícitos de la asignación.

### 10.6. Editar, retirar y descartar

Editar exige `expectedDraftVersion` y solo admite campos operativos autorizados.

Retirar:

```
DELETE /schedule-drafts/{draftId}/assignments/{itemId}?expectedDraftVersion=5
```

Además requiere CSRF e idempotencia.

Devuelve `200` con la nueva `draftVersion`, para que el cliente no siga editando sobre la anterior.

Descartar recibe:

```
{
  "expectedDraftVersion": 6
}
```

No cambia la publicación vigente.

El servidor comprueba que la línea pertenezca al borrador de la ruta.

### 10.7. Validar

Entrada:

```
{
  "expectedDraftVersion": 5
}
```

Respuesta:

```
{
  "draftId": "draft_demo_1",
  "draftVersion": 5,
  "basePublicationId": "publication_demo_3",
  "policyVersionId": "policy_demo_1",
  "canPublish": false,
  "blockers": [
    {
      "code": "SHIFT_OVERLAP",
      "message": "Ana tiene turnos superpuestos.",
      "draftItemIds": ["draft_item_demo_1"],
      "relatedAssignmentIds": ["assignment_demo_8"]
    }
  ],
  "warnings": []
}
```

La validación completada devuelve `200`, aunque encuentre bloqueos. Un intento de publicar incumpliendo reglas devuelve un error.

Solo se revelan referencias autorizadas.

### 10.8. Guardado frente a publicación

Al guardar se rechazan datos imposibles o prohibidos:

- Intervalo inválido.
    
- Recurso ajeno.
    
- Colaborador inactivo para una nueva asignación.
    
- Puesto incompatible.
    
- Cambio prohibido de turno iniciado.
    
- Versión antigua.
    

Pueden permanecer en el borrador, identificados para corregir:

- Superposición.
    
- Indisponibilidad.
    
- Descanso insuficiente.
    
- Exceso del umbral.
    

Los tres primeros bloquean publicar. El umbral requiere reconocimiento y motivo.

### 10.9. Vista previa de publicación

Entrada:

```
{
  "expectedDraftVersion": 5,
  "expectedBasePublicationId": "publication_demo_3"
}
```

Salida:

- Borrador y versión.
    
- Publicación base y vigente.
    
- Política aplicada.
    
- Asignaciones agregadas, modificadas y retiradas.
    
- Colaboradores afectados.
    
- `isEmptySchedule`.
    
- `blockers`.
    
- `warnings`.
    
- Solicitudes que quedarían invalidadas.
    
- `validationFingerprint`.
    
- Requisitos de confirmación.
    

Los detalles que debe revisar el usuario no pueden omitirse silenciosamente por truncamiento.

Ejemplo de requisitos:

```
{
  "requirements": {
    "reasonRequired": true,
    "emptyScheduleConfirmationRequired": false
  }
}
```

La huella corresponde al contenido e impactos revisados, no a cualquier confirmación posterior.

### 10.10. Publicar

```
{
  "expectedDraftVersion": 5,
  "expectedBasePublicationId": "publication_demo_3",
  "validationFingerprint": "<huella de la vista previa>",
  "reason": "Ajuste de cobertura",
  "confirmEmptySchedule": false,
  "warningAcknowledgements": []
}
```

Motivo:

|   |   |
|---|---|
|Caso|Obligatorio|
|Primera publicación con turnos y sin advertencias|No|
|Advertencias reconocidas|Sí|
|Semana vacía|Sí|
|Reemplazo mediante borrador|Sí|

`confirmEmptySchedule` debe ser `true` cuando la publicación resultante no tenga asignaciones.

Confirmar vacío no permite retirar turnos iniciados.

La transacción incluye:

- Validaciones actuales.
    
- Conjunto de cambio.
    
- Identidades y revisiones.
    
- Publicación.
    
- Puntero vigente.
    
- Cierre del borrador.
    
- Invalidaciones y reservas.
    
- Reconocimientos.
    
- Auditoría y outbox.
    
- Resultado idempotente.
    

Respuesta:

```
{
  "changeSetId": "change_demo_4",
  "draft": {
    "id": "draft_demo_1",
    "status": "PUBLICADO",
    "version": 6
  },
  "publication": {
    "id": "publication_demo_4",
    "number": 4,
    "origin": "REVISION",
    "isEmpty": false,
    "publishedAt": "2026-08-30T15:30:00Z"
  },
  "invalidatedSwapIds": []
}
```

La respuesta exitosa se emite después del commit.

### 10.11. Consulta de una publicación

Incluye:

- Identificador y número.
    
- Planificación y semana.
    
- Origen.
    
- Política.
    
- Zona.
    
- Conjunto de cambio.
    
- Autor.
    
- Motivo.
    
- Fecha.
    
- Asignaciones.
    
- Evidencia de advertencias según permisos.
    

Autor y motivo se obtienen del conjunto de cambio del documento 07.

No se habilita edición o eliminación de publicaciones.

## 11. Horario personal

|   |   |
|---|---|
|Método y ruta|Permiso|
|`GET /me/schedule?weekStart=...`|Personal|
|`GET /me/next-assignment`|Personal|
|`GET /me/assignments/{id}`|Personal|

Respuesta semanal:

```
{
  "weekStart": "2026-08-31",
  "timeZone": "America/Lima",
  "publication": {
    "id": "publication_demo_4",
    "number": 4
  },
  "assignments": [
    {
      "id": "assignment_demo_ana",
      "revisionId": "revision_demo_ana_2",
      "startsAt": "2026-08-31T13:00:00Z",
      "endsAt": "2026-08-31T21:00:00Z",
      "positionName": "Barista",
      "plannedMinutes": 480,
      "canRequestSwap": true
    }
  ]
}
```

### Estados vacíos

- Sin publicación: `publication: null`, `assignments: []`.
    
- Publicación existente sin turnos propios: publicación informada, array vacío.
    
- Sin próximo turno: `200` con `assignment: null`.
    

No se utiliza un `404` para representar todos esos casos.

Sin vínculo personal habilitado se devuelve un error específico, no un horario vacío que pueda interpretarse incorrectamente.

`canRequestSwap` no sustituye la validación al crear.

Después de un intercambio, un usuario no puede abrir por identificador una asignación que ya no le pertenece mediante el endpoint personal de horario vigente. El historial de su solicitud conserva la información autorizada del intercambio.

## 12. Intercambios

### 12.1. Endpoints

|   |   |   |
|---|---|---|
|Método y ruta|Permiso|Uso|
|`GET /me/swap-candidates`|Personal|Candidatos elegibles|
|`POST /swaps`|Personal|Proponer|
|`GET /me/swaps`|Personal|Solicitudes propias|
|`GET /swaps`|A/S|Gestión|
|`GET /swaps/{id}`|Participante o A/S autorizado|Detalle|
|`POST /swaps/{id}/accept`|Destinatario|Aceptar|
|`POST /swaps/{id}/reject`|Según estado y actor|Rechazar|
|`POST /swaps/{id}/cancel`|Solicitante|Cancelar|
|`POST /swaps/{id}/approval-preview`|A/S independiente|Revisar resultado|
|`POST /swaps/{id}/approve`|A/S independiente|Aprobar|

`approval-preview` es una evaluación sin escritura: requiere CSRF, no clave idempotente.

### 12.2. Candidatos

Parámetros:

- `sourceAssignmentId`.
    
- `from`.
    
- `toExclusive`.
    
- Paginación.
    

El periodo se limita inicialmente a 92 días por consulta.

La búsqueda permite otras semanas, pero no acceso libre al horario completo de la empresa.

Devuelve solo:

- Identificación visible del compañero.
    
- Asignación y revisión.
    
- Inicio y fin.
    
- Puesto.
    
- Zona necesaria para interpretar los horarios.
    

No devuelve contacto, indisponibilidades privadas ni credenciales.

Consultar candidatos no reserva turnos.

### 12.3. Crear solicitud

```
{
  "sourceAssignmentId": "assignment_demo_ana",
  "sourceRevisionId": "revision_demo_ana_2",
  "targetAssignmentId": "assignment_demo_bruno",
  "targetRevisionId": "revision_demo_bruno_1"
}
```

El servidor:

- Obtiene al solicitante desde la sesión y vínculo.
    
- Comprueba propiedad del turno de origen.
    
- Obtiene al destinatario desde la asignación y cuenta vinculada.
    
- Verifica revisiones y condiciones.
    
- Obtiene las publicaciones vigentes que contienen esas revisiones.
    
- Guarda esas referencias.
    
- Reserva ambos turnos.
    

No acepta un `requesterUserId` arbitrario.

Respuesta:

```
{
  "id": "swap_demo_1",
  "status": "PENDIENTE_COMPANERO",
  "version": 1,
  "expiresAt": "2026-08-31T13:00:00Z",
  "scheduleChanged": false,
  "availableActions": ["CANCEL"]
}
```

### 12.4. Aceptar, rechazar y cancelar

Aceptar:

```
{
  "expectedVersion": 1
}
```

Rechazar o cancelar:

```
{
  "expectedVersion": 1,
  "reason": "Motivo cuando corresponda"
}
```

|   |   |   |
|---|---|---|
|Acción|Condición|Motivo|
|Aceptar|Destinatario, pendiente de compañero|No se exige|
|Rechazar como destinatario|Pendiente de compañero|Opcional|
|Cancelar|Solicitante, cualquier estado pendiente|Opcional|
|Rechazar como responsable|A/S independiente, pendiente de supervisor|Obligatorio|

Un participante con rol administrativo no puede utilizar la resolución del responsable para rechazar su propio intercambio. Conserva únicamente sus acciones personales permitidas.

Estas acciones devuelven el estado y versión resultantes.

Aceptar nunca aplica la permuta.

### 12.5. Detalle

Incluye:

- Participantes.
    
- Asignaciones y revisiones originales.
    
- Horario actual y resultado propuesto.
    
- Estado.
    
- Versión.
    
- Vencimiento.
    
- Eventos legibles.
    
- Motivo de resolución cuando corresponda.
    
- Acciones disponibles.
    
- Cambio aplicado si fue aprobado.
    

Las acciones se calculan para el usuario actual.

Las consultas pueden mostrar el vencimiento efectivo aunque la actualización persistente esté pendiente. Un GET no escribe el estado de negocio; los comandos aplican la regla temporal y el mantenimiento materializa el estado correspondiente.

### 12.6. Vista previa de aprobación

Entrada:

```
{
  "expectedVersion": 2
}
```

Salida:

```
{
  "swapId": "swap_demo_1",
  "version": 2,
  "policyVersionId": "policy_demo_2",
  "canProceed": true,
  "blockers": [],
  "warnings": [],
  "validationFingerprint": "<huella del resultado>",
  "requirements": {
    "reasonRequired": false
  }
}
```

La respuesta completa incluye las asignaciones actuales y propuestas, junto con las semanas afectadas.

Si hay advertencias, `reasonRequired` es `true`.

No se mantiene un bloqueo mientras el usuario revisa la pantalla.

### 12.7. Aprobar

```
{
  "expectedVersion": 2,
  "validationFingerprint": "<huella de aprobación>",
  "reason": null,
  "warningAcknowledgements": []
}
```

Además de versiones y huella, se comprueban:

- Estado correcto.
    
- Vencimiento.
    
- Independencia.
    
- Cuentas y empleados activos.
    
- Revisiones vigentes.
    
- Reservas.
    
- Puesto y sede.
    
- Indisponibilidad.
    
- Solapamientos.
    
- Descansos.
    
- Política y umbrales.
    

Respuesta:

```
{
  "id": "swap_demo_1",
  "status": "APROBADA",
  "version": 3,
  "scheduleChanged": true,
  "changeSetId": "change_demo_swap_1",
  "publications": [
    {
      "id": "publication_demo_5",
      "scheduleId": "schedule_demo_week_1",
      "number": 5
    }
  ],
  "availableActions": []
}
```

Entre semanas distintas, `publications` contiene dos elementos.

No se considera éxito si solo cambió una asignación.

### 12.8. Finalización e invalidación

- Los estados terminales no se reabren.
    
- Modificar una revisión referenciada invalida la solicitud.
    
- Modificar otra asignación no la invalida automáticamente.
    
- La aprobación revalida aunque la revisión esperada permanezca.
    
- Terminar libera ambas reservas.
    
- Vencimiento e invalidación conservan causa.
    

Un comando rechazado por vencimiento puede confirmar únicamente la actualización del estado terminal, su auditoría y liberación de reservas. No aplica el intercambio solicitado.

El error debe permitir que la interfaz recargue el estado actual.

## 13. Concurrencia y huellas

### 13.1. Versiones esperadas

|   |   |
|---|---|
|Campo|Uso|
|`expectedVersion`|Recurso mutable|
|`expectedDraftVersion`|Borrador y sus líneas|
|`expectedBasePublicationId`|Base de planificación|
|`expectedCurrentPolicyVersionId`|Política que se pretende reemplazar|

Los campos requeridos no pueden omitirse para forzar una actualización.

No se combina este mecanismo con un segundo control paralelo mediante `ETag` e `If-Match` en el MVP.

### 13.2. Huellas de revisión

`validationFingerprint` y `warningFingerprint` serán cadenas hexadecimales de longitud fija correspondientes a las huellas definidas en el modelo.

La representación normalizada y su versión se fijarán antes de implementar.

Se excluyen del cálculo valores que cambian sin alterar el resultado, como un identificador de petición o la hora de generación de la vista previa.

Se incluyen condiciones y resultados que sí cambian la decisión.

### 13.3. Validación temporal

El servidor vuelve a evaluar si los turnos comenzaron o la solicitud venció.

Una huella coincidente no permite aprobar un turno que ya comenzó.

### 13.4. Orden de confirmación

Para comandos idempotentes:

1. Identificar y autorizar al actor.
    
2. Coordinar la ejecución.
    
3. Buscar un resultado anterior de la misma intención.
    
4. Si existe, recuperarlo según permisos.
    
5. Si no existe, validar versiones, reglas y confirmaciones.
    
6. Ejecutar y registrar el resultado dentro de la transacción.
    

Un reintento de una publicación ya confirmada no se rechaza simplemente porque su versión anterior ya cambió: primero debe reconocerse el resultado idempotente existente.

### 13.5. Respuesta del frontend

Ante conflicto:

- No sobrescribir.
    
- No actualizar versiones y reenviar automáticamente como nueva intención.
    
- Consultar el estado vigente.
    
- Mostrar diferencias pertinentes.
    
- Pedir una nueva decisión.
    

## 14. Idempotencia y resultados inciertos

### 14.1. Alcance

Se exige `Idempotency-Key` en los comandos de negocio inventariados.

Las excepciones de credenciales son explícitas. Las consultas y vistas previas no crean registros genéricos de operación.

### 14.2. Regla de identidad

La clave se delimita por:

- Organización.
    
- Actor.
    
- UUID de intención.
    

El contenido normalizado incluye método, ruta, parámetros, cuerpo, versiones, motivos y confirmaciones.

- Misma clave y contenido: mismo resultado registrado.
    
- Misma clave con contenido diferente: conflicto.
    
- Decisión nueva: clave nueva.
    

### 14.3. Persistencia

Se utiliza `api_operations`, definida en la nota 07.

El cambio y el resultado se confirman juntos.

`IN_PROGRESS` es interno a la transacción; no se presenta como una cola de trabajos persistentes del MVP.

Un resultado confirmado puede ser:

- `SUCCEEDED`.
    
- `REJECTED`, para un rechazo de negocio conocido y registrado.
    

Un fallo técnico con reversión no se registra como éxito.

No se guardan secretos, cookies o contraseñas para reproducir respuestas.

### 14.4. Repetición del comando

El servidor devuelve el código y cuerpo original, sujetos a autorización actual.

Puede indicar:

```
Idempotency-Replayed: true
```

Esta cabecera es una convención de TurnoShift.

No se reproducen cookies ni cabeceras de seguridad antiguas.

El identificador de diagnóstico de la nueva petición sigue siendo propio de esa petición.

### 14.5. Consulta del resultado

```
GET /operations/{idempotencyKey}
```

Resultado confirmado:

```
{
  "status": "COMPLETED",
  "outcome": "SUCCEEDED",
  "completedAt": "2026-08-30T15:30:00Z",
  "expiresAt": "2026-08-31T15:29:59Z",
  "originalResponse": {
    "status": 201,
    "contentType": "application/json",
    "location": "/api/v1/publications/publication_demo_4",
    "body": {
      "changeSetId": "change_demo_4",
      "publicationId": "publication_demo_4"
    }
  }
}
```

El cuerpo mostrado es esquemático: en implementación contiene la respuesta exacta definida para el comando original.

`200` significa que se recuperó el resultado; `outcome` y `originalResponse.status` indican si el comando original tuvo éxito.

### 14.6. Resultado no encontrado

Un `404 OPERATION_NOT_FOUND` significa:

> No hay un resultado recuperable visible para esta consulta.

No prueba que nunca se haya ejecutado.

Puede deberse a:

- Petición todavía sin confirmar.
    
- Petición no recibida.
    
- Transacción revertida.
    
- Registro fuera de retención.
    

Una petición simultánea no ejecuta otro cambio por no ver todavía la fila de la primera; debe respetar el protocolo transaccional.

### 14.7. Ventana

Retención inicial propuesta: 24 horas desde la creación del registro.

Dentro de la ventana:

- Se conserva la clave.
    
- Se puede reenviar exactamente la misma intención.
    
- Se limita el número de reintentos.
    
- Se consulta el estado cuando sea necesario.
    

Fuera de la ventana:

- No se promete deduplicación.
    
- Se revisa el recurso.
    
- Se requiere una decisión consciente antes de otra operación.
    

Una respuesta recuperada puede describir una versión anterior del recurso. Después se consulta el estado vigente.

### 14.8. Qué no hacer

- Generar una clave nueva por cada intento de red.
    
- Reutilizar una clave para otra edición.
    
- Guardar la clave únicamente en el botón.
    
- Interpretar cualquier error como ausencia de cambio.
    
- Repetir automáticamente un comando tras una restauración sin reconciliar su resultado.
    

## 15. Errores

Se utiliza `application/problem+json`, con extensiones propias para códigos, campos y referencias autorizadas. [Referencia: RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html).

Ejemplo:

```
{
  "type": "/problems/preview-outdated",
  "title": "La revisión previa cambió",
  "status": 409,
  "detail": "Revisa nuevamente los impactos antes de confirmar.",
  "instance": "/api/v1/schedule-drafts/draft_demo_1/publish",
  "code": "PREVIEW_OUTDATED",
  "requestId": "request_demo_123",
  "errors": []
}
```

El frontend utiliza `code`, no compara frases en español.

### 15.1. Estados

|   |   |
|---|---|
|HTTP|Uso|
|200|Consulta, evaluación o acción con resultado|
|201|Creación de recurso, incluida publicación|
|204|Operación sin contenido, cuando lo especifique|
|400|Formato, parámetro, cabecera o estructura inválida|
|401|Autenticación requerida|
|403|Acción o protección de acceso rechazada|
|404|Recurso o resultado no disponible|
|409|Conflicto de versión, estado, huella o idempotencia|
|422|Petición interpretable que incumple validaciones|
|429|Límite de solicitudes|
|500|Error interno|
|503|Indisponibilidad temporal|

Una vista previa con bloqueos devuelve `200` porque la evaluación se completó. El comando incompatible devuelve el error correspondiente.

### 15.2. Códigos principales

|   |   |
|---|---|
|Código|Significado|
|`VALIDATION_FAILED`|Campos inválidos|
|`SESSION_REQUIRED`|Sesión ausente o vencida|
|`ACCESS_DENIED`|Acción no permitida|
|`CSRF_INVALID`|Protección CSRF inválida|
|`RESOURCE_NOT_FOUND`|Recurso no disponible|
|`PERSONAL_ACCESS_UNAVAILABLE`|Sin vínculo personal habilitado|
|`VERSION_CONFLICT`|Versión antigua|
|`DRAFT_ALREADY_EXISTS`|Borrador abierto existente|
|`DRAFT_BASE_OUTDATED`|Publicación base desactualizada|
|`PREVIEW_OUTDATED`|Impactos diferentes a los revisados|
|`SHIFT_OVERLAP`|Superposición|
|`MIN_REST_VIOLATION`|Descanso insuficiente|
|`EMPLOYEE_UNAVAILABLE`|Indisponibilidad incompatible|
|`EMPLOYEE_INACTIVE`|Empleado no habilitado|
|`POSITION_INCOMPATIBLE`|Puesto incompatible|
|`SHIFT_ALREADY_STARTED`|Modificación de turno iniciado|
|`EMPLOYEE_HAS_COMMITMENTS`|Desactivación bloqueada|
|`USER_LINK_HAS_PENDING_SWAPS`|Cambio de vínculo bloqueado|
|`LAST_ACTIVE_ADMIN`|Protección del último administrador|
|`TIMEZONE_LOCKED`|Ya existe publicación|
|`TIMEZONE_DRAFT_WEEK_CONFLICT`|Cambio de zona incompatible con borrador|
|`POLICY_FUTURE_CONFLICT`|Política propuesta incompatible|
|`REASON_REQUIRED`|Falta motivo obligatorio|
|`EMPTY_SCHEDULE_CONFIRMATION_REQUIRED`|Falta confirmar semana vacía|
|`WARNING_ACKNOWLEDGEMENTS_INVALID`|Advertencias no reconocidas correctamente|
|`SWAP_ASSIGNMENT_RESERVED`|Turno en otra solicitud pendiente|
|`SWAP_SELF_APPROVAL`|Participante intenta aprobar|
|`SWAP_INDEPENDENT_REVIEW_REQUIRED`|Resolución final sin independencia|
|`SWAP_EXPIRED`|Solicitud vencida|
|`SWAP_INVALIDATED`|Solicitud invalidada|
|`INVALID_STATE_TRANSITION`|Acción incompatible con el estado|
|`IDEMPOTENCY_KEY_REQUIRED`|Falta clave|
|`IDEMPOTENCY_KEY_REUSED`|Clave con otra intención|
|`OPERATION_NOT_FOUND`|Resultado no recuperable|
|`REPORT_SOURCE_CHANGED`|Fuentes del reporte cambiaron|
|`RESET_LINK_INVALID`|Enlace no utilizable|

No se exponen excepciones, consultas SQL, secretos ni referencias ajenas.

El mapeo definitivo de cada código a su estado HTTP y esquema se declara en OpenAPI.

## 16. Reportes, historial y avisos

### 16.1. Endpoints

|   |   |   |
|---|---|---|
|Método y ruta|Permiso|Uso|
|`GET /dashboard`|A/S|Resumen operativo|
|`GET /reports/planned-hours`|A/S|Horas programadas|
|`GET /reports/planned-hours.csv`|A/S|Exportación|
|`GET /reports/swaps`|A/S|Resumen de intercambios|
|`GET /reports/swaps.csv`|A/S|Exportación|
|`GET /audit-events`|A/S según alcance|Historial|
|`GET /me/notifications`|Usuario activo|Avisos propios|
|`POST /me/notifications/{id}/read`|Propietario|Marcar leído|
|`GET /me`|Usuario activo|Cuenta propia|

### 16.2. Periodos

```
from=2026-08-31
toExclusive=2026-09-07
employeeId=employee_demo_ana
```

Las fechas se interpretan en la zona de la organización.

Máximo inicial: 92 días por consulta.

Para horas programadas se incluyen los segmentos de turnos que transcurren dentro del periodo, aunque comiencen antes.

Para el resumen de intercambios, el periodo selecciona solicitudes creadas dentro de él. El estado se evalúa al momento de generar el reporte.

Esa definición debe mostrarse para no confundir creación con fecha de resolución.

### 16.3. Resultado

Incluye:

- Periodo y filtros.
    
- Zona.
    
- Fecha de generación.
    
- Fuentes.
    
- Totales.
    
- `sourceFingerprint`.
    

La huella considera el conjunto filtrado y datos relevantes. En intercambios incluye el estado efectivo; no basta con contar filas.

No incluye la hora de generación como valor que fuerce un conflicto en cada consulta.

### 16.4. Exportación consistente

La exportación recibe los mismos filtros y:

```
expectedSourceFingerprint=<huella del reporte>
```

En el flujo del MVP este parámetro es obligatorio: primero se consulta, después se exporta el resultado revisado.

Si las fuentes relevantes cambiaron:

```
409 REPORT_SOURCE_CHANGED
```

El backend compara y genera el archivo sobre una lectura coherente. No valida una huella y después exporta datos leídos sin consistencia.

### 16.5. CSV

Se define:

- UTF-8.
    
- Columnas estables.
    
- Nombre seguro generado por el servidor.
    
- Escape de separadores, comillas y saltos.
    
- Datos y filtros autorizados.
    
- Metadatos suficientes para identificar periodo y generación.
    
- Protección contra fórmulas en campos de texto no confiables.
    

Entrecomillar un campo no basta para neutralizar fórmulas; se verificará el tratamiento con los programas de destino. [Referencia: OWASP, CSV Injection](https://owasp.org/www-community/attacks/CSV_Injection).

Los reportes CSV no constituyen una exportación completa de toda la organización.

### 16.6. Historial

Devuelve eventos legibles:

- Actor.
    
- Fecha.
    
- Acción.
    
- Recurso.
    
- Motivo.
    
- Cambios autorizados.
    

No expone JSON interno completo por defecto.

La consulta de decisiones y advertencias puede integrarse en los detalles de publicación, política e intercambio. No se crea un CRUD público para las tablas de evidencia.

### 16.7. Avisos

Marcar leído recibe `{}` y devuelve el aviso o su estado de lectura.

- No cambia `readAt` si ya estaba leído.
    
- Solo el propietario puede ejecutarlo.
    
- Leer no equivale a aceptar un turno.
    
- Abrir el recurso vuelve a comprobar permisos.
    

Se utiliza actualización al abrir la bandeja y consulta periódica.

La consulta automática no debe mantener indefinidamente una sesión que se presenta como inactiva. Su tratamiento se coordina con la política de sesiones.

No se añaden WebSockets o push al MVP.

## 17. Integraciones

|   |   |
|---|---|
|Componente|Responsabilidad|
|React|Presentación y consumo de contratos|
|Nginx|Mismo origen y entrada HTTPS|
|Spring Boot|Sesiones, permisos y reglas|
|SQL Server|Datos, sesiones, evidencia e idempotencia|
|Proceso interno|Avisos y mantenimiento|
|CSV|Salida autorizada|

No se incorporan pasarelas, facturación, SMS, WhatsApp, calendarios externos, nómina, biometría o IA.

### 17.1. Operador

Alta y suspensión de organizaciones se realizan mediante herramientas restringidas.

No hay registro público ni endpoints del cliente para ejecutar funciones de plataforma.

### 17.2. Obsidian, Antigravity y MCP

Son herramientas de desarrollo, no integraciones de TurnoShift.

- No se agregan endpoints del producto para consultar Obsidian.
    
- No se concede acceso indiscriminado a producción.
    
- Contratos y decisiones se versionan.
    
- El contexto se organiza según el documento 15.
    

## 18. Seguridad transversal

- Aislamiento en cada consulta y comando.
    
- Permisos actuales.
    
- Validación de campos.
    
- Tamaños y periodos acotados.
    
- Límite de intentos de acceso y recuperación.
    
- CSRF.
    
- Consultas parametrizadas.
    
- Registros sin secretos.
    
- Exportaciones protegidas.
    
- Invalidación efectiva de sesiones.
    
- Tiempos de espera controlados.
    
- Sin respuestas exitosas ficticias.
    

Las respuestas autenticadas sensibles y las de recuperación utilizan `Cache-Control: no-store`.

Producción usa el mismo origen; no se habilita CORS arbitrario.

Las rutas de retorno se restringen a destinos internos seguros.

## 19. OpenAPI y límites de implementación

Esta nota no reemplaza el contrato ejecutable.

Cada operación debe tener:

- Identificador único.
    
- Esquema completo.
    
- Campos obligatorios y anulables.
    
- Límites.
    
- Formatos.
    
- Enumeraciones.
    
- Cabeceras.
    
- Respuestas.
    
- Códigos de error.
    
- Permisos.
    
- Ejemplos válidos.
    
- Reglas de versión e idempotencia.
    

### Límites que deben concretarse antes del endpoint afectado

- Longitud de códigos y nombres.
    
- Longitud de motivos.
    
- Longitud de notas privadas.
    
- Tamaño máximo de petición.
    
- Máximo de elementos en vistas previas.
    
- Tamaño de respuesta idempotente.
    
- Límites de intentos y reintentos.
    
- Tiempos de espera.
    

Estos valores deben coincidir con las migraciones. No se permite que el frontend acepte texto que después se trunque silenciosamente en la base.

### Correspondencia con persistencia

|   |   |
|---|---|
|Contrato|Persistencia|
|Publicar|Cambio, publicación, contenido y cierre del borrador|
|Aprobar|Cambio, revisiones, publicaciones, solicitud y reservas|
|Activar política|Versión, cambio y puntero vigente|
|Reconocer advertencias|`planning_warning_acknowledgements`|
|Reintentar comando|`api_operations`|
|Consultar horario|Publicación vigente y sus revisiones|
|Emitir aviso|Outbox y notificación|
|Consultar autor y motivo|Conjunto de cambio|

La especificación debe comprobarse contra pruebas, no mantenerse como un archivo decorativo.

## 20. Pruebas prioritarias

### Acceso

- Recurso de otra empresa no revelado.
    
- Supervisor sin acceso administrativo.
    
- Cuenta desactivada con sesión anterior bloqueada.
    
- Sin CSRF no hay escritura.
    
- Cuenta sin contraseña no puede iniciar sesión.
    
- Vínculo personal requerido.
    
- Recuperación de un solo uso.
    

### Planificación

- Varias líneas nuevas con `assignmentId: null`.
    
- No se edita una línea de otro borrador.
    
- Retirar una línea devuelve versión nueva.
    
- Conflicto operativo guardable, pero no publicable.
    
- Semana vacía confirmada y justificada.
    
- Turnos iniciados preservados.
    
- Revisión exige motivo.
    
- Huella antigua no confirma impactos nuevos.
    
- Advertencias faltantes, duplicadas o desconocidas rechazadas.
    
- Cambio de zona protegido.
    

### Políticas e intercambios

- Política candidata sin identificador persistido en la vista previa.
    
- Activación incompatible bloqueada.
    
- Aceptación no cambia horarios.
    
- Resolución final independiente.
    
- Vista previa de aprobación no modifica datos.
    
- Advertencias aceptadas quedan registradas.
    
- Aprobación entre semanas atómica.
    
- Vencimiento efectivo respetado.
    
- Referencias no relacionadas no invalidan innecesariamente.
    

### Idempotencia

- Misma intención no se duplica.
    
- Otra intención con la misma clave se rechaza.
    
- Resultado recuperado antes de revalidar versiones ya superadas por su propio éxito.
    
- Resultado ajeno no accesible.
    
- `404` de operación no dispara automáticamente otra intención.
    
- Pérdida de respuesta posterior al commit recuperable.
    
- Retención no elimina evidencia de negocio.
    
- Credenciales no aparecen en respuestas almacenadas.
    

### Reportes y avisos

- Reportes excluyen borradores.
    
- Intervalos se distribuyen correctamente.
    
- Exportación detecta cambio de fuentes.
    
- CSV neutraliza contenido peligroso.
    
- Avisos respetan propiedad.
    
- Lectura repetida conserva la primera fecha.
    
- Error de aviso no revierte una publicación.
    

## 21. Sincronización documental

|   |   |
|---|---|
|Documento|Ajuste|
|04|Referencia funcional consolidada|
|05|Criterios de aceptación y errores por caso|
|06|Protocolo transaccional e idempotencia|
|07|Campos y relaciones utilizados por estos contratos|
|08 y 09|Vistas previas, confirmaciones y mensajes|
|11|Tareas de contratos y pruebas|
|12|Casos de seguridad, concurrencia y recuperación|

Ya no queda pendiente decidir en esta propuesta si se permite publicar vacío: se permite con las restricciones de la nota 04.

Tampoco queda indefinido el reconocimiento de advertencias: se envían huellas concretas y el servidor conserva la evidencia calculada.

Continúan pendientes la aprobación de la línea base, OpenAPI y su implementación.

## 22. Criterios de cierre

- Cada acción tiene endpoint.
    
- Roles y pertenencia están definidos.
    
- Identidades de borrador y publicación no se confunden.
    
- Motivos y confirmaciones son consistentes.
    
- Fechas y periodos tienen interpretación única.
    
- Las vistas previas no modifican negocio.
    
- La confirmación revalida.
    
- Los reintentos utilizan almacenamiento persistente.
    
- Los resultados inciertos tienen tratamiento seguro.
    
- Los errores permiten una interfaz clara.
    
- OpenAPI coincide con datos, implementación y pruebas.
    
- No se incorporan funciones fuera del MVP.
    

## 23. Estado de esta revisión

**Contratos principales:** consolidados para revisión frente a las notas 04 y 07.

**Archivo de la bóveda:** no modificado automáticamente.

**OpenAPI, código y pruebas:** pendientes.

El siguiente trabajo documental será trasladar estos comportamientos a los requisitos, pantallas y tareas afectadas, sin modificar nuevamente el núcleo salvo que aparezca una contradicción concreta.