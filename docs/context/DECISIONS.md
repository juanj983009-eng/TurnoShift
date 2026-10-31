# REGISTRO DE DECISIONES — TURNOSHIFT

Este documento clasifica las decisiones clave del proyecto (`DEC-01` a `DEC-09`) distinguiendo su nivel de madurez: **Especificado** (definido en planificación), **Pendiente** (por confirmar u homologar) o **Verificado** (comprobado en código/ejecución).

| ID | Decisión | Estado | Descripción y Momento de Aplicación |
| :--- | :--- | :--- | :--- |
| **DEC-01** | Rúbrica y restricciones académicas | **Pendiente** | Pendiente de confirmación. Afecta compromisos académicos sin bloquear el desarrollo funcional básico. |
| **DEC-02** | Equipo y disponibilidad | **Pendiente** | Pendiente de confirmación por el propietario antes de asignar responsables y fechas definitivas. |
| **DEC-03** | Publicación vacía | **Especificado** | Permitida con confirmación explícita y motivo justificado, conservando turnos ya iniciados. Implementar en HU-14. |
| **DEC-04** | Advertencias y motivos | **Especificado** | Exige reconocimiento de advertencias concretas y motivos según el tipo de operación. Aplicar en borrador, publicaciones e intercambios. |
| **DEC-05** | Activación de políticas | **Especificado** | Activación inmediata, versionada y con evaluación de impactos. |
| **DEC-06** | Acceso inicial y recuperación | **Verificado (técnico) / Pendiente (operativo)** | Mecanismo técnico verificado en I1 (token criptográfico 256 bits, hash SHA-256 en BD, enlace con fragmento hash `#token=`, expiración 30 min, consumo y revocación atómicos). Canal y procedimiento operativo de entrega para piloto pendiente de concretar fuera de banda. |
| **DEC-07** | Idempotencia y previsualización | **Verificado (I1 + I2.1) / Especificado** | Mecanismo de idempotencia verificado en I1 (onboarding/enlace de operador) e I2.1 (puestos y configuración de organización mediante `BusinessIdempotentOperationExecutor` con actor `app_users`, huella canónica SHA-256, bloqueos `sp_getapplock` y replays 200/201/404/409 con `requestId` fresco). Previsualización pendiente para incrementos de planificación de turnos. |
| **DEC-08** | Detalles de OpenAPI y límites | **Verificado (I1 + I2.1)** | Contratos OpenAPI v0.1.0-i1 e I2.1 (v0.2.1-i2.1) completados y validados con Redocly CLI (0 errores). En I2.1 las rutas no emiten cabecera HTTP `X-Request-ID` y usan componentes dedicados `I21*`. Límites de campos físicos, Argon2id, HIBP k-anonymity, rate limiters en memoria por IP (5 req/60s), vencimiento de enlace (30 min) y expiración absoluta de sesión (8h) verificados. Límites de I2.2+ pendientes. |
| **DEC-09** | Entorno y operación piloto | **Pendiente** | Confirmar infraestructura, presupuesto y soporte antes de incorporar datos reales. |

| **PROP-OP-01** | Mecanismo de autenticación del operador | **Verificado** | Verificado en I1: tabla `platform_operators` (V10), autenticación por email y Argon2id, endpoints exclusivos `/api/v1/operator/auth/*`, cookie `TSESSIONID`, autoridad `ROLE_PLATFORM_OPERATOR` con frontera bidireccional estricta, revalidación continua y bootstrap condicional con `sp_getapplock`. |

*Nota: Ninguna propuesta arquitectónica o técnica se considera verificada en código hasta que exista implementación ejecutada y probada.*

---

## Decisiones técnicas adoptadas en I0 (2026-08-30)

### DEC-08-FIELDS — Límites de campos físicos (parcial, organizations, sites y positions)

Concretado al implementar V1/V2 y V12. Pendientes: colaboradores y contratos (I2.2+).

| Campo | Tabla(s) | Tipo SQL | Longitud | Reglas |
|---|---|---|---|---|
| `code` | `organizations`, `sites`, `positions` | `VARCHAR(32) COLLATE Latin1_General_100_BIN2` | 2–32 chars | Solo `[A-Z0-9-]`; sin espacios; comparación binaria exacta (sin depender de collation predeterminada del servidor) |
| `display_name` / `name` | `organizations`, `sites` | `NVARCHAR(150)` | ≥1 char no vacío (LTRIM/RTRIM en CHECK) | Unicode; la normalización NFC corresponde a la aplicación |
| `name` | `positions` | `NVARCHAR(100)` | ≥1 char no vacío (LTRIM/RTRIM en CHECK) | Unicode; la normalización NFC corresponde a la aplicación (V12) |
| `timezone_id` | `organizations` | `VARCHAR(64) COLLATE Latin1_General_100_CI_AS` | ≥1 char | Identificador de zona IANA; en I2.1 solo lectura (HU-05), mutación diferida hasta I3 |

**Normalización y comparaciones:**
- `code` usa collation `Latin1_General_100_BIN2` para comparación binaria exacta. No depende del collation por defecto del servidor ni de la base de datos.
- Unicidad de `code` en `organizations` es global (toda la plataforma). En `sites` y en `positions` es local (por `organization_id`).
- `display_name` y `name` son `NVARCHAR`; la normalización Unicode (NFC/NFD) debe aplicarse en capa de aplicación antes de persistir.
- `timezone_id`: En I2.1 (HU-05) la zona horaria se expone únicamente en modo lectura en `GET /api/v1/organization/configuration` para contextualizar operaciones; no se permite su modificación en este subincremento (diferido hasta I3).
- No existe truncamiento silencioso: las columnas tienen `CHECK` de longitud mínima y el tipo impone el máximo.

**Pendientes de DEC-08:** campos y límites físicos de colaboradores, contratos y jornadas (I2.2+).

### DEC-08-DEFER-FK-POLICY — FK de `current_policy_version_id` diferida

`organizations.current_policy_version_id` referenciará `planning_policy_versions.id`.  
Esta tabla se crea en el paso 5 del orden de implementación (Nota 07, §17) mientras `organizations` se crea en el paso 2, formando una referencia circular.

**Decisión:** La columna existe como `UNIQUEIDENTIFIER NULL` desde V1, pero la FK se añade en una migración posterior (cuando `planning_policy_versions` exista). Esto es explícito en el comentario de V1.  
La condición de referencia circular se resuelve creando primero las tablas, luego la FK, según Nota 07, §17: "Cuando existan referencias circulares, se crean las tablas y después las FKs". No se deshabilitan restricciones en producción.

### DEC-08-IDENTITY — Límites de campos físicos (identidad empresarial: roles, app_users, password_reset_tokens)

Concretado al implementar V4–V8. Pendientes: longitud de contraseña en texto plano, rate limiting, mecanismo del operador.

| Campo | Tabla(s) | Tipo SQL | Longitud | Reglas |
|---|---|---|---|---|
| `code` | `roles` | `VARCHAR(32) COLLATE Latin1_General_100_BIN2` | ≥1 char | Códigos técnicos estables del catálogo |
| `name` | `roles`, (ver display_name en app_users) | `NVARCHAR(150)` | ≥1 char no vacío | Capacidad física: hasta 150 unidades UTF-16; chars fuera del BMP ocupan 2 unidades |
| `display_name` | `app_users` | `NVARCHAR(150)` | ≥1 char no vacío | Mismo criterio que arriba |
| `email` | `app_users` | `NVARCHAR(254)` | ≥1 char | Capacidad física: hasta 254 unidades UTF-16. RFC 5321 establece ≤254 octetos ASCII para la parte local+dominio; esa restricción es semántica y la aplica la aplicación, no el tipo SQL |
| `normalized_email` | `app_users` | `NVARCHAR(254) COLLATE Latin1_General_100_BIN2` | ≥1 char | Comparación binaria exacta del valor canónico producido por la aplicación (V8). Capacidad física igual que `email` |
| `password_hash` | `app_users` | `VARCHAR(512)` | NULL o ≥1 char | Nunca vacío cuando está presente; nunca contraseña en texto plano |
| `purpose` | `password_reset_tokens` | `VARCHAR(32) COLLATE Latin1_General_100_BIN2` | — | Valores: `INITIAL_SETUP`, `PASSWORD_RESET` |
| `token_hash` | `password_reset_tokens` | `BINARY(32)` | 32 bytes fijos | SHA-256 del secreto; secreto original nunca almacenado |

**Normalización y comparaciones:**
- `normalized_email`: la **aplicación** aplica trim exterior y conversión a minúsculas, independiente de la configuración regional del servidor. No se eliminan puntos ni sufijos `+`. La BD almacena el valor ya normalizado y lo compara con `Latin1_General_100_BIN2` (comparación binaria exacta). La BD no normaliza ni valida sintácticamente el correo.
- `NVARCHAR(254)` es una capacidad física en unidades UTF-16, no equivale al límite RFC 5321 (que mide octetos ASCII). La conformidad con el estándar es responsabilidad de la capa de aplicación.
- Caracteres fuera del BMP (emoji, algunos símbolos) ocupan dos unidades UTF-16. No se promete que todos los caracteres de un correo ocupen una sola unidad.

**Pendientes de DEC-08:** longitud mínima/máxima de contraseña en texto plano, rate limiting de intentos de autenticación, mecanismo técnico del operador (PROP-OP-01).

### DEC-08-DEFER-FK-EMPLOYEE — FK de `employee_id` diferida en `app_users`

`app_users.employee_id` referenciará `employees.id` mediante FK empresarial `(organization_id, employee_id)`.
Esta FK no existe en V5 porque la tabla `employees` aún no se ha creado.

**Decisión:** La columna existe como `UNIQUEIDENTIFIER NULL` desde V5. La FK se añadirá en la migración correspondiente al introducir `employees` (I2+). Esta entrega por etapas es intencional: no se crean empleados ficticios para permitir cuentas sin vínculo, ni se compromete integridad referencial sin la tabla de destino.

Al introducir `employees`:
1. Se elimina el CHECK temporal `chk_app_users_employee_id_null_guard` (V7).
2. Se añade la FK empresarial `(organization_id, employee_id)` → `employees(organization_id, id)`.
3. El índice filtrado `uix_app_users_org_employee` (existente desde V5) garantizará la unicidad: un colaborador vinculado a como máximo una cuenta por organización.

