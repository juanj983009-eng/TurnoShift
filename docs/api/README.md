# TurnoShift — API Docs

## Alcance de esta carpeta

Contiene el contrato HTTP del MVP de TurnoShift.

| Archivo | Propósito |
|---|---|
| `openapi.yaml` | Contrato ejecutable OpenAPI 3.1 |
| `README.md` | Guía de lectura, decisiones y pendientes |

---

## Estado del contrato

**Versión:** `0.2.4-i2.4`  
**Validación:** `npx @redocly/cli lint docs/api/openapi.yaml` → 0 errores, 0 advertencias.
**Esta validación comprueba la forma del contrato, no que los endpoints estén implementados, probados ni desplegados.**

---

## Cómo leer el contrato

```bash
npx @redocly/cli preview-docs docs/api/openapi.yaml
```

### Estructura del archivo

```
openapi.yaml
├── info          → versión, descripción, convenciones globales
├── servers       → prefijo /api/v1 (API empresarial) + raíz / (salud)
├── tags          → infrastructure | session | operator | password-setup | positions | organization | employees | policies | unavailabilities
├── paths         → endpoints organizados por tag
└── components
    ├── securitySchemes  → sessionCookie, csrfHeader, operatorSession (pendiente)
    ├── headers          → X-Request-ID, Idempotency-Replayed, Retry-After
    ├── parameters       → CsrfTokenHeader, IdempotencyKeyHeader, PositionIdPath, PositionActiveQuery, EmployeeIdPath, EmployeeActiveQuery, UnavailabilityIdPath
    ├── schemas          → request/response bodies y enumeraciones
    └── responses        → respuestas de error reutilizables
```

### Semántica AND/OR de bloques `security` (OpenAPI 3.1)

- Entradas **dentro del mismo objeto** (mismo guión `-`): **AND** — todas requeridas simultáneamente.
- Entradas en **distintos objetos** (distintos guiones `-` en el array): **OR** — cualquiera basta.

Ejemplos en este contrato:
```yaml
# AND: se requieren cookie de sesión Y CSRF juntos
security:
  - sessionCookie: []
    csrfHeader: []

# Solo CSRF (no requiere sesión activa)
security:
  - csrfHeader: []

# Acceso anónimo
security: []
```

---

## Alcance del contrato (I1 + I2.1 + I2.2 + I2.3 + I2.4)

### Incluido en este contrato

| Endpoint | Tag | Actor | HU | Reqto |
|---|---|---|---|---|
| `GET /api/health` | infrastructure | Cualquiera | — | HT-01 |
| `GET /auth/csrf` | infrastructure | Cualquiera (anón.) | HU-02, HU-04.1 | RF-004, RF-005 |
| `GET /auth/session` | session | Usuario autenticado | HU-02 | RF-004 |
| `POST /auth/login` | session | Usuario empresarial | HU-02 | RF-004 |
| `POST /auth/logout` | session | Usuario autenticado | HU-02 | RF-004 |
| `POST /operator/setup/organizations` | operator | Operador (restringido) | HU-01 | RF-001 |
| `POST /operator/setup/organizations/{id}/initial-access-link` | operator | Operador (restringido) | HU-04.1 + HU-01 | RF-005 |
| `POST /auth/password-reset/inspect` | password-setup | Cualquiera (anón.) | HU-04.1 | RF-005 |
| `POST /auth/password-reset/complete` | password-setup | Cualquiera (anón.) | HU-04.1 | RF-005 |
| `GET /positions` | positions | Administrador / Supervisor | HU-06 | RF-007 |
| `POST /positions` | positions | Administrador | HU-06 | RF-007 |
| `PUT /positions/{positionId}` | positions | Administrador | HU-06 | RF-007 |
| `PATCH /positions/{positionId}/status` | positions | Administrador | HU-06 | RF-007 |
| `GET /organization/configuration` | organization | Administrador / Supervisor | HU-05 | RF-006 |
| `PATCH /organization/configuration/display-name` | organization | Administrador | HU-05 | RF-006 |
| `PATCH /organization/configuration/active-site/name` | organization | Administrador | HU-05 | RF-006 |
| `GET /employees` | employees | Administrador / Supervisor | HU-07 | RF-008 |
| `POST /employees` | employees | Administrador | HU-07 | RF-008 |
| `GET /employees/{employeeId}` | employees | Administrador / Supervisor | HU-07 | RF-008 |
| `PATCH /employees/{employeeId}` | employees | Administrador | HU-07 | RF-008 |
| `PATCH /employees/{employeeId}/status` | employees | Administrador | HU-07 | RF-008 |
| `POST /employees/{employeeId}/deactivation-preview` | employees | Administrador | HU-07 | RF-008 |
| `GET /policies/current` | policies | Administrador / Supervisor | I2.3 | RF-009 |
| `GET /policies/history` | policies | Administrador / Supervisor | I2.3 | RF-009 |
| `POST /policies/preview` | policies | Administrador | I2.3 | RF-009 |
| `POST /policies/activate` | policies | Administrador | I2.3 | RF-009 |
| `POST /employees/{employeeId}/unavailabilities` | unavailabilities | Administrador / Supervisor | HU-08 | I2.4 |
| `PUT /unavailabilities/{unavailabilityId}` | unavailabilities | Administrador / Supervisor | HU-08 | I2.4 |
| `POST /unavailabilities/{unavailabilityId}/cancel` | unavailabilities | Administrador / Supervisor | HU-08 | I2.4 |

