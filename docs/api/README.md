# TurnoShift — API Docs

## Alcance de esta carpeta

Contiene el contrato HTTP del MVP de TurnoShift.

| Archivo | Propósito |
|---|---|
| `openapi.yaml` | Contrato ejecutable OpenAPI 3.1 |
| `README.md` | Guía de lectura, decisiones y pendientes |

---

## Estado del contrato

**Versión:** `0.1.0-i1`  
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
├── tags          → infrastructure | session | operator | password-setup
├── paths         → endpoints organizados por tag
└── components
    ├── securitySchemes  → sessionCookie, csrfHeader, operatorSession (pendiente)
    ├── headers          → X-Request-ID, Idempotency-Replayed
    ├── parameters       → CsrfTokenHeader, IdempotencyKeyHeader
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

## Alcance del contrato parcial I1

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

### Fuera de este contrato parcial (en alcance de I1)

Los siguientes endpoints pertenecen al incremento I1 pero no forman parte del subconjunto de
acceso inicial cubierto aquí. Se describirán cuando se implemente su historia correspondiente:

- `POST /auth/change-password` (cambio autenticado — HU-04.2).
- `POST /users` (crear cuenta — HU-03, parcialmente en I1).
- `POST /users/{id}/recovery-links` (emitir enlace posterior — HU-04.2, requiere HU-03).
- `GET/PATCH /users/{id}`, activar/desactivar cuentas (HU-03).

### Fuera del incremento I1

RF-002 a RF-003, RF-006 a RF-018, sede, puestos, colaboradores, indisponibilidades,
planificación, intercambios, reportes y avisos (I2–I7).

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

### Errores

- Problem Details ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)).
- El frontend usa `code` para identificar el problema, nunca `title` ni `detail`.
- `401 AUTHENTICATION_FAILED`: credenciales incorrectas, cuenta inactiva o sin contraseña configurada
  (sin revelar cuál aplica).
- `401 SESSION_REQUIRED`: sesión ausente o vencida.
- `403 ACCESS_DENIED`: acción no permitida para el rol o estado actual.
- `403 CSRF_INVALID`: cabecera CSRF ausente o inválida.
  Ambos `403` se distinguen por `code`; comparten la respuesta `AccessDeniedOrCsrf`.
- `422 RESET_LINK_INVALID`: enlace inválido, vencido, consumido o revocado.
- `409 IDEMPOTENCY_KEY_REUSED`: misma clave con contenido diferente.

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