### DEC-08-EMPLOYEE-ID-GUARD — Protección temporal de employee_id (V7)

Mientras `employees` no existe, V7 añade el CHECK `chk_app_users_employee_id_null_guard` sobre `employee_id IS NULL`.

**Decisión:** Si alguna fila ya tiene un valor no nulo, la migración V7 falla con error claro de restricción — no elimina ni convierte datos. Esto garantiza que no pueden existir UUIDs huérfanos sin integridad referencial mientras la tabla de destino no existe.

### DEC-08-NORMALIZED-EMAIL-COLLATION — Collation explícita en normalized_email (V8)

En V5, `normalized_email` heredaba la collation predeterminada del servidor. V8 aplica `COLLATE Latin1_General_100_BIN2` mediante: (1) drop del UNIQUE `uq_app_users_org_normalized_email`, (2) ALTER COLUMN con la collation explícita, (3) recreación del UNIQUE con el mismo nombre y alcance empresarial `(organization_id, normalized_email)`.

**Decisión:** La comparación de unicidad ahora es binaria exacta sobre el valor canónico que provee la aplicación. Si hay correos existentes incompatibles con BIN2, V8 falla con error claro — no altera ni elimina correos. La normalización sigue siendo responsabilidad de la aplicación.

### DEC-08-ARGON2ID — Parámetros Argon2id y benchmark

**Implementación:** `Argon2Password4jPasswordEncoder` (Spring Security 7 / Password4j 1.8.4).
Formato de hash: PHC string con variante, parámetros y salt aleatorio incrustados.
Sin cadena de filtros HTTP; solo `spring-security-crypto`.

**Objetivo de costo:** 500–1000 ms de mediana en el hardware de desarrollo.
Hardware: Temurin 21.0.10 en la máquina de desarrollo local.

**Candidatos evaluados:**

| # | Memoria | Iteraciones | Paralelismo | Hash | Mediana | Rango | Decisión |
|---|---|---|---|---|---|---|---|
| 1 | 64 MiB (65536 KiB) | 3 | 1 | 32 B | 257 ms | [228, 276] ms | Descartado: por debajo de 500 ms |
| 2 | 128 MiB (131072 KiB) | 3 | 1 | 32 B | 500 ms | [478, 529] ms | Elegido: dentro de 500–1000 ms |

**Parámetros elegidos:** 128 MiB, 3 iteraciones, paralelismo 1, hash de 32 bytes.

**Pendiente de producción:** El hardware de producción puede diferir significativamente. Repetir el benchmark antes del piloto en el servidor objetivo y ajustar si es necesario.

**Bloqueo activo:** No hay implementación de producción de `PasswordBlocklist`. El flujo de establecimiento/cambio de contraseña está bloqueado hasta integrar un proveedor real (HIBP k-anonymity o lista curada local). Ver `PasswordPolicy.validar()`.

**Política de contraseña adoptada (NIST SP 800-63B):**
- Mínimo: 15 puntos de código Unicode (medido con codePointCount, no String.length)
- Máximo: 128 puntos de código Unicode
- Se acepta cualquier carácter Unicode imprimible incluyendo espacios internos
- No se exigen clases artificiales (mayúsculas, dígitos, símbolos)
- No se recortan espacios exteriores de la contraseña
- Normalización NFC antes de medir y codificar

---

## Decisiones técnicas adoptadas en PoC-02 (2026-08-31)

### DEC-08-SPRING-SESSION — Spring Session JDBC 4.0.0 sobre SQL Server

**Implementación:** `spring-boot-starter-session-jdbc` (Spring Session 4.0.0, BOM Spring Boot 4.0.0).
Persiste sesiones HTTP en `SPRING_SESSION` y `SPRING_SESSION_ATTRIBUTES` mediante `JdbcIndexedSessionRepository`.

**Esquema:** extraído del JAR oficial `spring-session-jdbc:4.0.0` (`org/springframework/session/jdbc/schema-sqlserver.sql`) y adaptado para Flyway (V9) — sin separadores `GO`, sin `IF EXISTS`.

**Decisiones:**
- `spring.session.jdbc.initialize-schema=never` — Flyway es el único propietario del esquema.
- Timeout de inactividad: 1800 segundos (30 minutos). El límite absoluto de 8 horas se implementa en el login real mediante el atributo `authenticatedAt` en la sesión.
- Nombre de cookie: `TSESSIONID` (openapi.yaml, securitySchemes).
- Atributos de cookie: `HttpOnly=true`, `SameSite=Lax`, `Path=/api`, `Secure=true` (deshabilitable en perfil `local`).
- Sin `Domain`: la cookie es host-only (no se propaga a subdominios).
- Sin `Max-Age`: la cookie es de sesión del navegador (se elimina al cerrar).
- `tracking-modes=COOKIE`: sin jsessionid en URL.

**Pendientes:**
- Autenticación real: se almacenará `authenticatedAt` en la sesión para el límite absoluto de 8 horas.
- Invalidación de sesiones al cambiar `auth_version` del usuario.
- Limpieza periódica de sesiones expiradas (spring-session-jdbc incluye `JdbcIndexedSessionRepository.cleanUpExpiredSessions()`; pendiente de configurar scheduler).

### DEC-08-SECURITY-INFRA — Infraestructura Spring Security 7.0.0

**Implementación:** `spring-boot-starter-security` (Spring Security 7.0.0, BOM Spring Boot 4.0.0).

**Sin usuario predeterminado:** `UserDetailsServiceAutoConfiguration` excluida en `@SpringBootApplication`.
Nombre de clase verificado en el JAR real de `spring-boot-security:4.0.0` (`org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration`). Sin esta exclusión, Spring Boot generaría contraseña aleatoria en el log.

**CSRF:**
- Repositorio: `HttpSessionCsrfTokenRepository` (token en sesión, no en cookie readable).
- Header: `X-CSRF-TOKEN` (openapi.yaml).
- Handler: `CsrfTokenRequestAttributeHandler` — token como atributo de petición.
- Endpoint: `GET /api/v1/auth/csrf` → materializa el token diferido, retorna `{"token":"..."}` con `Cache-Control: no-store`.

**Manejadores de error (Problem+JSON, RFC 9457):**
- 401 `SESSION_REQUIRED` → `SessionRequiredEntryPoint` (implementa `AuthenticationEntryPoint`).
- 403 `CSRF_INVALID` → `TurnoShiftAccessDeniedHandler` cuando `exception instanceof CsrfException`.
- 403 `ACCESS_DENIED` → `TurnoShiftAccessDeniedHandler` para los demás casos.
- Sin redirecciones HTML. `Content-Type: application/problem+json`.

**Rutas configuradas:**
- Públicas (sin sesión): `GET /api/health`, `GET /api/v1/auth/csrf`.
- Con CSRF requerido (anónimas): `POST /api/v1/auth/login`, `POST /api/v1/auth/password-reset/inspect`, `POST /api/v1/auth/password-reset/complete`.
- Protegidas (sesión autenticada): todo lo demás.

**Desactivado:** `formLogin`, `httpBasic`, `rememberMe`, logout predeterminado, request cache, CORS permisivo.

**Sesión:** `changeSessionId` (protección contra session fixation). No `STATELESS`.

**Hallazgo de Spring Boot 4:**
`@AutoConfigureMockMvc` está en `org.springframework.boot.webmvc.test.autoconfigure` (módulo `spring-boot-webmvc-test`), no en `spring-boot-test-autoconfigure`. Con Spring Session JDBC activo en `RANDOM_PORT`, hay que usar la cookie del response (no `request.getSession(false)`) para correlacionar peticiones en MockMvc.

**Pendientes:**
- Login real (`AuthenticationManager` + `UserDetailsService` → `app_users`).
- Logout (`POST /api/v1/auth/logout`): invalidar sesión, limpiar cookie.
- `authenticatedAt` en sesión para límite absoluto de 8 horas.
- Revalidación de usuario, empresa, rol y estado en cada petición.
- PROP-OP-01: mecanismo de autenticación del operador.
- Rate limiting de autenticación.

---

## Decisiones técnicas adoptadas en I1-AUTH-CORE (2026-09-15)

### DEC-08-AUTH-CORE — Núcleo de autenticación empresarial multi-tenant

**Alcance:** Modelado, búsqueda y validación de credenciales previas a la exposición de endpoints HTTP.

**1. Normalización de entrada:**
- `organizationCode`: Trim exterior, mayúsculas con `Locale.ROOT`, longitud 2–32, patrón `^[A-Z0-9\\-]+$`. Se consulta exclusivamente con el valor normalizado.
- `normalizedEmail`: Reutiliza `EmailNormalizer` (NFC, trim Unicode, minúsculas `Locale.ROOT`), verificación de capacidad física UTF-16 (`NVARCHAR(254)`).
- `password`: Sin blocklist durante el login; sin comprobación de longitud mínima de 15 en login; rechazo de entrada vacía; límite máximo de 128 puntos de código Unicode; normalización NFC; espacios preservados íntegramente.

**2. Aislamiento multi-tenant en persistencia:**
- Adaptador `JdbcAuthenticationCandidateRepository` con SQL 100% parametrizado uniendo `app_users` y `organizations` mediante `o.id = u.organization_id`.
- Filtro estricto `WHERE o.code = ? AND u.normalized_email = ?`.
- No se acepta `organizationId` del cliente.
- El mismo correo normalizado puede coexistir en organizaciones distintas de forma completamente aislada. Un código de empresa A jamás recupera usuarios de B.

**3. Mitigación de diferencias de coste (Argon2id):**
- Se genera un hash señuelo (`decoyHash`) Argon2id una sola vez por instancia de `AuthenticateUser` a partir de un secreto efímero aleatorio (UUID).
- Si el usuario/empresa no existe, o si `password_hash` es `NULL` o vacío, se ejecuta `verifier.matches(nfcPassword, decoyHash)` para igualar aproximadamente el coste computacional con la ruta exitosa.
- Para usuario inactivo con hash real, no se omite el coste de verificación.
- No se afirma "tiempo constante"; se documenta como igualación aproximada del coste de cómputo.
- Hashes malformados provocan fail-closed sin revelar contenido.