### Fuera de este contrato

- **Endpoints de I1 restantes:** `POST /auth/change-password` (cambio autenticado — HU-04.2), administración de usuarios (HU-03).
- **Incrementos posteriores (I3+):** Contratos y jornadas laborales completas, motor de planificación (I3), intercambios (I4), reportes y avisos (I5+). Las historias de configuración (HU-05), catálogo de puestos (HU-06), gestión de colaboradores (HU-07), política operativa inicial (I2.3) e indisponibilidades (I2.4 / HU-08) ya forman parte del alcance documentado e implementado.

---

## Mecanismo del operador — PROP-OP-01 (Pendiente de revisión)

Las notas de planificación especifican que las altas y suspensiones de organizaciones
se realizan mediante **herramientas restringidas, no mediante endpoints públicos del cliente**
(Nota 10, §17.1: "No hay registro público ni endpoints del cliente para ejecutar funciones de plataforma").

`platform_operators` es una entidad separada de `app_users`:
- No pertenece a ninguna organización cliente.
- No concede acceso empresarial por sí mismo.
- No implica construir otro portal público de autenticación (Nota 07, §6.1).
- El operador no es un cuarto rol empresarial (Nota 07, §6.3).

**Estado actual:** la planificación no especifica el mecanismo técnico concreto de autenticación
del operador. Este contrato propone `operatorSession` (cookie propia) como placeholder bajo
rutas `/operator/setup/*` para describir el comportamiento esperado.

**Propuesta técnica mínima (PROP-OP-01):**

> Herramienta CLI o interfaz administrativa restringida por red (no accesible públicamente),
> autenticada mediante una sesión gestionada internamente en el servidor bajo la tabla
> `platform_operators`. El campo `subject` identifica al operador sin mezclar identidades
> con `app_users`. La auditoría registra `actor_type = PLATFORM_OPERATOR` y
> `actor_operator_id` (Nota 07, §9.1 y §9.2).

**Pendiente de revisión por el propietario antes de implementar los endpoints `/operator/*`.**

---

## Flujo de incorporación + primer acceso

| Paso | Actor | Acción | Endpoint | Dependencia pendiente |
|---|---|---|---|---|
| 1 | Operador | Alta atómica: org + sede + admin | `POST /operator/setup/organizations` | PROP-OP-01: mecanismo auth operador |
| 2 | Operador | Emitir enlace inicial (acción diferenciada) | `POST /operator/setup/organizations/{id}/initial-access-link` | PROP-OP-01 |
| 3 | Operador → Admin | Entregar enlace por canal seguro | — | Canal operativo (DEC-06, pendiente de ensayo) |
| 4 | Administrador | Inspeccionar enlace sin consumirlo | `POST /auth/password-reset/inspect` | — |
| 5 | Administrador | Establecer contraseña y consumir enlace | `POST /auth/password-reset/complete` | — |
| 6 | Administrador | Iniciar sesión empresarial | `POST /auth/login` | — |