**4. Respuestas tipadas y privacidad:**
- Resultado cerrado `AuthenticationOutcome` con enum `AuthenticationResult` (`SUCCESS`, `INVALID_CREDENTIALS`, `ORGANIZATION_ACCESS_DENIED`, `VALIDATION_FAILED`).
- Fallo genérico no distingue causas (usuario inexistente, contraseña errónea, cuenta inactiva o hash ausente devuelven `INVALID_CREDENTIALS`).
- Ni `AuthenticationCandidate` ni `AuthenticationOutcome` exponen contraseñas o hashes en sus representaciones `toString()`.

**5. Disponibilidad de base de datos y aislamiento técnico:**
- `JdbcAuthenticationCandidateRepository` no enmascara `DataAccessException` como `Optional.empty()`.
- Se introdujo `AuthenticationDataAccessException` (excepción técnica en dominio, sin Spring ni JDBC) para encapsular caídas, timeouts y problemas de red sin exponer SQL, credenciales ni hashes.
- El caso de uso `AuthenticateUser` propaga dicha excepción sin envolverla en `INVALID_CREDENTIALS`, permitiendo que la capa HTTP la convierta a `503 Service Unavailable`.
- `Optional.empty()` se reserva exclusivamente para casos de usuario u organización no encontrados o datos corruptos en fila (UUID malformado fail-closed).

**6. Serialización y contexto administrado (Jackson 3 / Spring Boot 4.0.0):**
- Se eliminó el fallback manual `new ObjectMapper()` en `SecurityConfig` para requerir inyección gestionada por el contenedor.
- Se identificó que Spring Boot 4.0.0 migró su autoconfiguración web/JSON a Jackson 3.x (`tools.jackson.databind.json.JsonMapper`).
- `SecurityConfig` y `SecurityProblemHandlers` se migraron para usar el bean gestionado `JsonMapper`, garantizando serialización RFC 9457 Problem Details sin alterar el comportamiento de producción ni romper la resolución de beans en el contexto de pruebas.

**7. Siguientes pasos (post-I1-AUTH-CORE):**
- Rate limiting antes de exponer login.
- Endpoint `POST /api/v1/auth/login`.
- `SecurityContextRepository` y guardado explícito.
- Rotación real del identificador.
- `authenticatedAt` y límite absoluto de ocho horas.
- Revalidación por `auth_version`, estado y rol.
- `GET /api/v1/auth/session` y `POST /api/v1/auth/logout`.
- Blocklist real solo para establecimiento/cambio de contraseña.

---

### DEC-08-AUTH-HTTP — Endpoint HTTP de autenticación empresarial y protección de sesión (2026-09-15)

**Alcance:** Exposición controlada del endpoint `POST /api/v1/auth/login`, limitación de tasa por IP, rotación de sesión, persistencia de `SecurityContext` y serialización de sesión JDBC.

**1. Limitador de tasa en memoria por IP (`LoginRateLimiter`):**
- Implementado en `InMemoryLoginRateLimiter` con ventana fija evaluada mediante `Clock` inyectable (100% determinista en pruebas).
- Valores por defecto: 5 intentos por ventana de 60 segundos por IP.
- **Resolución segura de IP (no evasión por cabeceras):** `LoginController` no confía en `X-Forwarded-For` ni en cabeceras reenviadas controlables por el cliente. Usa exclusivamente `HttpServletRequest.getRemoteAddr()`. Dos solicitudes con distinto `X-Forwarded-For` pero con el mismo `remoteAddr` comparten el mismo contador de limitación.
- Las peticiones que exceden la tasa se rechazan antes de invocar `AuthenticateUser`, protegiendo los recursos de CPU contra agotamiento por Argon2id.
- Respuestas 429 devuelven `Retry-After` con los segundos restantes para el reinicio de la ventana y cuerpo Problem Details RFC 9457 (`/problems/rate-limited`, `code="RATE_LIMITED"`).
- Autenticaciones exitosas restablecen el contador de la IP (`reset(clientIp)`).

**2. Validación estructural pura y compartida sin duplicación (400 vs 401):**
- Se extrajo `LoginInputValidator` y el record `LoginValidationError` (`field, code, message`) en el paquete `domain` (100% puro, sin dependencias de Spring, JDBC ni HTTP).
- Tanto `LoginController` como el caso de uso `AuthenticateUser` reutilizan las mismas reglas de validación sin duplicar código ni analizar cadenas de texto para inferir campos erróneos:
  - Petición ausente / campos obligatorios ausentes (`organizationCode`, `email`, `password`) → `code="REQUIRED"`.
  - Longitudes fuera de rango (`organizationCode` < 2 o > 32) → `code="INVALID_LENGTH"`.
  - Formato no permitido (`organizationCode` no alfanumérico con guiones, `email` sintácticamente inválido) → `code="INVALID_FORMAT"`.
  - Contraseña con más de 128 puntos de código Unicode NFC o correo que excede 254 unidades UTF-16 → `code="MAX_LENGTH_EXCEEDED"`.
- Los errores estructurales devuelven HTTP `400 VALIDATION_FAILED` con RFC 9457 Problem Details seguro y el arreglo `errors` (`FieldErrorDto`).
- `AuthenticationOutcome.result() == VALIDATION_FAILED` se preserva como objeto estructurado con `validationErrors()`.

**3. Prevención de fijación de sesión y persistencia:**
- Tras validación exitosa de credenciales en `AuthenticateUser`, se asegura la sesión con `request.getSession(true)` y se rota inmediatamente su identificador mediante `request.changeSessionId()`.
- Se construye un `SecurityContext` con `UsernamePasswordAuthenticationToken` asignando el rol (`ROLE_<ROL>`) y credenciales `null` (nunca se almacena la contraseña ni el hash en memoria de sesión).
- El contexto se persiste explícitamente mediante `SecurityContextRepository.saveContext(...)`, el cual está configurado en `SecurityConfig` usando `HttpSessionSecurityContextRepository`.
- Ante fallos de autenticación, validación o rate limiting, jamás se invoca `getSession(true)`, impidiendo la creación de sesiones anónimas espurias.

**4. Identidad del Principal y serialización JDBC:**
- `AuthenticatedIdentity` implementa `java.security.Principal` retornando `userId.toString()` (UUID de 36 caracteres). Esto asegura que `SPRING_SESSION.PRINCIPAL_NAME` (columna `VARCHAR(100)` de V9) almacene el identificador unívoco de usuario sin desbordamiento.
- `AuthenticatedIdentity` implementa `java.io.Serializable` con `serialVersionUID = 1L`, permitiendo que el `DefaultSerializer` de Spring Session JDBC serialice el `SecurityContext` en `SPRING_SESSION_ATTRIBUTES` sin `NotSerializableException`.

**5. Prevención de enumeración e indisponibilidad:**
- Los fallos por credenciales incorrectas, cuenta inexistente, cuenta inactiva o hash ausente devuelven un Problem Details `401 AUTHENTICATION_FAILED` genérico e idéntico en estructura y mensaje.
- Las organizaciones suspendidas o cerradas con credenciales válidas devuelven `403 ACCESS_DENIED`.
- La indisponibilidad técnica de persistencia (`AuthenticationDataAccessException`) se traduce a `503 SERVICE_UNAVAILABLE` sin filtrar SQL, contraseñas, hashes ni nombres de clases internas.

---

### DEC-08-AUTH-SESSION — Estado de sesión empresarial y cierre de sesión (2026-09-15)

**Alcance:** Exposición de `GET /api/v1/auth/session` y `POST /api/v1/auth/logout` conforme a `openapi.yaml`.

**1. Consulta de estado de sesión (`GET /api/v1/auth/session`):**
- Deriva la identidad exclusivamente del `SecurityContext` activo en el servidor (`AuthenticatedIdentity`).
- Si la sesión no existe, o el contexto no está autenticado, responde `401 SESSION_REQUIRED` con RFC 9457 Problem Details (`/problems/session-required`, `code="SESSION_REQUIRED"`).
- No crea, rota ni renueva sesiones HTTP (no invoca `getSession(true)` ni altera la cookie).
- Devuelve `200 OK` con `SessionResponse` (`user`, `organization`, `landingPage` según rol: `/app/mi-horario` para `COLABORADOR`, `/app/inicio` para los demás roles).
- Cabecera `Cache-Control: no-store`.
- Factoría compartida `SessionResponse.from(AuthenticatedIdentity)` reutilizada en `LoginController` y `SessionController` para eliminar duplicación de mapeo y lógica de `landingPage`.

**2. Cierre de sesión (`POST /api/v1/auth/logout`):**
- Requiere sesión autenticada activa (401 `SESSION_REQUIRED` si no lo está) y token CSRF válido (403 `CSRF_INVALID` si el token es inválido o falta la cabecera, preservando la sesión).
- Validación estricta del cuerpo de petición: cuerpo ausente, en blanco o `{}` permitido. Estructuras no objeto (arreglos, escalares), JSON sintácticamente inválido o cualquier propiedad adicional son rechazados con `400 VALIDATION_FAILED` (`/problems/validation-failed`), sin invalidar la sesión existente ni limpiar cookies.
- Tras validación exitosa:
  - Invalida la sesión HTTP en el servidor (`session.invalidate()`).
  - Limpia el `SecurityContext` mediante `SecurityContextHolderStrategy.clearContext()`.
  - Revoca la cookie de sesión emitiendo `Set-Cookie` para `TSESSIONID` con `Max-Age=0`, `Path=/api`, `HttpOnly`, `SameSite=Lax` y `Secure` dinámico según propiedad `${server.servlet.session.cookie.secure:true}` (`false` en perfil local, `true` en producción/staging).
  - Responde `204 No Content` sin cuerpo, con `Cache-Control: no-store`.
- Invariante de seguridad: tras el logout, cualquier llamada posterior a `GET /api/v1/auth/session` con la cookie previa devuelve `401 SESSION_REQUIRED`.

---

### DEC-08-SESSION-VALIDATION — Revalidación continua de sesión y límite absoluto de 8 horas (2026-09-15)

**Alcance:** Implementación del límite absoluto de sesión de 8 horas y verificación en cada petición protegida del estado vigente del usuario, rol, empresa y versión de autenticación en SQL Server.

**1. Límite absoluto de sesión de 8 horas (`authenticatedAt`):**
- Se almacena el atributo `SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE` (`authenticatedAt`) en la sesión HTTP en el momento exacto del login exitoso (`LoginController`) usando un `Clock` inyectable (epoch millis `Long`).
- El filtro de revalidación (`SessionRevalidationFilter`) evalúa este valor en cada petición protegida.
- Si el atributo no existe, es corrupto/no numérico, excede el tiempo futuro más allá de la tolerancia de desvío de reloj de 60 segundos (`CLOCK_SKEW_TOLERANCE`), o la diferencia respecto al tiempo actual supera las 8 horas (`DEFAULT_MAX_LIFETIME = Duration.ofHours(8)`):
  - Se invalida inmediatamente la sesión en el servidor (`session.invalidate()`).
  - Se limpia el `SecurityContext` (`clearContext()`).
  - Se revoca la cookie `TSESSIONID` (`Max-Age=0`, `Path=/api`, `HttpOnly`, `SameSite=Lax`, `Secure` dinámico).
  - Se responde `401 SESSION_REQUIRED` con RFC 9457 Problem Details (`/problems/session-required`).

**2. Revalidación continua en base de datos (`SessionRevalidationFilter` + `RevalidateSession`):**
- Filtro registrado como `OncePerRequestFilter` en `SecurityConfig` ubicado estratégicamente antes de `AuthorizationFilter.class`.
- Consulta SQL eficiente y parametrizada contra SQL Server (`JdbcSessionRevalidationRepository`) uniendo `app_users`, `organizations` y `roles` por `(u.id = ? AND u.organization_id = ?)`.
- **Sesión válida:** continúa la cadena de filtros sin alterar ni rotar el identificador de sesión.
- **Mutación de rol en caliente:** si el rol del usuario cambió en la base de datos (`candidate.roleCode() != principal.roleCode()`), se reconstruye `AuthenticatedIdentity` y las autoridades (`ROLE_<NUEVO_ROL>`) en el `SecurityContext`, se persiste explícitamente mediante `SecurityContextRepository.saveContext(...)` y se continúa la cadena de filtros sin rotar el ID de sesión.
- **Usuario inactivo / baja / auth_version alterada:** si `is_active = 0`, el usuario ya no existe o `candidate.authVersion() != principal.authVersion()`, se invalida la sesión del servidor, se limpia el contexto, se revoca la cookie `TSESSIONID` y se responde `401 SESSION_REQUIRED`.
- **Organización suspendida o cancelada:** si la empresa pasa a estar no activa (`service_status != 'ACTIVA'`), se invalida la sesión, se revoca la cookie `TSESSIONID` y se responde `403 ACCESS_DENIED` conforme al contrato OpenAPI.
- **Falla técnica de base de datos:** si ocurre `AuthenticationDataAccessException` (indisponibilidad de SQL Server o timeout de conexión), se responde HTTP `503 SERVICE_UNAVAILABLE` Problem Details sin filtrar detalles internos de infraestructura ni SQL y **sin invalidar la sesión del cliente**, de modo que al recuperarse el servicio la sesión continúe siendo válida.

**3. Exclusiones de filtro (`shouldNotFilter`):**
- Exclusión de rutas públicas no autenticadas: `GET /api/health`, `GET /api/v1/auth/csrf`, `POST /api/v1/auth/login`.
- Exclusión de `POST /api/v1/auth/logout`: garantiza que si una sesión ya expiró por 8 horas o la empresa fue suspendida, el usuario pueda ejecutar logout voluntario sin recibir errores 401/403 espurios, cerrando su sesión limpiamente con `204 No Content`.

---

### DEC-08-PASSWORD-BLOCKLIST — Proveedor Have I Been Pwned k-anonymity y política de fallo cerrado (2026-09-15)

**Alcance:** Implementación de producción de `PasswordBlocklist` para rechazar contraseñas comprometidas antes de codificarlas con Argon2id.

**1. Protocolo k-anonymity y preservación de privacidad:**
- Implementado en `HibpPasswordBlocklist` (`com.turnoshift.identity.infrastructure.client`).
- Usa SHA-1 exclusivamente porque el protocolo de rangos de HIBP lo exige (no reemplaza Argon2id para almacenamiento).
- Calcula SHA-1 en hexadecimal mayúsculo (40 caracteres) de la contraseña previamente normalizada en NFC (preservando espacios).
- Envía únicamente los primeros 5 caracteres hexadecimales (`prefix`): `https://api.pwnedpasswords.com/range/{PREFIJO}`.
- La contraseña en texto plano y el hash SHA-1 completo jamás salen del servidor.
- La comparación del sufijo (últimos 35 caracteres) se efectúa localmente en memoria de forma insensible a mayúsculas/minúsculas (`equalsIgnoreCase`).
- Se incluye la cabecera `Add-Padding: true` para prevenir inferencias sobre el número de resultados mediante el tamaño de la respuesta.
- Se define `User-Agent: TurnoShift/0.1` explícito y no sensible.
- `HttpClient` de Java 21 configurado con `Redirect.NEVER` y timeout explícito configurable (por defecto 2000 ms).

**2. Política estricta de fallo cerrado (fail-closed):**
- Si la contraseña se encuentra en el rango devuelto: `PasswordPolicy` agrega la violación `ViolacionPolitica.CONTRASENA_BLOQUEADA` (`IDENTITY_PASSWORD_BLOCKED`).
- Si la respuesta es exitosa (200 OK) y la contraseña no aparece: la contraseña continúa el flujo de validación.
- **Validación de cuerpo estricta:** Un cuerpo `null`, vacío (`""`) o en blanco (`body.isBlank()`, como espacios o saltos de línea sin contenido) es rechazado inmediatamente con `PasswordBlocklistUnavailableException`. Una respuesta válida debe contener al menos una línea no vacía con formato estricto `SUFFIX:COUNT`. Si ninguna línea cumple el formato, o si cualquier línea no vacía es corrupta (`![0-9A-Fa-f]{35}:[0-9]+`), se falla cerrado.
- Ante timeout, error de red (`IOException`), interrupción de hilo (`InterruptedException`), código HTTP distinto de 200 o respuesta corrupta: se lanza `PasswordBlocklistUnavailableException`.
- Ante `InterruptedException`, se restaura inmediatamente el estado con `Thread.currentThread().interrupt()` antes de lanzar la excepción.
- Una caída de HIBP nunca se interpreta como "contraseña segura". Esta excepción técnica de dominio será mapeada a `503 SERVICE_UNAVAILABLE` en los futuros endpoints de configuración o cambio de contraseñas.
- `PasswordPolicy.validar` aplica primero las reglas estructurales (vacío, mínimo 15, máximo 128 puntos de código); si la contraseña tiene violaciones estructurales, nunca invoca la lista de bloqueo (fail-fast, protegiendo recursos).

**3. Inyección y ciclo de vida en Spring Boot:**
- Registrado como `@Bean PasswordBlocklist` en `IdentityConfiguration` inyectando propiedades de `application.properties`:
  - `turnoshift.security.password-blocklist.hibp.url` (default `https://api.pwnedpasswords.com/range/`)
  - `turnoshift.security.password-blocklist.hibp.timeout-ms` (default `2000`)
  - `turnoshift.security.password-blocklist.hibp.user-agent` (default `TurnoShift/0.1`)
- No realiza ninguna llamada de red durante el arranque del contexto Spring.

**4. Privacidad e inmutabilidad en logs y excepciones:**
- `PasswordBlocklistUnavailableException` solo expone constructores sin mensaje arbitrario (`()` y `(Throwable cause)`). El mensaje público es constante y fijo (`[IDENTITY_BLOCKLIST_UNAVAILABLE] El servicio de verificación de contraseñas no está disponible temporalmente.`), impidiendo que llamadas externas o causas intermedias inyecten datos sensibles (contraseñas, hashes, prefijos o errores remotos) en el mensaje de error.

**5. Dependencia externa temporal:**
- Se adopta la API pública de Have I Been Pwned para la fase MVP. Se documenta como dependencia externa temporal sujeta a evaluación para reemplazo por una lista curada local y versionada antes de la fase de piloto/producción.

---

### DEC-08-TOKEN-LIFECYCLE — Ciclo de vida seguro de tokens de primer acceso (INITIAL_SETUP) (2026-09-15)

**Alcance:** Componentes de dominio, aplicación y persistencia para la emisión, inspección de solo lectura, revocación y consumo atómico de tokens de configuración de primer acceso (`INITIAL_SETUP`).

**1. Generación criptográfica y no persistencia de secretos:**
- **Entropía:** El secreto en texto plano se genera mediante `SecureRandom` con 256 bits de entropía (32 bytes).
- **Formato:** Codificación Base64 URL-safe sin relleno (`withoutPadding()`), con longitud fija de 43 caracteres.
- **Protección de secreto y datos sensibles:** El token en texto plano nunca se almacena en base de datos. Solo reside en memoria durante la emisión para su entrega al operador o usuario. `InitialSetupTokenEmission.toString()` redacta el secreto y todos los identificadores (`tokenId`, `organizationId`, `userId`, `rawToken`) a `[PROTECTED]`, evitando fugas en logs de depuración o trazas accidentales.
- **Almacenamiento por hash:** Se persiste exclusivamente el resumen criptográfico SHA-256 (32 bytes) en la columna `password_reset_tokens.token_hash BINARY(32)` con restricción `UNIQUE`.

**2. Emisión atómica y unívoca con bloqueo pesimista (`IssueInitialSetupToken`):**
- **Aptitud previa y serialización con `UPDLOCK, HOLDLOCK`:** `IssueInitialSetupToken` ejecuta dentro de `@Transactional` una consulta de aptitud específica (`findEligibilityForEmission`) que aplica `WITH (UPDLOCK, HOLDLOCK)` sobre `app_users` (y `organizations`). Solo se emite si `organizations.service_status` es estrictamente `'ACTIVA'`, el usuario está activo (`is_active = 1`) y aún no cuenta con contraseña configurada (`password_hash IS NULL`).
- **Garantía contra emisiones concurrentes:** Si múltiples hilos intentan emitir tokens simultáneamente para el mismo usuario, `UPDLOCK` serializa las transacciones. Cada transacción sucesiva revoca el token insertado por la anterior antes de insertar el suyo. Al finalizar todas las emisiones concurrentes, existe exactamente un único token activo (`consumed_at IS NULL AND revoked_at IS NULL`).
- **Vigencia estricta:** Tiempo de expiración configurado a exactamente 30 minutos desde la emisión (`expires_at = now + 30m`).
- **Revocación unívoca:** Dentro de la transacción se revocan todos los tokens no consumidos y no revocados previos (`purpose = 'INITIAL_SETUP'`) para ese `(organization_id, user_id)`, incluso si ya estaban vencidos por tiempo, impidiendo la coexistencia de múltiples tokens activos o estados ambiguos.