**Dependencias no resueltas bloqueantes:**
- PROP-OP-01: mecanismo de autenticación del operador.
- DEC-06: canal y procedimiento operativo de entrega del enlace (pendiente de ensayo antes del piloto).

---

## Decisiones aplicadas en este contrato

### Autenticación y sesión

- **Cookie de sesión** (`HttpOnly`, `Secure` en producción, `SameSite=Lax`). Sin JWT ni `Authorization: Bearer`.
- La organización se deriva **exclusivamente** de la sesión del servidor. No se acepta `organizationId` enviado por el cliente.
- `GET /auth/session` es el mecanismo canónico para revalidar el estado del frontend.

### CSRF

- Todas las escrituras requieren `X-CSRF-TOKEN`. Se obtiene con `GET /auth/csrf`.
- Renovar tras `login` y `logout`.
- Sesiones anónimas para CSRF son válidas; no representan usuarios autenticados (Nota 07, §10.1).

### Idempotencia

- `Idempotency-Key` es una cabecera obligatoria de **parámetro de petición** para comandos de negocio.
  No es un mecanismo de autenticación y no aparece en `securitySchemes` como esquema de seguridad.
- Las operaciones de credenciales (`login`, `logout`, password-setup) no crean registros en `api_operations`.
  El secreto del enlace nunca se almacena en `response_body` (Nota 07, §10.3).
- La clave de idempotencia del alta (antes de que exista la organización) está delimitada por
  la identidad del operador y el UUID de intención, no por `organization_id`.
- Para mutaciones empresariales (HU-05 y HU-06), la idempotencia está delimitada por
  `(organizationId, actorUserId, idempotencyKey)`.
- En replay determinista, el servidor reemite la respuesta serializada con la cabecera
  `Idempotency-Replayed: true` y `Cache-Control: no-store` (el replay exitoso JSON no tiene cabecera `X-Request-ID`; las respuestas Problem Details incluyen un `requestId` fresco).
- Reutilizar una clave vigente con un payload canónico diferente responde `409 IDEMPOTENCY_KEY_REUSED`.
- Si la clave ha vencido en la persistencia, responde `409 IDEMPOTENCY_KEY_EXPIRED`.

### Concurrencia optimista (HU-05 y HU-06)

- Las operaciones de actualización (`PUT /positions/{id}`, `PATCH /positions/{id}/status`,
  `PATCH /organization/configuration/display-name` y `PATCH /organization/configuration/active-site/name`)
  exigen el campo `expectedLockVersion` (entero >= 0) en el cuerpo de la petición.
- Si el registro fue modificado concurrentemente por otra operación, el servidor rechaza la
  petición de forma determinista con `409 CONCURRENT_MODIFICATION`.

### Errores

- Problem Details ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)).
- El frontend usa `code` para identificar el problema, nunca `title` ni `detail`.
- `400 VALIDATION_FAILED`: cuerpo JSON inválido, formato incorrecto o parámetros fuera de regla.
- `400 PAST_INTERVAL_MODIFICATION_PROHIBITED`: intento de registrar una indisponibilidad en un periodo ya finalizado (`endsAt <= now`), o de modificar o cancelar una indisponibilidad en un periodo ya iniciado (`startsAt <= now`, RN-013).
- `400 REASON_REQUIRED`: motivo de activación ausente, vacío, con puros espacios en blanco o superior a 500 caracteres.
- `400 INVALID_VALIDATION_FINGERPRINT`: huella de validación ausente, fuera del formato `^[0-9a-f]{64}$` o no coincidente con la propuesta.
- `401 AUTHENTICATION_FAILED`: credenciales incorrectas, cuenta inactiva o sin contraseña configurada
  (sin revelar cuál aplica).