**3. Inspección en caliente de solo lectura (`InspectResetToken`):**
- **Validación estructural rápida:** Descarte inmediato de tokens `null`, en blanco o con longitud distinta de 43 caracteres, sin realizar consultas a la base de datos.
- **Búsqueda por hash:** Consulta por SHA-256 del token uniendo `password_reset_tokens`, `app_users` y `organizations`.
- **Validación del estado integral del sistema:** Un token se evalúa como inválido (`TokenInspectionResult.Invalid`) si:
  - Ya fue consumido (`consumed_at IS NOT NULL`) o revocado (`revoked_at IS NOT NULL`).
  - Ha expirado (`expires_at <= now`).
  - Su propósito es distinto de `INITIAL_SETUP` (en este incremento `PASSWORD_RESET` se rechaza).
  - El usuario fue desactivado (`is_active = 0`) o eliminado.
  - El usuario ya configuró una contraseña (`password_hash IS NOT NULL`), incluso si el registro del token aún no figuraba como consumido.
  - La organización fue suspendida o cerrada (`service_status != 'ACTIVA'`).

**4. Consumo atómico condicional con revalidación integral (`ConsumeResetToken`):**
- **Operación atómica en BD con JOINs obligatorios:** El consumo se ejecuta mediante una única sentencia `UPDATE` condicionada a:
  ```sql
  UPDATE prt
  SET prt.consumed_at = ?
  FROM password_reset_tokens prt
  JOIN app_users u ON prt.organization_id = u.organization_id AND prt.user_id = u.id
  JOIN organizations o ON prt.organization_id = o.id
  WHERE prt.token_hash = ?
    AND prt.purpose = 'INITIAL_SETUP'
    AND prt.consumed_at IS NULL
    AND prt.revoked_at IS NULL
    AND prt.expires_at > ?
    AND u.is_active = 1
    AND u.password_hash IS NULL
    AND o.service_status = 'ACTIVA'
  ```
- **Revalidación en caliente:** Si el usuario fue desactivado, la organización suspendida/cerrada o el usuario ya configuró contraseña antes de que el token sea consumido, la sentencia actualiza 0 filas, `consumed_at` permanece `NULL` en base de datos y se devuelve `TokenConsumptionResult.Failure`.
- **Garantía de concurrencia:** Bajo el bloqueo de fila exclusivo del motor SQL Server, solo una transacción concurrentemente en curso puede actualizar la fila. Intentos concurrentes o posteriores obtienen 0 filas actualizadas y retornan `Optional.empty()`.
- **Resultado de fallo genérico:** `TokenConsumptionResult.Failure` es un registro completamente vacío sin campos ni identificadores de usuario o empresa, previniendo ataques de enumeración u oráculos de estado.
- **Composición transaccional:** `ConsumeResetToken` participa en transacciones externas activas (`@Transactional` REQUIRED). El futuro caso de uso de cambio de contraseña envolverá «actualizar password_hash + consumir token + invalidar sesiones» en una única transacción atómica.

**5. Resiliencia técnica, RowMappers estrictos y no filtración (`TokenDataAccessException`):**
- Se eliminaron todos los bloques `catch (Exception)` silenciosos que retornaban `null` en los RowMappers.
- Si una fila presenta datos corruptos, incompletos o un valor de enumeración inesperado, se lanza `SQLException`, que Spring traduce a `DataAccessException` y el repositorio propaga como `TokenDataAccessException`. Los problemas técnicos jamás se confunden con tokens inválidos.
- Toda `DataAccessException` en `JdbcPasswordResetTokenRepository` se traduce a `TokenDataAccessException`.
- La excepción expone únicamente un mensaje seguro e inmutable (`[IDENTITY_TOKEN_DATA_ACCESS_ERROR]`) sin constructores públicos que admitan cadenas arbitrarias, imposibilitando la fuga de consultas SQL, hashes, identificadores o detalles internos de SQL Server.

---

## Decisiones técnicas adoptadas en I1-PASSWORD-SETUP-CORE (2026-09-15)

### DEC-08-PASSWORD-SETUP — Núcleo transaccional de establecimiento de primera contraseña

**Alcance:** Caso de uso interno de aplicación y persistencia para configurar la contraseña inicial con token `INITIAL_SETUP`, validación HIBP fuera de transacción e invalidación de sesiones vía `auth_version`.

**1. Separación estricta fuera de transacción (Red HIBP y CPU Argon2id):**
- La orquestación en `CompleteInitialPasswordSetup` ejecuta la validación estructural y la consulta al proveedor k-anonymity de Have I Been Pwned (`PasswordPolicy.validar`) **antes** de abrir la transacción de base de datos.
- La preparación de la contraseña (normalización NFC) y el cómputo intensivo de Argon2id (~500 ms de CPU) también se ejecutan fuera de la transacción.
- De esta manera, el sistema previene retener conexiones de base de datos o bloqueos SQL durante esperas de red externa o procesamiento criptográfico intensivo.

**2. Delimitación transaccional y rollback forzado (`TransactionalInitialPasswordSetupApplier`):**
- La fase de persistencia se delega al componente `@Transactional(propagation = Propagation.REQUIRED)` `TransactionalInitialPasswordSetupApplier`, ejecutando en una única transacción atómica:
  a) Consumo atómico condicional del token mediante `ConsumeResetToken.consume(rawToken)`.
  b) Si el consumo falla (token inválido, vencido, revocado, consumido o condiciones no vigentes), no se modifica `app_users` y se retorna `PasswordSetupResult.InvalidToken`.
  c) Si el consumo tiene éxito, se invoca `AppUserPasswordRepository.setInitialPassword(orgId, userId, passwordHash, now)`.
  d) Si el `UPDATE` a `app_users` afecta 0 filas (p. ej., si el usuario fue desactivado, la organización suspendida o la contraseña configurada entre operaciones concurrentes), se lanza `InitialPasswordSetupConditionFailedException`, forzando el rollback automático de la transacción. Con ello se asegura que el token no quede marcado como consumido (`consumed_at` permanece `NULL`).
  e) Si el `UPDATE` afecta exactamente 1 fila, se revocan los tokens `INITIAL_SETUP` restantes no consumidos y no revocados del usuario mediante `tokenRepository.revokeUnconsumedTokens`.
  f) El caso de uso orquestador captura `InitialPasswordSetupConditionFailedException` tras el rollback y devuelve `PasswordSetupResult.InvalidToken` de forma genérica.

**3. Persistencia defensiva en `app_users` (`JdbcAppUserPasswordRepository`):**
- Sentencia `UPDATE ... FROM ... JOIN` parametrizada al 100%:
  ```sql
  UPDATE u
  SET u.password_hash = ?,
      u.password_changed_at = ?,
      u.auth_version = u.auth_version + 1,
      u.updated_at = ?
  FROM app_users u
  JOIN organizations o ON u.organization_id = o.id
  WHERE u.organization_id = ?
    AND u.id = ?
    AND u.is_active = 1
    AND u.password_hash IS NULL
    AND o.service_status = 'ACTIVA'
  ```
- Validación defensiva de formato: rechazo inmediato con `IllegalArgumentException` si `passwordHash.length() > 512`.
- Parámetros `now` convertidos a `OffsetDateTime` en UTC para alineación con las columnas `DATETIMEOFFSET(3)` de SQL Server.

**4. Invalidación de sesiones mediante `auth_version` sin borrado físico:**
- Al incrementar `auth_version = auth_version + 1` de forma atómica en `app_users`, cualquier sesión preexistente del usuario (cuyo `AuthenticatedIdentity` posee el `auth_version` anterior) resulta invalidada en la siguiente petición protegida interceptada por `SessionRevalidationFilter`.
- No se realiza borrado físico en las tablas de Spring Session (`SPRING_SESSION`), preservando la separación de capas y evitando contención en la tabla de sesiones JDBC.

**5. Tipado de resultados y aislamiento de excepciones:**
- Interfaz sellada `PasswordSetupResult`:
  - `Success`: registro vacío sin campos ni datos sensibles.
  - `InvalidToken`: resultado genérico para cualquier condición anómala de token o cuenta.
  - `PolicyViolation(List<ViolacionPolitica> violations)`: contiene la lista estructurada de violaciones (p. ej. `CONTRASENA_BLOQUEADA`, `DEMASIADO_CORTA`) sin exponer la contraseña evaluada.
- Excepciones técnicas:
  - `PasswordBlocklistUnavailableException`: propagada cuando HIBP no está disponible o falla cerrado, para posterior traducción a HTTP 503.
  - `PasswordSetupDataAccessException`: traduce de forma estricta cualquier `DataAccessException` de Spring JDBC con mensaje fijo y seguro (`[IDENTITY_PASSWORD_SETUP_DATA_ACCESS_ERROR]`), sin filtrar SQL, hashes ni credenciales, para posterior traducción a HTTP 503.

---

## Decisiones técnicas adoptadas en I1-PASSWORD-SETUP-HTTP (2026-09-15)

### DEC-08-PASSWORD-SETUP-HTTP — Endpoints HTTP de inspección y completado de primera contraseña

**Alcance:** Exposición de los endpoints `POST /api/v1/auth/password-reset/inspect` y `POST /api/v1/auth/password-reset/complete` conforme al contrato OpenAPI v0.1.0-i1.

**1. Limitación de tasa en memoria por IP (`PasswordResetRateLimiter`):**
- Implementado en `InMemoryPasswordResetRateLimiter` con ventanas fijas evaluadas con `Clock` inyectable (UTC).
- Cuotas diferenciadas por IP:
  - `POST /api/v1/auth/password-reset/inspect`: 10 intentos por ventana de 60 segundos (`tryAcquireInspect`).
  - `POST /api/v1/auth/password-reset/complete`: 5 intentos por ventana de 60 segundos (`tryAcquireComplete`).
- **Resolución segura de IP:** usa exclusivamente `HttpServletRequest.getRemoteAddr()`, sin confiar en `X-Forwarded-For` ni cabeceras reenviadas del cliente.
- Las solicitudes que exceden la cuota devuelven HTTP `429 RATE_LIMITED` RFC 9457 Problem Details (`/problems/rate-limited`), cabecera `Retry-After` y `Cache-Control: no-store` antes de tocar la base de datos, la API de HIBP o el algoritmo Argon2id.
- Operaciones exitosas de completado restablecen el contador de la IP (`resetComplete(clientIp)`).

**2. Optimización por pre-inspección rápida en `complete`:**
- Antes de validar la política de contraseñas (que invoca a Have I Been Pwned) y antes de codificar la contraseña con Argon2id (~500ms de CPU), `POST /api/v1/auth/password-reset/complete` invoca `InspectResetToken.inspect(token)`.
- Si el enlace es inválido, vencido, consumido o revocado, o si la cuenta/organización no cumplen las condiciones, responde inmediatamente HTTP `422 RESET_LINK_INVALID` sin consumir recursos de red externa ni ciclos intensivos de CPU.

**3. Protección CSRF y exclusión de revalidación:**
- Ambos endpoints son accesibles sin sesión autenticada previa (`permitAll()` en `SecurityConfig`), pero requieren obligatoriamente la cabecera `X-CSRF-TOKEN`. Peticiones sin token o con CSRF inválido son interceptadas por Spring Security devolviendo `403 CSRF_INVALID`.
- Se añadieron ambas rutas a `SessionRevalidationFilter.shouldNotFilter` para evitar que peticiones con cookies de sesión residuales o corruptas sufran interferencia de revalidación durante el proceso de configuración de contraseñas.

**4. Prevención de fijación de sesión y no inicio automático:**
- El completado exitoso responde `204 No Content` con cabecera `Cache-Control: no-store` y no genera sesión HTTP ni establece `SecurityContext`. El usuario debe realizar el inicio de sesión explícito mediante `POST /api/v1/auth/login`.

**5. Mapeo estricto de errores a RFC 9457 Problem Details:**
- `400 VALIDATION_FAILED`: cuerpo ausente, campos `token` o `newPassword` faltantes/en blanco (`REQUIRED`), o violaciones de política de contraseña en `complete` (`IDENTITY_PASSWORD_TOO_SHORT`, `IDENTITY_PASSWORD_BLOCKED`, etc.) tipadas en el campo `newPassword`.
- `422 RESET_LINK_INVALID`: enlace no utilizable por cualquier causa (`detail: "El enlace ha vencido o ya fue utilizado."`, `errors: []`), sin revelar la causa concreta al cliente.
- `429 RATE_LIMITED`: cuota de IP superada con `Retry-After`.
- `503 SERVICE_UNAVAILABLE`: fallos de red/disponibilidad del proveedor HIBP (`PasswordBlocklistUnavailableException`) o indisponibilidad de SQL Server (`PasswordSetupDataAccessException`, `TokenDataAccessException`) sin filtrar datos internos ni SQL.

**6. Coherencia de contrato y robustez pre-controlador en HTTP:**
- **Alineación contractual en OpenAPI (`docs/api/openapi.yaml`):** se explicita que `INITIAL_SETUP` es el único propósito soportado y habilitado en esta versión. `PASSWORD_RESET` continúa definido en el esquema y enumeración para interoperabilidad futura (HU-04.2), pero se documenta que no está habilitado y que peticiones con tokens de dicho propósito devuelven `422 RESET_LINK_INVALID`. Validado con Redocly CLI: 0 errores, 0 advertencias.
- **Validación previa y parseo seguro en `PasswordResetController`:**
  - Validación de cabecera `Content-Type`: exige compatibilidad con `application/json`; rechaza `text/plain`, `application/xml` o ausencia de cabecera con `400 VALIDATION_FAILED` RFC 9457.
  - Cuerpos vacíos o en blanco detectados antes del parseador, devolviendo `400 VALIDATION_FAILED` con los campos requeridos (`REQUIRED`).
  - Parseo seguro mediante `ObjectMapper` administrado por Spring Boot, exigiendo objeto raíz (`isObject()`).
  - Validación de tipos JSON (`isTextual()`), rechazando tipos inválidos (números, booleanos, arrays, objetos) con código `INVALID_FORMAT` y sin eco de la entrada.
  - Manejador `@ExceptionHandler` acotado exclusivamente a `PasswordResetController` para `HttpMessageNotReadableException`, `HttpMediaTypeNotSupportedException` y `HttpMediaTypeNotAcceptableException`. Previene la delegación en controladores de error genéricos o páginas HTML de Spring Boot, unificando todas las respuestas de error de transporte en `application/problem+json`, `400 VALIDATION_FAILED` y `Cache-Control: no-store` con cero eco de datos sensibles ni trazas internas de Jackson.

---

### DEC-08-OPERATOR-PERSISTENCE — Persistencia y modelo de operadores de plataforma (2026-09-16)

**Alcance:** Separación física y de dominio de los operadores de plataforma respecto de las identidades empresariales multi-tenant.

**1. Separación absoluta de identidades (PROP-OP-01):**
- Un operador de plataforma administra la provisión de organizaciones y la emisión de enlaces de primer acceso (`INITIAL_SETUP`).
- El operador NO es un rol empresarial ni un valor del catálogo `roles` (V4), y jamás debe convivir en la tabla `app_users` (V5).
- La tabla `platform_operators` (V10) no contiene `organization_id`, no tiene clave foránea hacia organizaciones ni roles, y vive en un espacio completamente desacoplado del dominio multi-tenant.

**2. Modelo de persistencia (`V10__platform_operators.sql`):**
- `id UNIQUEIDENTIFIER DEFAULT NEWID()` (PK).
- `email NVARCHAR(254) NOT NULL`.
- `normalized_email NVARCHAR(254) COLLATE Latin1_General_100_BIN2 NOT NULL UNIQUE`: comparación binaria exacta sobre el correo normalizado en capa de aplicación, independiente de la configuración regional de SQL Server.
- `display_name NVARCHAR(150) NOT NULL`.
- `password_hash VARCHAR(512) NOT NULL`: hash Argon2id válido, nunca nulo ni vacío.
- `is_active BIT NOT NULL DEFAULT 1`: activación / suspensión del operador.
- `auth_version BIGINT NOT NULL DEFAULT 1`: control de revocación inmediata de sesiones activas.
- `lock_version BIGINT NOT NULL DEFAULT 0`: control de concurrencia optimista.
- `created_at` y `updated_at DATETIMEOFFSET(3) NOT NULL DEFAULT SYSUTCDATETIME()`.
- Restricciones CHECK para no vacío en `email`, `normalized_email`, `display_name` y `password_hash`.
- Restricciones CHECK para no negatividad en `auth_version` y `lock_version`.
- Índice `ix_platform_operators_active_lookup` sobre `(id, is_active, auth_version)` optimizado para la revalidación de sesión.

**3. Dominio puro y confidencialidad (`PlatformOperator`):**
- Registro inmutable con validación de invariantes defensiva.
- `toString()` redacta completamente `email`, `normalizedEmail` y `passwordHash` como `[PROTECTED]`, previniendo cualquier fuga accidental en logs, trazas de excepción o diagnósticos.

**4. Puerto y persistencia JDBC (`PlatformOperatorRepository`, `JdbcPlatformOperatorRepository`):**
- Consultas SQL 100% parametrizadas.
- Métodos atómicos: `save`, `updateAuthVersion` (actualización condicional `WHERE id = ? AND auth_version = ?`), `updateActiveStatus` y `count`.

---

### DEC-09-OPERATOR-AUTH-BOOTSTRAP — Autenticación y bootstrap seguro del primer operador de plataforma (2026-09-16)

**Alcance:** Núcleo de autenticación del operador de plataforma y aprovisionamiento seguro del primer operador mediante bootstrap condicional.

**1. Caso de uso aislado de autenticación (`AuthenticateOperator`):**
- Caso de uso completamente separado de `AuthenticateUser`, sin referencias a `organizations`, roles de negocio (`UserRole`), ni `app_users`.
- Búsqueda exclusiva en `platform_operators` por `normalized_email` usando la normalización uniforme de `EmailNormalizer` (NFC, Unicode trim, minúsculas `Locale.ROOT`).
- Mitigación estricta de coste por hash señuelo (`decoyHash`): se genera un hash Argon2id a partir de un secreto efímero (`UUID.randomUUID()`) una única vez por instancia de servicio. Si el operador no existe, el hash está ausente/vacío, o `verifier.matches` lanza excepción (hash malformado), el hash señuelo se consume explícitamente para mitigar ataques de temporización de enumeración.
- Comportamiento fail-closed indistinguible: correo no encontrado, contraseña errónea, cuenta inactiva o hash malformado retornan el mismo resultado `OperatorAuthenticationOutcome.invalidCredentials()`. Las cuentas inactivas verifican el hash real o consumen el señuelo si el hash es inválido, absorbiendo siempre un coste computacional equivalente.
- `OperatorDataAccessException` (fallo de persistencia/red/SQL Server) se propaga directamente como excepción técnica no verificada; jamás se enmascara como credencial inválida.

**2. Identidad desacoplada (`PlatformOperatorIdentity`):**
- Registro inmutable que implementa `java.security.Principal` y `java.io.Serializable` (`serialVersionUID = 1L`) para persistencia segura en `SPRING_SESSION_ATTRIBUTES`.
- `getName()` retorna `id.toString()` (UUID de 36 caracteres) para compatibilidad con la columna `PRINCIPAL_NAME VARCHAR(100)` de Spring Session JDBC.
- Contiene exclusivamente `id`, `displayName` y `authVersion`. No contiene `organizationId`, ni `UserRole`, ni tokens, ni correos.
- `toString()` redacta el `id` a `[PROTECTED]` y omite correos y hashes para evitar fugas en logs de diagnóstico o volcados de memoria.

**3. Provisión condicional y segura (`OperatorBootstrapRunner`):**
- Desactivado por defecto (`turnoshift.operator.bootstrap.enabled=false` en `application.properties`). Si está desactivado, no realiza consultas a la base de datos ni lee variables de entorno.
- Credenciales leídas exclusivamente de variables de entorno dedicadas del sistema: `TURNOSHIFT_OPERATOR_EMAIL`, `TURNOSHIFT_OPERATOR_DISPLAY_NAME`, `TURNOSHIFT_OPERATOR_PASSWORD`. Jamás se configuran valores por defecto ni se leen de archivos de propiedades.
- La contraseña se maneja como array de caracteres `char[]` y se limpia (`Arrays.fill(password, '\0')`) en el bloque `finally`.
- Validación rigurosa con `PasswordPolicy` (mínimo 15 caracteres, NFC) y comprobación contra `PasswordBlocklist` (Have I Been Pwned / lista local). Si el proveedor de blocklist no está disponible (`PasswordBlocklistUnavailableException`), la operación falla cerrado (`BootstrapConfigurationException`) y no se crea ningún operador.
- La excepción `BootstrapConfigurationException` es final, no expone constructores con causa (`Throwable cause`) y posee mensaje estático, previniendo que un fallo en el arranque imprima detalles internos de SQL Server, nombres de tablas o variables de entorno en la cadena causal.
- Cero eco en registros: mensajes de log estáticos y unificados; jamás se registran correos, contraseñas, hashes, valores de variables de entorno ni trazas internas de excepción.