- `401 SESSION_REQUIRED`: sesión ausente o vencida.
- `403 ACCESS_DENIED`: acción no permitida para el rol o estado actual.
- `403 CSRF_INVALID`: cabecera CSRF ausente o inválida.
  Ambos `403` se distinguen por `code`; comparten la respuesta `AccessDeniedOrCsrf`.
- `404 EMPLOYEE_NOT_FOUND`: colaborador inexistente o no perteneciente a la organización del usuario.
- `404 POSITION_NOT_FOUND`: puesto inexistente o no perteneciente a la organización del usuario.
- `404 RESOURCE_NOT_FOUND`: organización o sede no encontrada. Se garantiza un detalle fijo y seguro
  (`"El recurso solicitado no existe o no pertenece a la organización."`) sin exponer UUIDs internos.
- `404 UNAVAILABILITY_NOT_FOUND`: indisponibilidad inexistente o no perteneciente a la organización del usuario.
- `409 CONCURRENT_MODIFICATION`: versión de bloqueo optimista desactualizada en recursos con `expectedLockVersion`.
- `409 POLICY_CONCURRENT_MODIFICATION`: la versión actual de la política operativa no coincide con `expectedCurrentPolicyVersionId`.
- `409 POSITION_CODE_ALREADY_EXISTS`: el código de puesto ya existe en la organización.
- `409 UNAVAILABILITY_ALREADY_CANCELLED`: la indisponibilidad especificada ya se encuentra cancelada (estado terminal).
- `409 IDEMPOTENCY_KEY_REUSED`: clave de idempotencia reutilizada con contenido diferente.
- `409 IDEMPOTENCY_KEY_EXPIRED`: clave de idempotencia vencida en la base de datos.
- `422 RESET_LINK_INVALID`: enlace inválido, vencido, consumido o revocado.
- `503 POLICY_OPERATION_BUSY`: candado exclusivo de políticas retenido por otra operación concurrente (`sp_getapplock`). Incluye cabecera `Retry-After: 1`.
- `503 SERVICE_UNAVAILABLE`: indisponibilidad transitoria en persistencia o idempotencia.

### Indisponibilidades de colaboradores (HU-08 / I2.4)

- **Formatos temporales mutuamente excluyentes:**
  - Rango parcial por horas: instantes UTC absolutos ISO-8601 (`startsAt`, `endsAt`), con `startsAt < endsAt`.
  - Día completo (`allDay: true`): fechas de calendario local (`startDate` inclusiva, `endDate` exclusiva en `YYYY-MM-DD`), expandidas a UTC mediante la zona horaria oficial del tenant (`organizations.timezone_id`).
  - Mezclar ambos formatos o enviar datos incompletos responde `400 VALIDATION_FAILED`.
- **Protección de periodos pasados o iniciados:**
  - **RN-013 (Modificación y Cancelación):** No se permite modificar ni cancelar indisponibilidades en periodos ya iniciados (`startsAt <= now` de la indisponibilidad existente o `newStartsAt <= now` del nuevo intervalo en modificación, y `startsAt <= now` de la indisponibilidad existente en cancelación; SQL Server `starts_at > SYSUTCDATETIME()`).
  - **Creación:** No se restringe que la indisponibilidad haya iniciado (`startsAt <= now`) mientras no haya concluido (`endsAt > now`). Se rechaza únicamente si el periodo ya ha finalizado completamente (`endsAt <= now`).
  - En todos los casos de rechazo temporal por periodo vencido o iniciado, responde deterministamente con `400 PAST_INTERVAL_MODIFICATION_PROHIBITED`.
- **Concurrencia optimista:** Las mutaciones de modificación (`PUT /unavailabilities/{id}`) y cancelación (`POST /unavailabilities/{id}/cancel`) requieren `expectedLockVersion`. Si difiere de la versión en base de datos, responde `409 CONCURRENT_MODIFICATION`.
- **Estado terminal:** Una indisponibilidad cancelada (`cancelled = true`) no puede modificarse ni volver a cancelarse; responde `409 UNAVAILABILITY_ALREADY_CANCELLED`.
- **Catálogo de motivos:** `reasonCode` opcional restringido a la enumeración `UnavailabilityReason` (`VACACIONES`, `PERMISO_PERSONAL`, `MEDICO`, `FORMACION`, `OTRO`).
- **Notas restringidas:** `restrictedNote` opcional de hasta 500 unidades UTF-16, con validación estricta que rechaza cadenas vacías o compuestas exclusivamente de espacios en blanco o caracteres Unicode de separación.