**4. Exclusión concurrente con `sp_getapplock` (`TransactionalOperatorInserter`):**
- Para prevenir condiciones de carrera cuando múltiples instancias arrancan en paralelo, la inserción se encapsula en `TransactionalOperatorInserter` con `@Transactional`.
- Se ejecuta `sp_getapplock` con `@Resource = 'TurnoShift_OperatorBootstrap'`, `@LockMode = 'Exclusive'`, `@LockOwner = 'Transaction'`, `@LockTimeout = 10000`.
- Si el contexto transaccional JDBC no ha iniciado una transacción explícita a nivel de motor (`@@TRANCOUNT == 0`), se antepone `IF @@TRANCOUNT = 0 BEGIN TRANSACTION;` para asegurar que el bloqueo transaccional sea admitido válidamente por SQL Server sin código `-999`.
- Bajo el bloqueo exclusivo, se verifica `count() == 0`: solo la primera instancia inserta; instancias subsecuentes detectan que ya existe un operador y omiten la operación silenciosamente sin error.
- El bloqueo se libera de forma determinista y atómica cuando la transacción finaliza (commit o rollback).
- La prueba de concurrencia en integración (`OperatorBootstrapIT`) valida la serialización mediante `Future.get(timeout)` estricto sobre ambos hilos sin tolerar excepciones, confirmando la finalización limpia de ambas instancias y exactamente 1 registro persistido.

---

### DEC-09-OPERATOR-SECURITY-SESSION — Sesiones HTTP y frontera de seguridad para operadores de plataforma (2026-09-16)

**Alcance:** Autenticación HTTP, sesiones y cierre de sesión exclusivos para operadores de plataforma (`platform_operators`), compartiendo Spring Session JDBC y cookie `TSESSIONID`, pero manteniendo una frontera estricta e infranqueable respecto de usuarios empresariales (`app_users`).

**1. Contrato OpenAPI y separación de rutas (`docs/api/openapi.yaml`):**
- Endpoints exclusivos bajo el prefijo `/api/v1/operator/auth/`:
  - `POST /api/v1/operator/auth/login`: autenticación por `email` y `password` (sin `organizationCode`). Responde `200 OK` con `OperatorSessionResponse` (`id`, `displayName`, `landingPage: "/operator/inicio"`).
  - `GET /api/v1/operator/auth/session`: consulta de identidad activa del operador desde el contexto de seguridad. Responde `200 OK` con datos del operador y `Cache-Control: no-store`. Si no está autenticado, responde `401 SESSION_REQUIRED`.
  - `POST /api/v1/operator/auth/logout`: invalidación de sesión en servidor, limpieza de `SecurityContext`, revocación de cookie `TSESSIONID` (`Max-Age=0`) y respuesta `204 No Content` con `Cache-Control: no-store`.
- Esquema de seguridad `operatorSession` configurado con cookie `TSESSIONID` y rol exigido `ROLE_PLATFORM_OPERATOR`.
- Verificado con Redocly CLI: 0 errores, 0 advertencias.

**2. Rate limiting independiente y resolución de IP:**
- Componente `OperatorLoginRateLimiter` e implementación en memoria `InMemoryOperatorLoginRateLimiter`: cuota de 5 intentos por 60 segundos por IP, con `Clock` inyectable determinista.
- Peticiones que exceden la cuota se rechazan inmediatamente con `429 RATE_LIMITED` y cabecera `Retry-After` antes de invocar `AuthenticateOperator` (protegiendo los núcleos de CPU contra DoS por sobrecarga de Argon2id).
- Resolución de IP exclusivamente mediante `HttpServletRequest.getRemoteAddr()`, sin confiar en cabeceras manipulables por el cliente como `X-Forwarded-For`.
- Tras un login exitoso, el contador de la IP se restablece.

**3. Autenticación y rotación de sesión:**
- Controlador `OperatorLoginController`:
  - Valida formato de entrada con `OperatorLoginInputValidator` (email RFC 5322 simplificado <= 254 chars UTF-16, password no vacío <= 128 puntos de código). Entrada inválida produce `400 VALIDATION_FAILED` RFC 9457 con `errors`.
  - Invoca el caso de uso `AuthenticateOperator` con la contraseña en `char[]` (limpiado en `finally`).
  - Fallo de autenticación mapeado a `401 AUTHENTICATION_FAILED` unificado e indistinguible (operador inexistente, contraseña errónea, cuenta inactiva o hash corrupto).
  - Fallo técnico (`OperatorDataAccessException`) traducido de forma segura a `503 SERVICE_UNAVAILABLE` sin filtrar SQL ni trazas internas.
  - Éxito: rota el identificador de sesión con `request.changeSessionId()` para prevenir ataques de fijación de sesión.
  - Persistencia explícita en `SecurityContextRepository` (`HttpSessionSecurityContextRepository`) con principal `PlatformOperatorIdentity` y una única autoridad: `ROLE_PLATFORM_OPERATOR`.
  - Registro de marca temporal de inicio de sesión `authenticatedAt` (epoch millis `Long`) para el control de expiración absoluta.

**4. Frontera de autorización bidireccional estricta (`SecurityConfig`):**
- `/api/v1/operator/**` exige exclusivamente la autoridad `ROLE_PLATFORM_OPERATOR`. Un usuario empresarial autenticado (`ROLE_ADMINISTRADOR`, `ROLE_SUPERVISOR`, `ROLE_COLABORADOR`) que intenta acceder recibe `403 ACCESS_DENIED`.
- `/api/v1/auth/session` y `/api/v1/auth/logout` exigen roles empresariales (`ADMINISTRADOR`, `SUPERVISOR`, `COLABORADOR`). Un operador de plataforma que intenta invocar estos endpoints recibe `403 ACCESS_DENIED`.
- Regla restrictiva por defecto: `.anyRequest().denyAll()`.

**5. Revalidación polimórfica en `SessionRevalidationFilter`:**
- El filtro inspecciona el principal presente en `SecurityContext`:
  - Si es `AuthenticatedIdentity` (usuario empresarial): delega en `RevalidateSession` (valida usuario, tenant activo, mutación de roles).
  - Si es `PlatformOperatorIdentity` (operador): delega en `RevalidateOperatorSession`.
- `RevalidateOperatorSession`:
  - Verifica límite absoluto de 8 horas (`authenticatedAt`); si expiró, responde `401 SESSION_REQUIRED` y revoca la cookie.
  - Consulta en base de datos mediante `PlatformOperatorRepository.findById(id)`:
    - Operador inexistente, inactivo (`is_active = 0`) o con `auth_version` alterada: responde `401 SESSION_REQUIRED`, invalida la sesión y revoca la cookie `TSESSIONID`.
    - Error de persistencia (`OperatorDataAccessException`): responde `503 SERVICE_UNAVAILABLE` sin invalidar la sesión del operador ni filtrar detalles internos.
    - Operador activo y coincidente: continúa la cadena sin alterar la sesión.
- Exclusiones en `shouldNotFilter`: rutas públicas, login empresarial/operador y logout empresarial/operador (`POST /api/v1/operator/auth/logout`), permitiendo el cierre de sesión limpio incluso con sesión vencida o cuenta desactivada.

---

### DEC-09-ORGANIZATION-ONBOARDING — Incorporación asistida de organizaciones e idempotencia mono-transaccional (2026-09-16)

**Alcance:** Endpoint `POST /api/v1/operator/setup/organizations` para la creación atómica y asistida de una organización SaaS, su sede principal inicial y su primer administrador, con deduplicación rigurosa de idempotencia mediante `api_operations` y bloqueos jerárquicos de SQL Server.

**1. Esquema de persistencia de idempotencia (`V11__api_operations.sql`):**
- Tabla `api_operations` diseñada para almacenar el estado y resultado terminal de operaciones protegidas por `Idempotency-Key`:
  - Clave primaria `id UNIQUEIDENTIFIER`.
  - `actor_operator_id UNIQUEIDENTIFIER NULL` y `actor_user_id UNIQUEIDENTIFIER NULL`.
  - Clave de idempotencia `idempotency_key UNIQUEIDENTIFIER NOT NULL`.
  - `request_fingerprint VARBINARY(32) NOT NULL` (hash SHA-256).
  - `status VARCHAR(16)` (`IN_PROGRESS`, `COMPLETED`, `FAILED`), `outcome VARCHAR(16)` (`SUCCEEDED`, `REJECTED`, `TECHNICAL_ERROR`).
  - `http_status INT NULL`, `response_content_type VARCHAR(64) NULL`, `response_body NVARCHAR(MAX) NULL`, `response_location NVARCHAR(512) NULL`.
  - `created_at`, `completed_at`, `expires_at DATETIMEOFFSET(7) NOT NULL`.
- Restricciones y coherencia relacional:
  - Coherencia de actor `chk_api_operations_actor_coherence`: si `actor_operator_id IS NOT NULL`, `actor_user_id` y `organization_id` deben ser estrictamente `NULL`. Si `actor_user_id IS NOT NULL`, `actor_operator_id` debe ser `NULL` y `organization_id` obligatorio.
  - Coherencia de estado `chk_api_operations_status_coherence`: `status = 'COMPLETED'` exige `outcome` no nulo, `completed_at` no nulo y `http_status` entre 100 y 599.
  - Clave foránea compuesta `fk_api_operations_tenant_user`: `(organization_id, actor_user_id)` hacia `app_users(organization_id, id)`.
  - Índices únicos filtrados:
    - `uix_api_operations_operator` sobre `(actor_operator_id, idempotency_key)` WHERE `actor_operator_id IS NOT NULL`.
    - `uix_api_operations_user` sobre `(organization_id, actor_user_id, idempotency_key)` WHERE `actor_user_id IS NOT NULL`.
  - Índice de expiración `ix_api_operations_expiration` sobre `(expires_at, status)`.

**2. Huella canónica inequívoca (`RequestFingerprintCalculator`):**
- Para prevenir colisiones o ambigüedades derivadas de separadores (como saltos de línea o comas dentro de nombres de sedes o empresas), el cálculo de huella serializa el comando normalizado mediante codificación prefijada por longitud en octetos UTF-8:
  - Formato: `<len>:<val>;` para cada campo en orden estricto: `METHOD`, `PATH`, `SCHEMA_VERSION`, `orgCode` (normalizado), `orgDisplayName`, `timeZone`, `siteDisplayName`, `adminEmail` (normalizado), `adminDisplayName`.
  - Cálculo de hash SHA-256 (`MessageDigest.getInstance("SHA-256")`) sobre los bytes UTF-8 de dicha secuencia.

**3. Arquitectura mono-transaccional estricta:**
- Se descarta cualquier patrón de dos fases ("rollback de negocio y nueva transacción para registrar REJECTED") debido a que con `LockOwner = 'Transaction'`, el rollback libera los bloqueos de aplicación, abriendo una ventana de carrera.
- Cada resultado terminal se confina a una única transacción de base de datos (`@Transactional`):
  - **Éxito (201):** inserción atómica en `organizations`, `sites` (`code = 'PRINCIPAL'`), `app_users` (`password_hash = NULL`, `role_code = 'ADMINISTRADOR'`) y registro `COMPLETED / SUCCEEDED / 201` en `api_operations`; commit único.
  - **Rechazo determinista por código en uso (409):** detectado bajo bloqueo antes de crear ninguna entidad; registra `COMPLETED / REJECTED / 409` en `api_operations` con una plantilla de error segura y confirma la transacción. Las tablas de dominio (`organizations`, `sites`, `app_users`) permanecen intactas (0 filas).
  - **Fallo técnico:** rollback total de la transacción managed por Spring. Ninguna entidad ni registro de idempotencia se persiste en la base de datos.

**4. Bloqueos de aplicación jerárquicos anti-deadlock (`sp_getapplock`):**
- Para garantizar serialización estricta ante peticiones concurrentes idénticas o con códigos en competencia, se adquieren bloqueos con orden unívoco:
  1. **Lock 1 (Idempotencia):** `@Resource = 'TurnoShift_Idem_' + actorOperatorId + '_' + idempotencyKey`, modo `Exclusive`, `LockOwner = 'Transaction'`, timeout 10000ms.
  2. **Lock 2 (Código de organización):** `@Resource = 'TurnoShift_OnboardingOrgCode_' + normalizedCode`, modo `Exclusive`, `LockOwner = 'Transaction'`, timeout 10000ms.
- El segundo intento con la misma clave espera la liberación del bloqueo y reproduce el resultado completado (`Idempotency-Replayed: true`). Dos intentos con claves distintas y mismo código serializan en Lock 2, permitiendo a uno crear la organización y forzando al otro a registrar el rechazo determinista 409.

**5. Semántica de replay y expiración de claves:**
- **Replay exitoso:** devuelve HTTP 201 con la cabecera `Idempotency-Replayed: true`, el payload original y cabecera `X-Request-ID` fresca.
- **Replay de rechazo:** el cuerpo persistido en `api_operations.response_body` almacena únicamente la plantilla de error (`type`, `title`, `status`, `detail`, `code`, `errors`), sin campos efímeros como `requestId` ni `instance`. Al reproducir el rechazo, el controlador genera un `requestId` fresco y un `instance` actualizado, conservando la cabecera `Idempotency-Replayed: true`.
- **Reutilización indebida:** misma clave con huella diferente devuelve `409 IDEMPOTENCY_KEY_REUSED` sin cabecera de replay.
- **Vigencia:** las operaciones tienen un periodo de vigencia de 24 horas (`expires_at = now + 24h`). Intentar usar una clave que ha superado su expiración devuelve `409 IDEMPOTENCY_KEY_EXPIRED`.

**6. Configuración y contrato:**
- Propiedad `turnoshift.organization.default-max-active-employees` validada en `@PostConstruct` de `OrganizationProperties` (valor > 0 obligatorio, fallback seguro a 50).
- La cabecera `Location` se omite deliberadamente en la respuesta 201 mientras el endpoint de consulta `GET /api/v1/organizations/{id}` no esté implementado.
- No se realiza emisión de contraseña ni token `INITIAL_SETUP` en este subincremento; `app_users.password_hash` queda `NULL`, con `is_active = 1` y `auth_version = 1`.
- Probado exhaustivamente con 10 unitarias web y 12 pruebas de integración en `OperatorOrganizationOnboardingIT` sobre SQL Server 2022 real (Testcontainers).

---

### DEC-09-INITIAL-ACCESS-LINK — Emisión y reemplazo seguro del enlace de acceso inicial (2026-09-16)

**Alcance:** Endpoint `POST /api/v1/operator/setup/organizations/{organizationId}/initial-access-link` para la emisión del primer enlace de configuración de contraseña (`INITIAL_SETUP`) por un operador de plataforma autenticado (`ROLE_PLATFORM_OPERATOR`).

**1. No persistencia de secretos en reposo ni en idempotencia:**
- El secreto criptográfico en texto plano (256 bits de entropía, 43 caracteres Base64 URL-safe) solo reside en memoria y se entrega exclusivamente en la respuesta HTTP `200 OK` con cabeceras `Cache-Control: no-store` y `X-Request-ID`.
- En base de datos, `password_reset_tokens` almacena únicamente el hash SHA-256 (`token_hash BINARY(32)`).
- En la tabla de auditoría/idempotencia `api_operations`, `response_body` persiste exclusivamente metadatos sanitizados: `{"tokenId":"...","adminUserId":"...","expiresAt":"...","secretRedacted":true}`. Cero texto plano, cero URLs completas con fragmento de token y cero eco en logs (`InitialSetupTokenEmission.toString()` redacta el secreto y campos a `[PROTECTED]`).

**2. Semántica de idempotencia y replays seguros:**
- Un reintento con la **misma `Idempotency-Key`** y la misma huella no reproduce el secreto (al no estar almacenado) ni genera un nuevo enlace inesperado (lo que violaría la idempotencia y revocaría el enlace en manos del operador).
- Responde `409 Conflict` con código `INITIAL_LINK_ALREADY_EMITTED`, cabecera `Idempotency-Replayed: true` y un mensaje claro instruyendo al operador sobre cómo solicitar deliberadamente un enlace de reemplazo si perdió la respuesta.
- Si el intento previo fue un rechazo determinista, se reproduce la plantilla con un nuevo `requestId` y cabecera `Idempotency-Replayed: true`.
- Reuso con distinta huella devuelve `409 IDEMPOTENCY_KEY_REUSED`. Claves con más de 24 horas devuelven `409 IDEMPOTENCY_KEY_EXPIRED`.

**3. Emisión deliberada de enlace de reemplazo y control de concurrencia optimista:**
- Para reemplazar un enlace perdido o vencido, el operador envía una nueva solicitud con una **nueva `Idempotency-Key`** y la versión actualizada del administrador (`expectedVersion = lock_version`).
- La emisión revoca atómicamente los tokens `INITIAL_SETUP` anteriores no consumidos del administrador (`revoked_at = now`), incrementa `app_users.lock_version = lock_version + 1` y genera un nuevo secreto con 30 minutos de vigencia.
- Si `expectedVersion` no coincide con el `lock_version` actual de `app_users`, se rechaza con `409 CONCURRENT_MODIFICATION` y se registra `REJECTED` en `api_operations`.

**4. Arquitectura mono-transaccional y bloqueos jerárquicos anti-deadlock (`sp_getapplock`):**
- La operación se ejecuta en una única transacción de base de datos (`@Transactional`):
  1. Lock 1 (Idempotencia): `TurnoShift_Idem_<operatorId>_<idempotencyKey>`, modo `Exclusive`, `LockOwner = 'Transaction'`, timeout 10000ms.
  2. Verificación de existencia en `api_operations`.
  3. Lock 2 (Administrador objetivo): `TurnoShift_AdminSetup_<adminUserId>`, modo `Exclusive`, `LockOwner = 'Transaction'`, timeout 10000ms.
  4. Inspección combinada con bloqueo pesimista `UPDLOCK, HOLDLOCK` de organización y administrador en una única consulta SQL (descartando `404 RESOURCE_NOT_FOUND` si la organización o el administrador no pertenecen a la misma).
  5. Validaciones de transición de estado: organización activa, usuario activo, rol `ADMINISTRADOR` y `password_hash IS NULL` (rechazo `409 INVALID_STATE_TRANSITION` registrado como `REJECTED` en `api_operations`).
  6. Invocación de `IssueInitialSetupToken.issue` (revocación de previos e inserción de hash SHA-256).
  7. Avance atómico de `lock_version` en `app_users`.
  8. Inserción de `SUCCEEDED / 200` en `api_operations` y commit unificado.
  9. Manejo de fallos técnicos de infraestructura y rollback total (`503 SERVICE_UNAVAILABLE`):
     - Si la emisión, revocación o persistencia en `IssueInitialSetupToken` falla con `TokenDataAccessException`, `EmitInitialAccessLink` la captura y relanza como `OperatorDataAccessException(e)` en la frontera de aplicación.
     - Siendo una `RuntimeException`, Spring `@Transactional` ejecuta rollback total e inmediato (no se inserta token, no se confirma revocación previa, no se incrementa `lock_version` y no se persiste `api_operations`).
     - `OperatorInitialAccessLinkController` captura tanto `OperatorDataAccessException` como `TokenDataAccessException` en su `@ExceptionHandler`, respondiendo HTTP 503 `application/problem+json`, `Cache-Control: no-store`, `code: SERVICE_UNAVAILABLE`, `requestId` nuevo (tanto en cuerpo como en cabecera `X-Request-ID`) y cero exposición de SQL, causas, hashes, tokens o detalles de infraestructura.

**5. Formato de URL con fragmento `#`:**
- La URL se compone mediante `${turnoshift.auth.initial-setup-base-url}#token=${rawToken}` (por defecto `https://app.turnoshift.example/setup`, en local `http://localhost:5173/setup`).
- Al alojarse el secreto en el fragmento hash (`#`), los navegadores nunca transmiten el token en cabeceras `Referer` ni líneas de petición HTTP a servidores web o proxys intermedios.