### Política operativa inicial (I2.3)

- **Modelo inmutable append-only:** Las versiones se insertan en `planning_policy_versions` con numeración secuencial (`versionNumber`) ascendente sin modificar versiones históricas.
- **Puntero de versión vigente:** `organizations.current_policy_version_id` referencia atómicamente la versión vigente. Si es `null`, la organización no cuenta aún con política configurada (`GET /policies/current` responde `{"policy": null}`).
- **Bloqueo transaccional exclusivo:** `sp_getapplock` por organización dentro de la transacción Spring garantiza aislamiento estricto en la activación de políticas. Si el bloqueo no se obtiene de inmediato, responde `503 POLICY_OPERATION_BUSY` con cabecera `Retry-After: 1`.
- **Huella de validación determinista:** `validationFingerprint` generado en `POST /policies/preview` enlaza de forma canónica el estado actual esperado y los umbrales propuestos, verificándose en `POST /policies/activate`. Formato estricto `^[0-9a-f]{64}$`.
- **Propiedad obligatoria `expectedCurrentPolicyVersionId`:** En `preview` y `activate`, el emisor debe enviar explícitamente `null` si no hay política previa o el UUID de la versión a sustituir. Si difiere de la versión en BD, responde `409 POLICY_CONCURRENT_MODIFICATION`.

### Restricciones de diseño aplicadas en I2.1

- **Zona horaria inmutable en I2.1:** `timezoneId` se expone en solo lectura en `GET /organization/configuration`.
  No se permite su modificación en I2.1 debido a que el recálculo y validación de turnos requiere
  el motor de planificación de I3.
- **Sede única:** El sistema opera bajo el modelo de sede activa única por organización (`activeSite`).
  Su código es inmutable; únicamente su nombre puede modificarse mediante `PATCH`.
- **Sin borrado físico de puestos:** No se expone `DELETE /positions/{id}` para preservar la
  integridad referencial de históricos y auditorías; en su lugar, se desactiva lógicamente mediante
  `PATCH /positions/{positionId}/status`. El código de puesto es inmutable.

### Enlace de configuración inicial (HU-04.1)

- Un solo uso; vencimiento propuesto 30 minutos (pendiente DEC-08).
- Hash SHA-256 almacenado; secreto original nunca en base de datos.
- Inspeccionarlo no lo consume.
- Emitir uno nuevo revoca el anterior.
- El secreto solo se entrega en la respuesta de emisión al operador; nunca en auditoría, logs ni `api_operations`.
- Respuesta incierta del operador: emitir uno nuevo de forma consciente; no hay forma de recuperar el secreto.

---

## Pendientes antes de implementar (DEC-08)

| Campo / aspecto | Estado |
|---|---|
| Longitud máxima de `organizationCode` y patrón permitido | Pendiente |
| Longitud máxima de `displayName` (organización, sede, usuario) | Pendiente |
| Longitud mínima/máxima de `password` y reglas de complejidad | Pendiente |
| Vencimiento del enlace de configuración (30 min propuesto) | Pendiente de confirmar |
| Límites de intentos de login (rate limiting) y periodo | Pendiente |
| Tamaño máximo de petición | Pendiente |
| Retención de `api_operations` (24 h propuesto) | Pendiente de confirmar |

Estos valores deben coincidir con las migraciones de base de datos.

---

## Próximos pasos

1. Resolver PROP-OP-01 (mecanismo auth del operador) y DEC-06 (canal de entrega).
2. Resolver DEC-08 (límites de campos).
3. Implementar HT-02 (migraciones base y datos ficticios) y HT-07 (suite de pruebas).
4. Implementar los endpoints en Spring Boot alineados con este contrato.
5. Ampliar el contrato con los endpoints restantes de I1 (HU-03, HU-04.2).
