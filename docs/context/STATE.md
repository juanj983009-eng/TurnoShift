# ESTADO REAL DEL PROYECTO — TURNOSHIFT

## 1. Estado de Incrementos

| Incremento | Estado | Fecha |
|---|---|---|
| **I0 — Preparación** | **COMPLETADO** (dentro de su alcance) | 2026-08-30 |
| **I1 — Acceso seguro e incorporación asistida** | **COMPLETADO** | 2026-09-16 |
| **I2 — Gestión de personal y estructura organizativa** | **COMPLETADO** (I2.1–I2.4 backend/API, Frontend F1–F4 y verificación manual integrada del MVP completados) | 2026-09-25 |
| **I3 — Planificación de turnos** | **EN CURSO** (Subincrementos I3.1-a persistencia/migraciones e I3.1-b dominio/validación/JDBC COMPLETADOS; casos de uso I3.1-c, API y frontend pendientes) | 2026-10-29 |
| I4 – I7 | PENDIENTES | — |

> **Alcance de I0 completado:** ejecución local reproducible, base de persistencia
> `organizations`/`sites` con Flyway integrado a Spring Boot, y pruebas repetibles.
> No incluye: sesiones, autenticación ni aislamiento de API.

---

## 2. Hechos Verificados (con fecha)

### Backend — Spring Boot 4.0.0 + Java 21
- `GET /api/health` → HTTP 200 `{"status":"UP","service":"turnoshift-backend"}`. ✅
- Compilación con JDK Temurin 21 local (`C:\Users\juan9\.jdks\temurin-21.0.10`). ✅
- Maven Wrapper incluido. ✅
- Componentes implementados: módulo identity (V4–V8), reglas de dominio (EmailNormalizer, PasswordPolicy, PasswordBlocklist) y servicio criptográfico (Argon2PasswordService). ✅
- Infraestructura de seguridad (PoC-02): Spring Security 7.0.0, Spring Session JDBC 4.0.0, CSRF, Problem Details 401/403 con Jackson 3 JsonMapper administrado por Spring, endpoint `GET /api/v1/auth/csrf`. ✅
- Núcleo de autenticación multi-tenant (I1-AUTH-CORE): dominio (`AuthenticatedIdentity`, `AuthenticationOutcome`, `AuthenticationResult`, `AuthenticationCandidate`, `AuthenticationDataAccessException`), caso de uso (`AuthenticateUser` con mitigación de coste por hash señuelo Argon2id) y persistencia JDBC (`JdbcAuthenticationCandidateRepository` con aislamiento multi-tenant estricto, SQL parametrizado y propagación técnica de indisponibilidad de BD hacia 503 sin enmascarar). ✅ (2026-09-15)
- Endpoint HTTP de login (`POST /api/v1/auth/login`) y seguridad de sesión (I1-AUTH-HTTP):
  - Limitación de tasa por IP en memoria (`LoginRateLimiter`: 5 req/60s) con rechazo antes de Argon2id, cabecera `Retry-After` y Problem Details 429 `RATE_LIMITED`. Restablecimiento de contador tras login exitoso. ✅ (2026-09-15)
  - Resolución segura de IP para rate limiting: usa exclusivamente `HttpServletRequest.getRemoteAddr()`, sin confiar en `X-Forwarded-For` ni cabeceras reenviadas del cliente. Peticiones con spoofing de cabecera comparten la misma cuota de su IP real. ✅ (2026-09-15)
  - Validación estructural compartida y pura: componente de dominio `LoginInputValidator` y `LoginValidationError` reutilizado por `LoginController` y `AuthenticateUser` sin duplicación de reglas ni parseo de texto. Devuelve 400 `VALIDATION_FAILED` con `errors` RFC 9457 ante request ausente, campos obligatorios faltantes, código de org inválido (<2, >32, caracteres no permitidos), formato email inválido, capacidad UTF-16 excedida o password >128 puntos de código. ✅ (2026-09-15)
  - Rotación obligatoria del identificador de sesión (`request.changeSessionId()`) para mitigar fijación de sesión tras éxito. ✅ (2026-09-15)
  - Contexto de seguridad: `UsernamePasswordAuthenticationToken` con rol `ROLE_<ROL>`, credenciales `null` (sin passwords ni hashes en memoria de sesión) y persistencia explícita en `SecurityContextRepository` (`HttpSessionSecurityContextRepository`). ✅ (2026-09-15)
  - Prevención de enumeración de usuarios/tenants: fallo unificado e indistinguible 401 `AUTHENTICATION_FAILED` ante credenciales erróneas, usuario inexistente, hash ausente o cuenta inactiva. ✅ (2026-09-15)
  - Respeto de control de acceso de organización: organización suspendida o cerrada con credenciales válidas devuelve 403 `ACCESS_DENIED`. ✅ (2026-09-15)
  - Resiliencia técnica: mapeo seguro de `AuthenticationDataAccessException` a HTTP 503 `SERVICE_UNAVAILABLE` sin filtrar detalles internos de infraestructura ni sentencias SQL. ✅ (2026-09-15)
  - Prevención de sesiones espurias: peticiones fallidas o no autorizadas nunca invocan `getSession(true)`. ✅ (2026-09-15)
  - Serialización JDBC y Spring Session: `AuthenticatedIdentity` implementa `Principal` (`getName()` retorna `userId.toString()`, 36 caracteres, cumpliendo el límite `VARCHAR(100)` de `SPRING_SESSION.PRINCIPAL_NAME`) e implementa `Serializable` con `serialVersionUID = 1L` (evita `NotSerializableException`). ✅ (2026-09-15)
  - Jackson 3 / Spring Boot 4.0.0: `SecurityConfig` inyecta estrictamente el bean administrado `tools.jackson.databind.ObjectMapper` sin fallback `new ObjectMapper()`. ✅ (2026-09-15)
- Endpoints HTTP de sesión empresarial y cierre de sesión (I1-AUTH-SESSION):
  - `GET /api/v1/auth/session`: consulta de identidad activa derivada exclusivamente del `SecurityContext` (`AuthenticatedIdentity`), responde `200 OK` con `SessionResponse` (`user`, `organization`, `landingPage` personalizada por rol: `/app/mi-horario` para `COLABORADOR`, `/app/inicio` para los demás roles) y `Cache-Control: no-store`. Responde `401 SESSION_REQUIRED` ante sesión ausente o anónima. No crea ni renueva sesión. ✅ (2026-09-15)
  - `POST /api/v1/auth/logout`: requiere sesión activa y CSRF válido. Invalida sesión (`session.invalidate()`), limpia `SecurityContext`, revoca cookie `TSESSIONID` (`Max-Age=0`, `Path=/api`, `HttpOnly`, `SameSite=Lax`, `Secure` dinámico según propiedad `${server.servlet.session.cookie.secure:true}`) y responde `204 No Content` con `Cache-Control: no-store`. ✅ (2026-09-15)
  - Validación de cuerpo de logout: cuerpo ausente, en blanco o `{}` permitido; rechaza arrays, JSON malformado o propiedades adicionales con `400 VALIDATION_FAILED` sin invalidar sesión ni tocar cookies. ✅ (2026-09-15)
- Revalidación continua de sesión y límite absoluto de 8 horas (I1-SESSION-VALIDATION):
  - Límite absoluto de sesión de 8 horas (`SessionSecurityConstants.AUTHENTICATED_AT_ATTRIBUTE`): guardado en login (`LoginController`) con `Clock` inyectable (epoch millis `Long`); evaluado en cada interacción autenticada. Si falta, es corrupto, excede el futuro (>60s) o supera las 8 horas, se invalida la sesión en servidor, se limpia el `SecurityContext`, se revoca `TSESSIONID` (`Max-Age=0`) y se responde `401 SESSION_REQUIRED` RFC 9457 Problem Details. ✅ (2026-09-15)
  - Revalidación por petición contra base de datos (`SessionRevalidationFilter` + `RevalidateSession` + `JdbcSessionRevalidationRepository`): ubicado antes de `AuthorizationFilter`, consulta SQL Server uniendo `app_users`, `organizations` y `roles`. ✅ (2026-09-15)
  - Sesión válida: continúa la cadena de filtros sin alterar ni rotar el ID de sesión. ✅ (2026-09-15)
  - Mutación de rol en caliente: si el rol cambia en la base de datos, actualiza principal y autoridades (`ROLE_<NUEVO_ROL>`) en el `SecurityContext`, persiste explícitamente en `SecurityContextRepository` y continúa sin rotar la sesión. ✅ (2026-09-15)
  - Usuario dado de baja (`is_active = 0`), eliminado o `auth_version` alterada: invalida sesión, revoca cookie y responde `401 SESSION_REQUIRED`. ✅ (2026-09-15)
  - Organización no activa (`service_status != 'ACTIVA'`): invalida sesión, revoca cookie y responde `403 ACCESS_DENIED` cumpliendo el contrato OpenAPI. ✅ (2026-09-15)
  - Indisponibilidad de base de datos (`AuthenticationDataAccessException`): responde `503 SERVICE_UNAVAILABLE` sin filtrar detalles internos ni SQL, y sin invalidar la sesión del cliente. ✅ (2026-09-15)
  - Exclusiones de filtro (`shouldNotFilter`): rutas públicas y `POST /api/v1/auth/logout` excluidas para permitir cierre de sesión limpio incluso con sesión expirada o tenant suspendido. ✅ (2026-09-15)
- Lista de contraseñas bloqueadas Have I Been Pwned k-anonymity (I1-PASSWORD-BLOCKLIST):
  - `HibpPasswordBlocklist` (`com.turnoshift.identity.infrastructure.client`) implementa `PasswordBlocklist` calculando SHA-1 en hexadecimal mayúsculo de la contraseña normalizada en NFC. ✅ (2026-09-15)
  - Solo envía los primeros 5 caracteres hexadecimales del SHA-1 a `https://api.pwnedpasswords.com/range/{PREFIJO}` con cabeceras `Add-Padding: true` y `User-Agent: TurnoShift/0.1`. Contraseñas y hashes completos jamás salen del servidor. ✅ (2026-09-15)
  - Comparación de sufijo (35 caracteres) local e insensible a mayúsculas/minúsculas (`equalsIgnoreCase`). Si coincide, devuelve `true` y `PasswordPolicy` produce `ViolacionPolitica.CONTRASENA_BLOQUEADA` (`IDENTITY_PASSWORD_BLOCKED`). ✅ (2026-09-15)
  - Política de fallo cerrado (fail-closed): ante body null, vacío ("") o en blanco (espacios/saltos de línea), ausencia de al menos una línea válida SUFFIX:COUNT, respuesta corrupta, timeout, error de red, respuesta HTTP != 200 o interrupción de hilo (restaurando Thread.currentThread().interrupt()), se lanza PasswordBlocklistUnavailableException. ✅ (2026-09-15)
  - Comprobaciones estructurales previas en `PasswordPolicy` (longitud 15–128, vacío) evitan invocar la lista de bloqueo si la contraseña ya es inválida por formato (fail-fast). ✅ (2026-09-15)
  - `PasswordBlocklistUnavailableException` solo expone constructores sin mensaje arbitrario con texto inmutable fijo, garantizando logs y excepciones completamente limpios sin exposición de contraseñas, hashes, prefijos ni URLs con datos sensibles. ✅ (2026-09-15)
  - Cero llamadas de red durante el arranque del contexto de Spring Boot (`@Bean PasswordBlocklist`). ✅ (2026-09-15)
- Ciclo de vida seguro de tokens de primer acceso (I1-TOKEN-LIFECYCLE-CORE):
  - Generador criptográfico `SecureTokenGenerator` con 256 bits de entropía (32 bytes) emitidos por `SecureRandom`, codificado en Base64 URL-safe sin relleno (`withoutPadding()`, 43 caracteres). Almacenamiento y búsqueda exclusivos por hash SHA-256 (32 bytes) en `password_reset_tokens.token_hash BINARY(32)`. El secreto en texto plano jamás se persiste y `InitialSetupTokenEmission.toString()` lo redacta a `[PROTECTED]`. ✅ (2026-09-15)
  - Emisión atómica (`IssueInitialSetupToken`): valida aptitud previa (`service_status = 'ACTIVA'`, `is_active = 1`, `password_hash IS NULL`), fija expiración estricta a 30 minutos y revoca dentro de la misma transacción (`@Transactional`) todos los tokens previos no consumidos y no revocados del usuario, incluso si ya expiraron. ✅ (2026-09-15)
  - Inspección en caliente (`InspectResetToken`): descarte rápido de tokens malformados (!=43 caracteres) sin tocar la base de datos; consulta combinada de token, usuario y organización; evalúa inválido si el token fue consumido, revocado, expirado, con propósito distinto a INITIAL_SETUP, o si el usuario fue desactivado, la organización suspendida/cerrada, o el usuario ya configuró contraseña (`password_hash IS NOT NULL`). ✅ (2026-09-15)
  - Consumo atómico y condicional (`ConsumeResetToken`): consumo en una única sentencia `UPDATE ... WHERE token_hash = ? AND purpose = 'INITIAL_SETUP' AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > ?`. Bloqueo de fila exclusivo en SQL Server garantiza un único ganador ante concurrencia masiva (verificado con 10 hilos simultáneos). En caso de rechazo, devuelve `TokenConsumptionResult.Failure` completamente genérico y sin fugas de datos. ✅ (2026-09-15)
  - Persistencia segura (`JdbcPasswordResetTokenRepository`): SQL 100% parametrizado con integridad referencial compuesta `(organization_id, user_id)`. Mapeo seguro de `DataAccessException` a `TokenDataAccessException` técnica e inmutable sin filtrar consultas ni infraestructura. ✅ (2026-09-15)
- Establecimiento transaccional de primera contraseña (I1-PASSWORD-SETUP-CORE):
  - Caso de uso `CompleteInitialPasswordSetup`: validación HIBP y codificación Argon2id fuera de transacción. Aplicación atómica `TransactionalInitialPasswordSetupApplier` con consumo condicional de token, actualización de `password_hash`, avance atómico de `auth_version` y revocación de tokens restantes en una sola transacción. Rollback atómico si las condiciones de usuario/org no se cumplen. ✅ (2026-09-15)
- Endpoints HTTP de inspección y completado de primera contraseña (I1-PASSWORD-SETUP-HTTP):
  - Exposición de `POST /api/v1/auth/password-reset/inspect` y `POST /api/v1/auth/password-reset/complete` conforme a OpenAPI v0.1.0-i1. Alineación contractual validada: `INITIAL_SETUP` es el único propósito habilitado; `PASSWORD_RESET` no está habilitado y responde `422 RESET_LINK_INVALID`. Redocly CLI lint: 0 errores, 0 advertencias. ✅ (2026-09-15)
  - Limitación de tasa en memoria por IP (`PasswordResetRateLimiter`: 10 req/60s para inspect, 5 req/60s para complete con `Clock` determinista). Peticiones que exceden devuelven `429 RATE_LIMITED` con cabecera `Retry-After`. ✅ (2026-09-15)
  - Resolución segura de IP usando exclusivamente `HttpServletRequest.getRemoteAddr()` (ignorando `X-Forwarded-For`). ✅ (2026-09-15)
  - Pre-controlador HTTP robusto: validación estricta de `Content-Type` compatible con `application/json`, detección de cuerpos vacíos o en blanco, parseo seguro con `ObjectMapper` gestionado por Spring Boot, comprobación de nodo raíz objeto (`isObject()`), validación de tipos JSON (`isTextual()`) con código `INVALID_FORMAT`, y manejador `@ExceptionHandler` acotado al controlador para excepciones de transporte HTTP de Spring MVC (`HttpMessageNotReadableException`, etc.) devolviendo RFC 9457 `400 VALIDATION_FAILED`, `Cache-Control: no-store` y cero eco de secretos, tokens o trazas de Jackson. ✅ (2026-09-15)
  - Pre-inspección optimizada en `complete`: descarte con `422 RESET_LINK_INVALID` antes de invocar HIBP y Argon2id. ✅ (2026-09-15)
  - Respuestas RFC 9457 Problem Details (`RESET_LINK_INVALID`, `VALIDATION_FAILED`, `RATE_LIMITED`, `SERVICE_UNAVAILABLE`). ✅ (2026-09-15)
  - Exclusión en `SessionRevalidationFilter.shouldNotFilter` y protección obligatoria con `X-CSRF-TOKEN` (403 `CSRF_INVALID` sin CSRF). ✅ (2026-09-15)

- Persistencia y modelo de operadores de plataforma (I1-OPERATOR-PERSISTENCE):
  - Migración `V10__platform_operators.sql`: tabla `platform_operators` con `id` (UUID PK), `email`, `normalized_email` (Latin1_General_100_BIN2 UNIQUE), `display_name`, `password_hash` (VARCHAR(512)), `auth_version` (BIGINT, default 1), `lock_version` (BIGINT, default 0), `is_active` (BIT, default 1), `created_at`, `updated_at`. Restricciones CHECK no vacías y no negativas. Índice `ix_platform_operators_active_lookup` sobre `(id, is_active, auth_version)`. Aislamiento total sin `organization_id` ni claves foráneas hacia organizaciones, roles ni `app_users`. ✅ (2026-09-16)
  - Dominio puro `PlatformOperator` con validación exhaustiva de invariantes (UTF-16, longitudes, versiones) y `toString()` redactando credenciales y correos a `[PROTECTED]`. ✅ (2026-09-16)
  - Excepción técnica `OperatorDataAccessException` con mensaje constante e inmutable sin filtración de SQL ni datos sensibles. ✅ (2026-09-16)
  - Puerto `PlatformOperatorRepository` y adaptador `JdbcPlatformOperatorRepository` con consultas parametrizadas por ID y email binario exacto BIN2, actualización condicional de `auth_version`, conmutación de `is_active`, conteo y mapeo seguro de errores JDBC. ✅ (2026-09-16)

- Autenticación y bootstrap seguro de operador de plataforma (I1-OPERATOR-AUTH-CORE-BOOTSTRAP):
  - Dominio puro: `PlatformOperatorIdentity` implementa `Principal` (`getName()` = UUID) y `Serializable` (`serialVersionUID = 1L`) con `toString()` que redacta el `id` a `[PROTECTED]`; `OperatorAuthenticationOutcome` y `OperatorAuthenticationResult` para resultados fuertemente tipados. ✅ (2026-09-16)
  - Caso de uso `AuthenticateOperator`: búsqueda exclusiva en `platform_operators` por `normalized_email` (`EmailNormalizer.normalizar`), mitigación estricta de temporización mediante consumo de hash señuelo (`decoyHash`) Argon2id efímero ante operador no encontrado, hash nulo/vacío o hash malformado/excepción del verificador (tanto para cuentas activas como inactivas). Falla cerrado indistinguible (`INVALID_CREDENTIALS`), y propaga `OperatorDataAccessException` directamente ante error técnico. ✅ (2026-09-16)
  - Bootstrap seguro `OperatorBootstrapRunner` (ApplicationRunner) condicionado por `turnoshift.operator.bootstrap.enabled=true` (desactivado por defecto). Lee credenciales exclusivamente de `TURNOSHIFT_OPERATOR_EMAIL`, `TURNOSHIFT_OPERATOR_DISPLAY_NAME`, `TURNOSHIFT_OPERATOR_PASSWORD`. Manejo de contraseña en `char[]` con limpieza en bloque `finally`. Validación con `PasswordPolicy` y `PasswordBlocklist` (fail-closed con `BootstrapConfigurationException` fija y sin causa encadenada ante indisponibilidad de HIBP o credenciales inválidas). ✅ (2026-09-16)
   - Exclusión concurrente real `TransactionalOperatorInserter`: `@Transactional` con `sp_getapplock` (`TurnoShift_OperatorBootstrap`, `Exclusive`, `LockOwner = 'Transaction'`, timeout 10s) con guardia `IF @@TRANCOUNT = 0 BEGIN TRANSACTION;`. Si ya existe operador, omite la inserción silenciosamente sin error. Captura acotada de `OperatorDataAccessException` en `count()` y `save()`, registrando un mensaje fijo seguro y lanzando `BootstrapConfigurationException` sin causa encadenada (evitando filtraciones de SQL, URL de conexión o secretos). ✅ (2026-09-16)

- Autenticación HTTP, sesiones y frontera de seguridad para operadores (I1-OPERATOR-SECURITY-SESSION):
  - Endpoints HTTP exclusivos: `POST /api/v1/operator/auth/login`, `GET /api/v1/operator/auth/session` y `POST /api/v1/operator/auth/logout` conforme a OpenAPI v0.1.0-i1 (Redocly: 0 errores). ✅ (2026-09-16)
  - Rate limiting por IP en memoria (`InMemoryOperatorLoginRateLimiter`: 5 req/60s con `Clock` determinista) que rechaza antes de Argon2id con `429 RATE_LIMITED` y `Retry-After`. Resolución estricta de IP mediante `HttpServletRequest.getRemoteAddr()`. ✅ (2026-09-16)
  - Validación de entrada con `OperatorLoginInputValidator`: email RFC 5322 simplificado <= 254 chars UTF-16, password <= 128 code points. Rechazo con 400 `VALIDATION_FAILED` RFC 9457 y detalle de campos. ✅ (2026-09-16)
  - Rotación real de sesión (`request.changeSessionId()`) en login exitoso, mitigando fijación de sesión. Persistencia explícita en `SecurityContextRepository` con `PlatformOperatorIdentity` y única autoridad `ROLE_PLATFORM_OPERATOR`. Registro de `authenticatedAt` para expiración absoluta de 8 horas. ✅ (2026-09-16)
  - Fallo de login unificado e indistinguible 401 `AUTHENTICATION_FAILED` (usuario no encontrado, clave errónea, cuenta inactiva o hash corrupto). Resiliencia 503 ante `OperatorDataAccessException` sin fugas. ✅ (2026-09-16)
  - Sesión (`GET`) responde datos seguros del operador y `landingPage: "/operator/inicio"` con `Cache-Control: no-store`. Logout (`POST`) invalida sesión, limpia contexto y revoca cookie `TSESSIONID` (`Max-Age=0`). ✅ (2026-09-16)
  - Revalidación continua polimórfica en `SessionRevalidationFilter` (`RevalidateOperatorSession`): evalúa límite de 8 horas, consulta operador en BD por ID, `is_active` y `auth_version`. Revocación inmediata con 401 y borrado de cookie si está inactivo o cambió versión; 503 preservando sesión ante fallo de BD. ✅ (2026-09-16)
  - Frontera bidireccional estricta en `SecurityConfig`: `/api/v1/operator/**` exige `ROLE_PLATFORM_OPERATOR`; rutas de sesión/logout de negocio exigen roles empresariales; acceso cruzado denegado con 403 `ACCESS_DENIED`. ✅ (2026-09-16)
- Incorporación asistida de organizaciones e idempotencia mono-transaccional (I1-ORGANIZATION-ONBOARDING):
  - Migración `V11__api_operations.sql`: tabla `api_operations` para persistencia y control de concurrencia de peticiones idempotentes, con coherencia de actor `chk_api_operations_actor_coherence`, coherencia de estado `chk_api_operations_status_coherence` y FK compuesta hacia `app_users`. ✅ (2026-09-16)
  - Huella canónica inequívoca `RequestFingerprintCalculator`: serialización prefijada por longitud (`<len>:<val>;`) en UTF-8 y hash SHA-256 BINARY(32) inmune a ambigüedades por separadores de texto. ✅ (2026-09-16)
  - Validador estructural puro `IncorporateOrganizationInputValidator`: verifica Idempotency-Key UUID, código de organización (2-32 caracteres `[A-Z0-9-]`), displayNames (1-150), zona horaria IANA válida y correo RFC 5322 simplificado (<=254 unidades UTF-16). ✅ (2026-09-16)
  - Caso de uso mono-transaccional `IncorporateOrganization`: adquiere bloqueos jerárquicos de aplicación (`sp_getapplock`) por idempotencia (`TurnoShift_Idem_...`) y por código (`TurnoShift_OnboardingOrgCode_...`). Detecta claves expiradas (>24h, 409 `IDEMPOTENCY_KEY_EXPIRED`), reuso con distinta huella (409 `IDEMPOTENCY_KEY_REUSED`) y reproduce resultados terminales previos con cabecera `Idempotency-Replayed: true`. ✅ (2026-09-16)
  - Creación y rechazo mono-transaccionales: ante código duplicado, registra `REJECTED/409` en `api_operations` y confirma la transacción sin crear entidades de dominio (0 filas en `organizations`, `sites`, `app_users`). Ante código libre, crea la organización, la sede 'PRINCIPAL' y el administrador (`password_hash = NULL`, `role_code = 'ADMINISTRADOR'`), registra `SUCCEEDED/201` y confirma en un único commit. Ante fallo técnico, rollback total sin filas residuales. ✅ (2026-09-16)
  - Controlador `OperatorOrganizationSetupController`: endpoint `POST /api/v1/operator/setup/organizations`. Omite `Location` mientras no exista `GET /api/v1/organizations/{id}`. En replays de rechazos, genera nuevos `requestId` e `instance` dinámicos sobre la plantilla persistida en BD. ✅ (2026-09-16)
- Emisión y reemplazo seguro del enlace de acceso inicial (I1-INITIAL-ACCESS-LINK-HTTP):
  - Endpoint exclusivo para operadores: `POST /api/v1/operator/setup/organizations/{organizationId}/initial-access-link` conforme a OpenAPI v0.1.0-i1 (Redocly: 0 errores). ✅ (2026-09-16)
  - Secreto efímero y no persistido: token aleatorio de 256 bits (43 caracteres Base64 URL-safe) entregado exclusivamente en la respuesta HTTP `200 OK` bajo `Cache-Control: no-store`. En base de datos se almacena únicamente el hash SHA-256 `BINARY(32)` en `password_reset_tokens`. En `api_operations` se almacena `response_body` sanitizado (`{"secretRedacted":true}`) sin contraseñas, tokens ni URLs completas. ✅ (2026-09-16)
  - Idempotencia con rechazo seguro en replays: reintentos con misma clave e idéntica huella responden 409 `INITIAL_LINK_ALREADY_EMITTED` con `Idempotency-Replayed: true`, sin recrear el secreto ni generar un enlace colateral que revocaría el previo. Clave reutilizada con distinta huella responde 409 `IDEMPOTENCY_KEY_REUSED`; clave expirada (>24h) responde 409 `IDEMPOTENCY_KEY_EXPIRED`. ✅ (2026-09-16)
  - Reemplazo deliberado con control optimista de concurrencia: emite nuevo enlace con nueva `Idempotency-Key` si `expectedVersion` coincide con `app_users.lock_version`, revocando atómicamente tokens `INITIAL_SETUP` previos e incrementando `lock_version`. Si la versión no coincide, rechaza con 409 `CONCURRENT_MODIFICATION`. ✅ (2026-09-16)
  - Bloqueos jerárquicos anti-deadlock (`sp_getapplock`): Lock 1 por idempotencia (`TurnoShift_Idem_...`), Lock 2 por administrador (`TurnoShift_AdminSetup_...`), e inspección pesimista con `UPDLOCK, HOLDLOCK` de la relación organización-usuario. Ejecución mono-transaccional atómica con commit único o rollback completo ante fallos técnicos. `TokenDataAccessException` se traduce a `OperatorDataAccessException`, respondiendo HTTP 503 Problem Details con nuevo `X-Request-ID` y sin detalles internos. ✅ (2026-09-16)
  - Formato URL seguro con fragmento (`#token=`): el secreto se ubica en el fragmento hash (`https://app.turnoshift.example/setup#token=...`) para evitar filtraciones en cabeceras `Referer` o logs de red/proxys intermedios. ✅ (2026-09-16)

### Frontend — React 19 + TypeScript + Vite 6
- `npm run build` → 0 errores, 72 módulos transformados, `dist/` generado. ✅ (2026-09-25)
- **Subincremento completado: Frontend F1 — Autenticación y Layout (2026-09-25):**
  - **F1.1 — Proxy de desarrollo y tipos base/API:**
    - Configuración de proxy de desarrollo en `frontend/vite.config.ts` para redirigir peticiones `/api` a `http://localhost:8081` durante `vite dev`.
    - Tipos TypeScript en `src/types/api.ts` para RFC 9457 Problem Details (`ProblemDetails`, `FieldError`, clase `ApiProblemError` con soporte para fallos de red seguros) y en `src/types/auth.ts` (`SessionResponse`, `SessionUser`, `SessionOrganization`, `UserRole`, `LoginRequest`).
    - Cliente HTTP centralizado (`src/api/client.ts`): gestión de cookies de sesión HttpOnly (`credentials: 'include'`), inyección de cabecera `X-CSRF-TOKEN` solo en memoria volátil para métodos de escritura, y mapeo de errores HTTP a `ApiProblemError`. ✅
  - **F1.2 — Servicio de autenticación y AuthContext:**
    - Módulo de API (`src/api/authApi.ts`): `getCsrfToken`, `getSessionState`, `login`, `logout`.
    - Contexto reactivo de autenticación (`src/context/AuthContext.tsx`): bootstrap automático al montar (obtención de token CSRF anónimo y consulta de sesión activa), login empresarial con renovación de CSRF tras rotación de ID de sesión (`changeSessionId`), logout seguro invalidando sesión y limpiando estado local únicamente ante éxito o 401 (preservando estado ante fallos de red para permitir reintento).
    - Invariante de seguridad estricta: ningún token, credencial ni dato de sesión se persiste en `localStorage` o `sessionStorage`. ✅
  - **F1.3 — Enrutador SPA nativo, layout base y navegación visual:**
    - Enrutador nativo sin librerías externas (`src/components/routing/Router.tsx`): basado en Web History API (`Router`, `useRouter`, `<Link>`), sincronización con eventos `popstate` y guardias de rol.
    - Layout empresarial responsivo alineado con Stitch:
      - `AppHeader`: contexto/breadcrumb (`TurnoShift / Operaciones / ...`), pastilla de organización con zona horaria oficial (`timeZone`), indicador de rol (`Administrador`, `Supervisor`, `Colaborador`) y botón para drawer móvil.
      - `AppSidebar`: barra lateral clara (240px), glifo `TS` ámbar (`#b45309`), sección `OPERACIONES`, enlaces condicionados por rol con pastilla activa en ámbar sólido, distintivo "Consulta" para rol Supervisor en equipo, tarjeta inferior con organización, y avatar de usuario con iniciales, rol y botón de logout seguro.
      - `AppLayout`: contenedor responsivo flex/grid con drawer móvil colapsable y backdrop oscurecido.
      - Vistas operativas: `HomePage` (`/app/inicio`) con saludo y datos reales de sesión (cero métricas ficticias, sin falsos ERP/biometría ni asistencias inventadas); `ForbiddenPage` (`/acceso-restringido`) para desvíos de accesos no autorizados; y `PlaceholderPage` para módulos futuros (`/app/configuracion`, `/app/equipo`, `/app/mi-horario`) explicando el alcance sin tablas ni formularios simulados. ✅
  - **F1.4 — Login real, seguro y accesible:**
    - `LoginPage` conectado con `useAuth().login()` y `useRouter()`. Ruta oficial de interfaz: `/acceso` (alias `/login` redirige automáticamente a `/acceso`).
    - Campos reales de acceso: `organizationCode`, `email` y `password`.
    - Manejo seguro de errores HTTP y RFC 9457: 401 genérico indistinguible sin enumeración de usuario/tenant, 429 sobrio sin tiempos inventados, 503 y error de red con mensaje de servicio no disponible y reintento, 400 mapeando solo campos reconocidos, y banner amigable de sesión expirada que se limpia ante interacción del usuario.
    - Botón conmutador para mostrar/ocultar contraseña accesible por teclado (sin `tabIndex={-1}`, con foco nativo secuencial y `aria-label` dinámico).
    - Exclusiones estrictas del diseño Stitch aplicadas: sin checkbox de recordar dispositivo, sin SSO, sin versiones técnicas ni certificaciones TLS, sin enlaces ficticios a funcionalidades no implementadas, y sin simulador de estados. ✅
  - **F1.5 — Establecimiento de contraseña inicial desde enlace seguro (HU-04.1):**
    - Ruta pública `/setup` en SPA nativo y componente `SetupPage` con estilos dedicados (`SetupPage.css`) coherentes con el diseño de `LoginPage`.
    - Lectura segura del token exclusivamente desde `window.location.hash` (`#token=...`); sin query params, sin persistencia en `localStorage`/`sessionStorage`/cookies ni exposición en logs, mensajes o título.
    - Consumo del endpoint real `POST /api/v1/auth/password-reset/complete` mediante `authApi.completePasswordSetup` con token CSRF en memoria (solicitado automáticamente si está ausente).
    - Validación local sobria (campos requeridos y coincidencia de confirmación); validación de longitud, complejidad y blocklist HIBP delegada al backend.
    - Manejo seguro de Problem Details: 422 `RESET_LINK_INVALID` muestra estado seguro de enlace no disponible sin filtrar detalles internos; 400 mapea errores de campo (`newPassword`); 429, 503 y error de red con mensajes sobrios y reintentables.
    - Tras éxito, borrado inmediato del hash con `history.replaceState`, confirmación breve y redirección controlada a `/acceso` sin iniciar sesión automática. ✅
  - **Evidencia de compilación y verificación manual:**
    - `npm run build`: BUILD SUCCESS (`72 modules transformed`, 0 errores TypeScript, 0 advertencias Vite). Verificado manualmente en entorno local integrado con `DEMO-2026`.
- **Subincremento completado: Frontend F2 — Configuración organizacional y puestos (2026-09-25):**
  - **F2.1 — Tipos TypeScript, generador de idempotencia y capa de API:**
    - Tipos de contrato en `frontend/src/types/organization.ts`: `OrganizationView`, `ActiveSiteView`, `OrganizationConfigurationResponse`, `UpdateOrganizationDisplayNameRequest` y `UpdateActiveSiteNameRequest`.
    - Tipos de catálogo en `frontend/src/types/position.ts`: `PositionResponse`, `CreatePositionRequest`, `UpdatePositionRequest`, `ChangePositionStatusRequest` y `PositionStatusFilter` (`'all' | 'active' | 'inactive'`).
    - Generador de idempotencia en `frontend/src/utils/idempotency.ts`: `generateIdempotencyKey()` implementado con Web Crypto nativo (`crypto.randomUUID()`) sin dependencias externas.
    - Clientes HTTP tipados:
      - `organizationApi` (`frontend/src/api/organizationApi.ts`): `getConfiguration()`, `updateDisplayName(data, idempotencyKey)`, `updateActiveSiteName(data, idempotencyKey)`.
      - `positionApi` (`frontend/src/api/positionApi.ts`): `getPositions(active?)`, `createPosition(data, idempotencyKey)`, `updatePosition(id, data, idempotencyKey)`, `changePositionStatus(id, data, idempotencyKey)`.
      - Reutilización de la capa HTTP existente (`apiClient`): envío de cookies de sesión HttpOnly (`credentials: 'include'`), inyección de `X-CSRF-TOKEN` solo en memoria para peticiones de mutación y mapeo de errores a `ApiProblemError` RFC 9457. ✅
  - **F2.2 — Pantalla de configuración organizacional:**
    - Página real `/app/configuracion` conectada con `organizationApi` y RBAC (`ADMINISTRADOR` permitido; `SUPERVISOR` y `COLABORADOR` restringidos con redirección a `/acceso-restringido`).
    - Carga de datos reales mediante `GET /api/v1/organization/configuration`: nombre visible de la organización, código y nombre de la sede activa, y `timezoneId` expuesto estrictamente en modo de solo lectura.
    - Edición idempotente e independiente del nombre visible de la organización y del nombre de la sede activa bajo control de concurrencia optimista (`expectedLockVersion`).
    - Manejo de reintentos seguros: retención de la misma `Idempotency-Key` únicamente ante fallo de red (`isNetworkError`); resolución de conflicto de versión (409 `CONCURRENT_MODIFICATION`) mediante banner contextual y botón de recarga manual sin pérdida de cambios locales no colisionados.
    - Poda de elementos ficticios de Stitch: sin áreas organizativas inexistentes, reglas simuladas de descanso, certificaciones ISO ni falsas integraciones con ERP/reloj biométrico. ✅
  - **F2.3 — Catálogo de puestos y mutaciones seguras:**
    - Ruta `/app/puestos` habilitada en enrutador SPA y navegación (`AppSidebar` con distintivo "Consulta" para supervisor, `AppHeader`, accesos directos en `HomePage`):
      - `ADMINISTRADOR`: consulta, alta, edición y activación/desactivación de puestos.
      - `SUPERVISOR`: modo consulta con banner contextual sobrio y controles de mutación completamente omitidos del DOM.
      - `COLABORADOR`: acceso restringido (`/acceso-restringido`).
    - Catálogo reactivo: filtros de estado (`Todos`, `Activos`, `Inactivos`), búsqueda textual en cliente por código o nombre, estados visuales de carga (`isLoading`), catálogo vacío general, vacío por búsqueda y error con reintento (`handleReload`).
    - Modales accesibles:
      - `PositionModal`: creación (código `^[A-Z0-9-]+$` normalizado en mayúsculas y nombre) y edición (código en solo lectura, nombre editable). Validación de campos, foco accesible, tecla Escape y mapeo de errores 409 `POSITION_CODE_ALREADY_EXISTS`.
      - `PositionStatusModal`: confirmación de activación o desactivación de puestos con texto neutral y verificable ("El puesto quedará marcado como activo/inactivo"), sin afirmar consecuencias operativas no garantizadas por el backend (sin asegurar bloqueo de contrataciones o asignaciones futuras).
    - Control de idempotencia y concurrencia estricto (`PendingOperationIntent`): unión discriminada que incluye `operation`, `positionId` (en edit y status), payload semántico (`code`, `name` o `targetStatus`) y `expectedLockVersion` (en edit y status). Solo se reutiliza la clave si el reintento coincide exactamente en todos sus atributos; cualquier cambio de datos, puesto u operación genera una nueva clave; la clave se limpia tras éxito, respuesta de error del servidor o recarga manual. ✅
  - **Evidencia de compilación y verificación manual:**
    - `npm run build`: BUILD SUCCESS (`72 modules transformed`, 0 errores TypeScript, 0 advertencias Vite). Verificado manualmente en entorno local integrado con `DEMO-2026`.
- **Subincremento completado: Frontend F3 — Directorio y gestión de colaboradores (2026-09-25):**
  - **F3.1 — Tipos TypeScript y capa de API de colaboradores:**
    - Tipos de contrato en `frontend/src/types/employee.ts`: `EmployeeResponse` (`id`, `code`, `displayName`, `positionId`, `siteId`, `active`, `lockVersion`), `CreateEmployeeRequest` (`code`, `displayName`, `positionId`), `UpdateEmployeeRequest` (`displayName?`, `positionId?`, `expectedLockVersion`), `ChangeEmployeeStatusRequest` (`expectedLockVersion`), `DeactivationPreviewResponse` (`employeeId`, `displayName`, `canDeactivate`, `blockers`), `EmployeeStatusFilter` (`'all' | 'active' | 'inactive'`), `EmployeeConflictCode` y `PendingEmployeeIntent`.
    - Poda contractual estricta: sin correos electrónicos de cuentas, sin fotos de perfil, sin teléfonos ni campos inventados inexistentes en el contrato OpenAPI.
    - Cliente HTTP tipado `employeeApi` (`frontend/src/api/employeeApi.ts`): siete operaciones reales implementadas con `apiClient` (`getEmployees`, `getEmployeeById`, `createEmployee`, `updateEmployee`, `activateEmployee`, `deactivateEmployee`, `previewDeactivation`).
    - Mutaciones protegidas con inyección de cabecera `Idempotency-Key` (UUID v4) y CSRF en memoria; `previewDeactivation` como consulta POST sin cuerpo ni clave de idempotencia. ✅
  - **F3.2 — Directorio de colaboradores, filtros y rutas:**
    - Ruta `/app/equipo` integrada en enrutador SPA (`frontend/src/App.tsx`) con RBAC estricto: `ADMINISTRADOR` y `SUPERVISOR` acceden a la vista; `COLABORADOR` redirigido a `/acceso-restringido`.
    - Carga coordinada en paralelo mediante `Promise.all` de tres fuentes reales: colaboradores con filtro de estado (`employeeApi.getEmployees`), catálogo de puestos (`positionApi.getPositions`) y configuración con sede activa (`organizationApi.getConfiguration`). Si alguna fuente esencial falla, se captura de forma segura en `loadError` y se muestra error con botón de reintento, sin presentar datos parciales ni inventados.
    - Resolución contextual en memoria: mapeo de `positionId` al nombre del puesto y `siteId` al nombre de la sede activa.
    - Filtros y búsqueda reactiva: filtro por estado (`Todos`, `Activos`, `Inactivos`), selector dropdown de puestos con puestos activos e inactivos asignados, y búsqueda local en memoria por `code` o `displayName`.
    - Estados visuales completos: carga (`employees-spinner-large`), error estructurado (`ApiProblemError`), directorio vacío general y búsqueda sin coincidencias con botón para limpiar filtros.
    - Supervisor en modo consulta estricto: renderizado de banner informativo de supervisión sin ningún control de mutación en el DOM.
    - Actualización de accesos y tarjeta de Equipo en `HomePage.tsx`. ✅
  - **F3.3 — Modales y mutaciones seguras de colaboradores:**
    - Controles de mutación exclusivos para `ADMINISTRADOR`: botón "Nuevo colaborador" en cabecera y estado vacío, y columna de acciones con botones "Editar" y "Activar/Desactivar". Controles completamente omitidos del DOM para `SUPERVISOR`.
    - Modales accesibles:
      - `EmployeeModal`: alta con validación de código inmutable (`^[A-Z0-9-]+$`, 2..32 caracteres, sin conversión silenciosa a mayúsculas), nombre visible (1..100 caracteres) y puesto (solo activos); edición con código bloqueado en solo lectura, nombre editable y puesto (si el actual es inactivo se muestra como valor legible pero no se reenvía si no cambia; al cambiar solo admite puestos activos; no permite guardar sin cambios). Foco automático inicial, cierre con Escape y backdrop, y semántica ARIA.
      - `EmployeeStatusModal`: activación con confirmación directa enviando `expectedLockVersion`; desactivación en dos pasos mediante pre-evaluación automática con `previewDeactivation`. Si `canDeactivate` es falso, muestra los `blockers` reales devueltos por el servidor y deshabilita la confirmación; si es verdadero, habilita la confirmación neutral. Explicaciones sobrias sin consecuencias inventadas.
    - Control de idempotencia y concurrencia estricto (`PendingEmployeeIntent`): unión discriminada que incluye `operation`, `employeeId` (en edit, activate y deactivate), payload semántico (`code`, `displayName`, `positionId`) y `expectedLockVersion` (en edit, activate y deactivate). Reutilización de `Idempotency-Key` exclusivamente ante error de red (`err.isNetworkError`) si todos los parámetros e intención son idénticos; generación de nueva clave ante cambios de formulario o nueva operación; limpieza de la intención tras éxito, error devuelto por servidor o recarga manual.
    - Actualización local de la lista únicamente tras confirmación de respuesta `EmployeeResponse` del servidor; ante conflicto 409 `CONCURRENT_MODIFICATION` no se sobrescribe el estado y se despliega banner con botón "Recargar directorio"; captura y mapeo de errores RFC 9457 reales (`EMPLOYEE_CODE_ALREADY_EXISTS`, `POSITION_INACTIVE`, `POSITION_NOT_FOUND`, `NO_ACTIVE_SITE`, `EMPLOYEE_LICENSE_LIMIT_REACHED`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_KEY_EXPIRED`). ✅
  - **Evidencia de compilación y verificación manual:**
    - `npm run build` en `frontend/`: BUILD SUCCESS (`72 modules transformed`, 0 errores TypeScript, 0 advertencias Vite). Verificado manualmente en entorno local integrado con `DEMO-2026`.

### Contrato HTTP I1
- `docs/api/openapi.yaml` v0.1.0-i1 → `npx @redocly/cli lint` → 0 errores, 0 advertencias. ✅
- Endpoints `POST /api/v1/auth/login`, `GET /api/v1/auth/session`, `POST /api/v1/auth/logout`, `POST /api/v1/auth/password-reset/inspect`, `POST /api/v1/auth/password-reset/complete`, `POST /api/v1/operator/auth/login`, `GET /api/v1/operator/auth/session`, `POST /api/v1/operator/auth/logout`, `POST /api/v1/operator/setup/organizations` y `POST /api/v1/operator/setup/organizations/{organizationId}/initial-access-link` implementados y verificados. ✅ (2026-09-16)

### Ejecución local — SQL Server dev + Backend
Verificado 2026-08-30:
- `docker compose -f infra/docker-compose.dev.yml --env-file infra/.env.dev up -d` → `turnoshift-sqldev healthy` en puerto 11433.
- Base `turnoshift_dev` creada con `sqlcmd` desde el contenedor.
- Backend arrancado con variables de entorno leídas de `infra/.env.dev` (sin `--env-file`, que no exporta al shell anfitrión).
- Flyway aplicó V1+V2+V3 en `turnoshift_dev` al arrancar. Log confirmado.
- `GET http://localhost:8082/api/health` → `{"status":"UP","service":"turnoshift-backend"}`. ✅

### JAR de producción (`turnoshift-backend-0.1.0.jar`)
- `BOOT-INF/classes/db/migration/V1`–`V11` presentes. ✅
- `BOOT-INF/lib/spring-boot-flyway-4.0.0.jar` presente (FlywayAutoConfiguration). ✅
- Recursos PoC (`db/poc/migration/`) ausentes del JAR. ✅

### Suite de pruebas — Verificación final y cierre de I1 (2026-09-16)
- **Incremento 1 (I1): COMPLETADO.**
- **Comando de cierre ejecutado:**
  ```powershell
  $env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'
  .\mvnw.cmd verify -B
  ```
- **Resultado de compilación:** BUILD SUCCESS, exit code 0, duración 38:44 min.
- **Pruebas Unitarias (Surefire):** 402/402 PASS (0 fallos, 0 errores, 0 omitidos).
- **Pruebas de Integración (Failsafe):** 183/183 PASS (0 fallos, 0 errores, 0 omitidos).
- **Total pruebas de backend:** 585/585 PASS.
- **Validaciones de persistencia e infraestructura:** Flyway V1–V11, SQL Server 2022/Testcontainers y las pruebas de seguridad, sesiones, onboarding y enlace inicial se verificaron correctamente.
- **Control de versiones:** Sin commits ni push (cambios locales verificados).

| Clase | Tests | Patrón |
|---|---|---|
| Pruebas unitarias (Surefire) | 402 | EmailNormalizer, PasswordPolicy, Argon2PasswordService, AuthenticateUser, LoginInputValidator, InMemoryLoginRateLimiter, LoginController, SessionController, SecurityProblemHandlers, RevalidateSession, SessionRevalidationFilter, HibpPasswordBlocklist, SecureTokenGenerator, IssueInitialSetupToken, InspectResetToken, ConsumeResetToken, JdbcPasswordResetTokenRepository, CompleteInitialPasswordSetup, JdbcAppUserPasswordRepository, InMemoryPasswordResetRateLimiter, PasswordResetController, PlatformOperator, JdbcPlatformOperatorRepository, PlatformOperatorIdentity, AuthenticateOperator, OperatorBootstrapRunner, TransactionalOperatorInserter, OperatorLoginInputValidator, InMemoryOperatorLoginRateLimiter, RevalidateOperatorSession, OperatorLoginController, OperatorSessionController, IncorporateOrganizationInputValidator, OrganizationProperties, IncorporateOrganization, OperatorOrganizationSetupController, EmitInitialAccessLinkInputValidator, RequestFingerprintCalculator, EmitInitialAccessLink, OperatorInitialAccessLinkController |
| `OperatorInitialAccessLinkIT` | 14 | @SpringBootTest + Testcontainers SQL Server — emisión de enlace inicial, no persistencia de secretos, replays 409 INITIAL_LINK_ALREADY_EMITTED, control de versiones optimista, sp_getapplock, URL hash, y fallo técnico TokenDataAccessException con respuesta 503 segura y rollback total |
| `OperatorOrganizationOnboardingIT` | 12 | @SpringBootTest + Testcontainers SQL Server — incorporación asistida, idempotencia mono-transaccional, bloqueos sp_getapplock, replays 201/409, y concurrencia |
| `OperatorSecuritySessionIT` | 7 | @SpringBootTest + Testcontainers SQL Server — login/session/logout operador, rotación y persistencia de sesión en SQL Server, rate limiting 429, CSRF 403, revocación inmediata por desactivación/auth_version, y bloqueo cruzado bidireccional 403 |
| `OperatorBootstrapIT` | 7 | @SpringBootTest + Testcontainers SQL Server — bootstrap desactivado, creación válida con hash Argon2id verificado, exclusión mutua concurrente estricta con sp_getapplock (Future.get con timeout sin swallow de excepciones), operador preexistente, contraseñas inválidas, fail-closed por HIBP |
| `SchemaUpgradeV2ToV3IT` | 8 | Flyway programático — prueba histórica V2→V3, target fijo en V3 |
| `SchemaUpgradeV3ToLatestIT` | 7 | Flyway programático — prueba V3→latest con datos preexistentes |
| `OrganizationsSitesSchemaIT` | 14 | @SpringBootTest + @Transactional + beans inyectados |
| `IdentitySchemaIT` | 12 | @SpringBootTest + @Transactional — identidad V4–V8 (constraint names verificados) |
| `SpringBootStartupIT` | 5 | @SpringBootTest + bean Flyway + connection metadata |
| `FlywayMssqlCompatibilityIT` (PoC-01) | 5 | Flyway manual (PoC, conservado) |
| `JdbcAuthenticationCandidateRepositoryIT` | 8 | IT multi-tenant candidate repository con SQL Server Testcontainers y fallo inducido |
| `JdbcSessionRevalidationRepositoryIT` | 6 | IT consulta de revalidación contra SQL Server Testcontainers y fallo inducido |
| `JdbcPasswordResetTokenRepositoryIT` | 17 | IT ciclo de vida de tokens sobre SQL Server Testcontainers |
| `CompleteInitialPasswordSetupIT` | 11 | IT establecimiento transaccional de contraseña sobre SQL Server Testcontainers |
| `PasswordResetHttpIT` | 7 | IT endpoints HTTP password-reset sobre SQL Server Testcontainers |
| `SecurityInfrastructureIT` (PoC-02 + HTTP + Revalidación) | 30 | Spring Security + Spring Session JDBC + CSRF + Login HTTP + Session + Logout + Revalidación continua + Límite 8h sobre SQL Server real |
| `JdbcPlatformOperatorRepositoryIT` | 13 | IT persistencia de operadores sobre SQL Server Testcontainers |

Verificaciones cubiertas:
- FlywayAutoConfiguration activa (spring-boot-flyway-4.0.0 en classpath). ✅
- DataSource y Flyway apuntan al mismo servidor. ✅
- V1–V9 aplicados desde base vacía. ✅
- V3 aplicable sobre datos V1/V2 sin pérdida de filas. ✅
- V4–V8 aplicables sobre datos V1–V3 sin pérdida (SchemaUpgradeV3ToLatestIT). ✅
- Idempotencia: segunda llamada a migrate() aplica 0 migraciones. ✅
- Restricciones V3 activas. ✅
- employee_id no nulo rechazado por `chk_app_users_employee_id_null_guard` (V7). ✅
- `normalized_email` con collation `Latin1_General_100_BIN2` verificada en `sys.columns` (V8). ✅
- Nombres de restricciones verificados en mensajes SQL de pruebas negativas. ✅
- Segunda sede activa rechazada (índice filtrado V2). ✅
- FK a organización inexistente rechazada. ✅
- SPRING_SESSION y SPRING_SESSION_ATTRIBUTES creadas por V9 vía Flyway. ✅
- Spring Session JDBC guarda sesión real con MAX_INACTIVE_INTERVAL=1800 en SQL Server. ✅
- Token CSRF almacenado como atributo de sesión en SPRING_SESSION_ATTRIBUTES. ✅
- `GET /api/health` devuelve 200 sin sesión activa. ✅
- `GET /api/v1/auth/csrf` devuelve token CSRF y cookie TSESSIONID con HttpOnly, SameSite=Lax, Path=/api. ✅
- Cookie sin Domain ni Max-Age persistente. ✅
- Cache-Control: no-store en respuesta CSRF. ✅
- POST sin CSRF devuelve 403 application/problem+json con código CSRF_INVALID. ✅
- POST con CSRF inválido devuelve 403 CSRF_INVALID. ✅
- POST con CSRF válido supera el filtro (no 403). ✅
- Ruta protegida sin sesión devuelve 401 SESSION_REQUIRED (no 302). ✅
- No existe UserDetailsService predeterminado en el contexto. ✅

**Hallazgo documentado (corregido en I0):**
`spring-boot-flyway-4.0.0` es módulo separado de `spring-boot-autoconfigure` en Spring Boot 4.0.0.
Sin él, `FlywayAutoConfiguration` no se registra aunque `flyway-core` esté en el classpath.
Corrección: `flyway-core` → `spring-boot-starter-flyway` en `pom.xml`; `flyway-sqlserver` conservado explícito.

**Hallazgo documentado (PoC-02):**
En Spring Boot 4.0.0, `@AutoConfigureMockMvc` se encuentra en `org.springframework.boot.webmvc.test.autoconfigure`
(módulo `spring-boot-webmvc-test`), no en `spring-boot-test-autoconfigure`.
`UserDetailsServiceAutoConfiguration` está en `org.springframework.boot.security.autoconfigure`
(módulo `spring-boot-security`), verificado en el JAR real.
Con Spring Session JDBC activo en `RANDOM_PORT`, la sesión vive en BD; hay que usar la cookie del response
(no `request.getSession(false)`) para correlacionar peticiones consecutivas en MockMvc.

### I2.1 — Configuración y puestos: backend/API completado (2026-09-16)

- **Alcance funcional implementado:**
  - **HU-05 (Configuración de organización y sede activa):**
    - Consulta (`GET /api/v1/organization/configuration`) de la configuración general de la organización y los datos de su sede única activa. Restricción explícita: `timezoneId` se expone únicamente en modo lectura para contextualizar operaciones; no existe endpoint para modificarlo en I2.1 (diferido hasta I3). Los campos internos y restringidos (`service_status`, `max_active_employees`, `current_policy_version_id`) no se exponen. ✅
    - Actualización idempotente del nombre visible de la organización (`PATCH /api/v1/organization/configuration/display-name`) con control de concurrencia optimista (`expectedLockVersion`). ✅
    - Actualización idempotente del nombre de la sede activa (`PATCH /api/v1/organization/configuration/active-site/name`) con control de concurrencia optimista (`expectedLockVersion`). ✅
  - **HU-06 (Catálogo de puestos de trabajo):**
    - Consulta (`GET /api/v1/positions`) del catálogo de puestos con filtro opcional por estado mediante el parámetro `active` (`true`, `false` o todos si se omite). ✅
    - Creación idempotente (`POST /api/v1/positions`) con código único normalizado en mayúsculas (`^[A-Z0-9-]+$`), estado activo por defecto y `lockVersion = 0`. ✅
    - Actualización idempotente del nombre descriptivo (`PUT /api/v1/positions/{positionId}`) bajo control de concurrencia optimista (`expectedLockVersion`), manteniendo el código inmutable. ✅
    - Activación o desactivación idempotente (`PATCH /api/v1/positions/{positionId}/status`) bajo control de concurrencia optimista (`expectedLockVersion`). ✅
- **Seguridad, aislamiento y transversalidad:**
  - **Aislamiento multi-tenant estricto por sesión:** la organización y el usuario actor se derivan exclusivamente de `AuthenticatedIdentity` en el `SecurityContext`. Nunca se acepta `organizationId` ni `actorUserId` desde el cliente (cuerpo, cabeceras o parámetros de ruta/consulta). ✅
  - **Control de acceso basado en roles (RBAC):**
    - Consultas (`GET`): requieren rol `ADMINISTRADOR` o `SUPERVISOR`; rol `COLABORADOR` recibe `403 ACCESS_DENIED`.
    - Mutaciones (`POST`, `PUT`, `PATCH`): requieren exclusivamente rol `ADMINISTRADOR`; roles `SUPERVISOR` y `COLABORADOR` reciben `403 ACCESS_DENIED`. ✅
  - **Protección CSRF y Cache-Control:** cabecera `X-CSRF-TOKEN` obligatoria en todas las mutaciones (`403 CSRF_INVALID` ante omisión o token inválido). Cabecera `Cache-Control: no-store` emitida en todas las respuestas exitosas y de error (Problem Details). ✅
  - **Idempotencia empresarial mono-transaccional (`BusinessIdempotentOperationExecutor`):** coordinación mono-transaccional de mutaciones con cabecera obligatoria `Idempotency-Key` (UUID), cálculo de huella canónica (`BusinessFingerprintCalculator`) y registro en `api_operations` (con coherencia relacional y clave foránea compuesta `fk_api_operations_tenant_user` hacia `app_users`). Replays deterministas devuelven la respuesta original con la cabecera `Idempotency-Replayed: true` tanto para éxitos (200/201) como para rechazos deterministas (404/409) acompañados de un `requestId` fresco. Rechazo con `409 IDEMPOTENCY_KEY_REUSED` ante alteración de huella y `409 IDEMPOTENCY_KEY_EXPIRED` tras 24 horas. ✅
  - **Problem Details RFC 9457:** respuestas de error tipadas sin cabecera HTTP `X-Request-ID`, conservando el campo `requestId` dentro del cuerpo JSON. ✅
- **Persistencia (V12):**
  - Migración `V12__positions.sql` con tabla `positions`: clave primaria `id UNIQUEIDENTIFIER`, clave candidata empresarial `uq_positions_org_id (organization_id, id)` para futuras referencias foráneas compuestas desde `employees`, unicidad por código `uq_positions_org_code (organization_id, code)` con collation binaria `Latin1_General_100_BIN2`, clave foránea a `organizations(id)` sin cascada, y restricciones `CHECK` de formato de código (2–32 caracteres, `[A-Z0-9-]`) y nombre no vacío tras trim. ✅
- **Contrato OpenAPI v0.2.1-i2.1:**
  - 7 rutas de I2.1 documentadas y alineadas (`GET /positions`, `POST /positions`, `PUT /positions/{positionId}`, `PATCH /positions/{positionId}/status`, `GET /organization/configuration`, `PATCH /organization/configuration/display-name`, `PATCH /organization/configuration/active-site/name`).
  - Definición y vinculación de variantes reutilizables de I2.1 (`I21ValidationFailed`, `I21SessionRequired`, `I21AccessDeniedOrCsrf`, `I21ServiceUnavailable`) sin cabecera HTTP `X-Request-ID`, conservando `requestId` en el cuerpo Problem Details y `Cache-Control: no-store`.
  - Validación con Redocly CLI (`npx @redocly/cli lint docs/api/openapi.yaml`): 0 errores y 0 advertencias. ✅
- **Métricas de pruebas respaldadas por reportes existentes en `backend/target/surefire-reports`:**
  - `PositionTest`: 18/18 PASS.
  - `PositionUseCasesTest`: 13/13 PASS.
  - `PositionControllerTest`: 23/23 PASS.
  - `BusinessIdempotentOperationExecutorTest`: 6/6 PASS.
  - `PositionsSchemaIT`: 9/9 PASS.
  - `SchemaUpgradeV11ToV12IT`: 3/3 PASS.
  - `JdbcPositionRepositoryIT`: 8/8 PASS.
  - `PositionControllerIT`: 9/9 PASS.
  - `JdbcOrganizationConfigurationRepositoryIT`: 6/6 PASS.
  - `OrganizationConfigurationControllerIT`: 10/10 PASS.
  - `BusinessIdempotencyIT`: 7/7 PASS.
  - `SpringBootStartupIT`: 5/5 PASS.
- **Subincremento completado: I2.2 — Colaboradores y vínculo con cuentas (2026-09-24):**
  - **I2.2-a Persistencia:** COMPLETADO. Migraciones `V13__employees.sql` (tabla `employees`) y `V14__app_users_employee_fk.sql` (eliminación de guarda V7 e instalación de FK compuesta `fk_app_users_employee`). Pruebas focalizadas de persistencia y upgrades aprobadas (`EmployeePersistenceIT`, `SchemaUpgradeV3ToLatestIT`, `SchemaUpgradeV11ToV12IT`). ✅
  - **I2.2-b Dominio, casos de uso y persistencia JDBC:** COMPLETADO. Núcleo de empleados con `Employee`, casos de uso (`CreateEmployeeUseCase`, `GetEmployeesUseCase`, `GetEmployeeUseCase`, `UpdateEmployeeUseCase`, `ActivateEmployeeUseCase`, `DeactivateEmployeeUseCase`), adaptador `JdbcEmployeeRepository` con corrección transaccional (sin `BEGIN TRANSACTION` manual, exige transacción Spring activa), timestamps UTC con `SYSUTCDATETIME()` y serialización de concurrencia de licencia vía `sp_getapplock`. Pruebas unitarias y `JdbcEmployeeRepositoryIT` aprobadas. ✅
  - **I2.2-c API HTTP segura:** COMPLETADO. `EmployeeController` implementado bajo `/api/v1/employees` con 7 endpoints, aislamiento multi-tenant estricto por sesión, RBAC (ADMINISTRADOR y SUPERVISOR para GET; solo ADMINISTRADOR para mutaciones), CSRF obligatorio en mutaciones, `Cache-Control: no-store`, Problem Details RFC 9457 con `requestId` en el body (sin cabecera `X-Request-ID`), deduplicación determinista con `BusinessIdempotentOperationExecutor` y `EmployeeIdempotentCommand` (serialización/deserialización tipada con ObjectMapper). Pruebas unitarias `EmployeeControllerTest` + `EmployeeIdempotentCommandTest`: 50/50 PASS; `EmployeeControllerIT` previamente aprobado (12/12 PASS). ✅
  - **I2.2-d Contrato OpenAPI:** COMPLETADO. `docs/api/openapi.yaml` actualizado a versión `0.2.2-i2.2`, documentando 7 rutas de colaboradores, seguridad AND (`sessionCookie` + `csrfHeader`), `Idempotency-Key` en parámetros header (omitido en preview evaluativa), esquemas (`EmployeeResponse`, `CreateEmployeeRequest`, `UpdateEmployeeRequest`, `ChangeEmployeeStatusRequest`, `DeactivationPreviewResponse`) y componentes de error reutilizables I22 (`I22ValidationFailed`, `I22SessionRequired`, `I22AccessDeniedOrCsrf`, `I22ServiceUnavailable`, `EmployeeNotFound`, `EmployeeCreationConflict`, `EmployeeMutationConflict`, `EmployeeActivateConflict`, `EmployeeDeactivateConflict`). Validado con Redocly CLI (`npx @redocly/cli lint docs/api/openapi.yaml`): 0 errores y 0 advertencias. ✅
- **Subincremento completado: I2.3 — Política operativa inicial (2026-09-25):**
  - **I2.3-a Persistencia:** COMPLETADO. Migraciones `V15__planning_policy_versions.sql` (tabla `planning_policy_versions` con modelo append-only, clave candidata `uq_planning_policy_versions_org_id (organization_id, id)`, restricciones CHECK de umbrales `minimum_rest_minutes [1..1440]`, `weekly_warning_minutes [1..10080]` y motivo no vacío) y `V16__organizations_policy_fk.sql` (desmantelamiento de guarda V3 e instalación de FK empresarial compuesta `fk_organizations_current_policy_version ((id, current_policy_version_id) -> planning_policy_versions(organization_id, id))`). ✅
  - **I2.3-b Dominio, casos de uso y persistencia JDBC:** COMPLETADO. Value objects (`PolicyThresholds`, `PolicyReason`, `PolicyDelta`, `ValidationFingerprint`), entidades inmutables (`NewPolicyVersion`, `PolicyVersion`), casos de uso (`GetCurrentPolicyUseCase`, `GetPolicyHistoryUseCase`, `PreviewPolicyChangeUseCase`, `ActivatePolicyUseCase`), puerto `PolicyRepository` y adaptador `JdbcPolicyRepository` con bloqueo pesimista transaccional exclusivo `sp_getapplock` por organización (`TurnoShift:Policy:Org:{orgId}`) dentro de transacción Spring activa. Evidencia: 58 pruebas unitarias y 10 IT JDBC aprobadas. ✅
  - **I2.3-c API HTTP segura:** COMPLETADO. `PolicyController` implementado bajo `/api/v1/policies`:
    - I2.3-c1: lectura (`GET /policies/current`, `GET /policies/history`) y previsualización evaluativa (`POST /policies/preview`) con validación obligatoria de `expectedCurrentPolicyVersionId` (nullable explícito), RBAC (`ADMINISTRADOR` y `SUPERVISOR` para lectura, exclusivo `ADMINISTRADOR` para preview), CSRF en preview y Problem Details. Evidencia: 21 HTTP + 11 de casos de uso aprobadas. ✅
    - I2.3-c2: activación (`POST /policies/activate`) con deduplicación determinista (`BusinessIdempotentOperationExecutor` y `PolicyIdempotentCommand`), cabecera obligatoria `Idempotency-Key` (UUID), verificación de huella canónica (`validationFingerprint`), bloqueo transaccional `sp_getapplock` con traducción a 503 `POLICY_OPERATION_BUSY` (`Retry-After: 1`), y replay determinista con cabecera `Idempotency-Replayed: true`. Evidencia: 47 pruebas focalizadas aprobadas. ✅
  - **I2.3-d Contrato OpenAPI:** COMPLETADO. `docs/api/openapi.yaml` y `docs/api/README.md` actualizados a versión `0.2.3-i2.3`, documentando las 4 rutas bajo el tag `policies`, seguridad `sessionCookie` para GET y AND (`sessionCookie` + `csrfHeader`) para POST, cabeceras `Retry-After` e `Idempotency-Replayed` (en replay), modelado exacto de DTOs y respuestas de error RFC 9457 (`VALIDATION_FAILED`, `REASON_REQUIRED`, `INVALID_VALIDATION_FINGERPRINT`, `SESSION_REQUIRED`, `ACCESS_DENIED`, `CSRF_INVALID`, `POLICY_CONCURRENT_MODIFICATION`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_KEY_EXPIRED`, `POLICY_OPERATION_BUSY`, `SERVICE_UNAVAILABLE`). Validación con Redocly CLI (`npx @redocly/cli lint docs/api/openapi.yaml`): 0 errores, 0 advertencias. ✅
- **Subincremento completado: I2.4 — Indisponibilidades (HU-08) (2026-09-25):**
  - **I2.4-a Persistencia:** COMPLETADO. Migración `V17__employee_unavailabilities.sql` creada con tabla `employee_unavailabilities`, aislamiento multi-tenant por `organization_id`, clave candidata empresarial `uq_employee_unavailabilities_org_id (organization_id, id)`, clave foránea compuesta `fk_employee_unavailabilities_org_employee ((organization_id, employee_id) -> employees(organization_id, id))` (`ON DELETE NO ACTION`, `ON UPDATE NO ACTION`), ciclo de vida con estados válidos `ACTIVA` y `CANCELADA` (`chk_employee_unavailabilities_status`), intervalo temporal semiabierto `[starts_at, ends_at)` con restricción `chk_employee_unavailabilities_interval (starts_at < ends_at)`, motivo tipado `reason_code` (`chk_employee_unavailabilities_reason_code`), nota restringida opcional `restricted_note NVARCHAR(500)`, campos de auditoría de cancelación (`cancelled_at`, `cancelled_by_user_id`, `cancellation_reason` con coherencia relacional `chk_employee_unavailabilities_cancellation_coherence`), control de concurrencia optimista `lock_version >= 0`, timestamps UTC con `SYSUTCDATETIME()`, e índices filtrados para optimización de consultas activas (`ix_employee_unavailabilities_active_lookup`) e histórico por colaborador y organización. Pruebas focalizadas de persistencia y upgrades aprobadas (`UnavailabilityPersistenceIT`, `SchemaUpgradeV3ToLatestIT`, `SchemaUpgradeV11ToV12IT`). ✅
  - **I2.4-b Dominio, casos de uso y persistencia JDBC:** COMPLETADO. Modelo de dominio puro e inmutable con entidades y value objects (`EmployeeUnavailability`, `UnavailabilityStatus`, `UnavailabilityReasonCode`, `UnavailabilityInterval`, `RestrictedNote` con validación estricta de hasta 500 caracteres, UTF-16 y rechazo de solo espacios Unicode). Casos de uso de aplicación: `CreateUnavailabilityUseCase`, `GetUnavailabilityUseCase`, `ListEmployeeUnavailabilitiesUseCase`, `ListOrganizationUnavailabilitiesUseCase`, `UpdateUnavailabilityUseCase` y `CancelUnavailabilityUseCase`. Adaptador `JdbcEmployeeUnavailabilityRepository` con consultas parametrizadas, aislamiento por organización, timestamps en BD con `SYSUTCDATETIME()` y protección atómica contra condiciones de carrera mediante filtro `starts_at > SYSUTCDATETIME()`. Regla de negocio RN-013: modificación o cancelación prohibidas si el intervalo existente ya inició (`startsAt <= now`); la actualización tampoco permite mover el inicio al pasado (`newStartsAt <= now`). La creación admite indisponibilidades en curso (`startsAt <= now`) siempre que su fin sea futuro (`endsAt > now`) y rechaza períodos ya concluidos en el pasado (`endsAt <= now`). Pruebas unitarias de dominio/casos de uso y `JdbcEmployeeUnavailabilityRepositoryIT` (14/14 PASS) aprobadas. ✅
  - **I2.4-c API HTTP e Idempotencia:** COMPLETADO. `UnavailabilityController` implementado con 6 endpoints REST:
    - Consultas: `GET /api/v1/employees/{employeeId}/unavailabilities` (filtro opcional estricto `includeCancelled`), `GET /api/v1/unavailabilities` (parámetros obligatorios `from` y `to` validados como `Instant` con `from < to`, límite `limit <= 200` y cursor opcional `cursorCreatedAt`) y `GET /api/v1/unavailabilities/{unavailabilityId}`.
    - Mutaciones: `POST /api/v1/employees/{employeeId}/unavailabilities` (201 Created con cabecera `Location`), `PUT /api/v1/unavailabilities/{unavailabilityId}` (200 OK) y `POST /api/v1/unavailabilities/{unavailabilityId}/cancel` (200 OK con `cancelledAt` y `cancellationReason`).
    - Soporte dual de formatos temporales: intervalo parcial UTC (`startsAt`, `endsAt`) y día completo organizacional (`allDay = true`, `startDate`, `endDate` exclusiva en `YYYY-MM-DD`), mutuamente excluyentes, resuelto con `OrganizationTimezonePort`, `OrganizationTimezoneAdapter` y `UnavailabilityTemporalParser` convirtiendo a UTC según la zona IANA de la organización.
    - Idempotencia determinista coordinada con `BusinessIdempotentOperationExecutor` y `UnavailabilityIdempotentCommand`: cabecera obligatoria `Idempotency-Key` (UUID), registro en `api_operations`, huella canónica, y replays deterministas con cabecera `Idempotency-Replayed: true` para éxitos (200/201) y rechazos (404/409).
    - Seguridad y control de acceso: roles `ADMINISTRADOR` y `SUPERVISOR` permitidos; `COLABORADOR` y `PLATFORM_OPERATOR` reciben 403 `ACCESS_DENIED`; peticiones sin sesión reciben 401 `SESSION_REQUIRED`; protección obligatoria CSRF en mutaciones (`403 CSRF_INVALID`).
    - Control de concurrencia optimista (`expectedLockVersion`) con emisión de 409 `CONCURRENT_MODIFICATION` ante colisiones y 409 `UNAVAILABILITY_ALREADY_CANCELLED` si ya está cancelada. Respuestas RFC 9457 Problem Details con `requestId` en el cuerpo y sin cabecera HTTP `X-Request-ID`.
    - Evidencia de pruebas: 63/63 PASS en suite focalizada (`UnavailabilityControllerIT` 14/14, `UnavailabilityControllerTest` 21/21, `UnavailabilityTemporalParserTest` 5/5, `OrganizationTimezoneAdapterTest` 5/5, `UnavailabilityIdempotentCommandTest` 4/4, `JdbcEmployeeUnavailabilityRepositoryIT` 14/14). ✅
  - **I2.4-d Contrato OpenAPI:** COMPLETADO. `docs/api/openapi.yaml` y `docs/api/README.md` actualizados a versión `0.2.4-i2.4` con tag `unavailabilities`, documentando las 6 rutas (3 de consulta, 3 de mutación), seguridad `sessionCookie` en GET y AND (`sessionCookie` + `csrfHeader`) en POST/PUT, cabeceras `Location`, `Idempotency-Key`, `Idempotency-Replayed`, esquemas de request/response (`CreateUnavailabilityRequest`, `UpdateUnavailabilityRequest`, `CancelUnavailabilityRequest`, `EmployeeUnavailabilityResponse`, `EmployeeUnavailabilityListResponse`, `UnavailabilityListResponse`), componentes reutilizables de error I24 RFC 9457 (`I24ValidationFailed`, `I24SessionRequired`, `I24AccessDeniedOrCsrf`, `I24ServiceUnavailable`, `UnavailabilityNotFound`, `UnavailabilityMutationConflict`, `UnavailabilityPastIntervalProhibited`). Validación con Redocly CLI (`npx @redocly/cli lint docs/api/openapi.yaml`): 0 errores y 0 advertencias. ✅
- **Pendientes explícitos de I2 (I2 sigue EN CURSO):**
  1. **Frontend de I2.4:** interfaz de usuario para registro, consulta y cancelación de indisponibilidades de colaboradores (HU-08 / Frontend F4).
  (Nota: I2.4 backend y API completados; la integración de indisponibilidades en la planificación de turnos y sus reglas de impacto corresponden aguas abajo a I3).

---

## 3. Migraciones de producción activas

| Versión | Archivo | Descripción |
|---|---|---|
| V1 | `V1__organizations.sql` | Tabla organizations, tipos, constraints, collation binaria |
| V2 | `V2__sites.sql` | Tabla sites, FK, índice filtrado sede activa única |
| V3 | `V3__organizations_constraints.sql` | max_active_sites=1, policy_version IS NULL, drop índice redundante |
| V4 | `V4__roles.sql` | Catálogo global de roles: ADMINISTRADOR, SUPERVISOR, COLABORADOR |
| V5 | `V5__app_users.sql` | Usuarios empresariales con FK a organizations y roles |
| V6 | `V6__password_reset_tokens.sql` | Tokens de configuración inicial y recuperación |
| V7 | `V7__app_users_employee_id_guard.sql` | CHECK `employee_id IS NULL` mientras `employees` no existe |
| V8 | `V8__app_users_normalized_email_collation.sql` | `COLLATE Latin1_General_100_BIN2` en `normalized_email`; drop/recreación de UNIQUE y CHECK |
| V9 | `V9__spring_session.sql` | Tablas SPRING_SESSION y SPRING_SESSION_ATTRIBUTES (spring-session-jdbc:4.0.0) |
| V10 | `V10__platform_operators.sql` | Tabla platform_operators, constraints, indices, collation binaria |
| V11 | `V11__api_operations.sql` | Tabla api_operations para control de idempotencia mono-transaccional |
| V12 | `V12__positions.sql` | Catálogo de puestos de trabajo por organización (HU-06), clave candidata (organization_id, id), unicidad (organization_id, code) BIN2 |
| V13 | `V13__employees.sql` | Catálogo de colaboradores/empleados por organización, clave candidata (organization_id, id), unicidad (organization_id, code) BIN2, FKs compuestas a positions y sites |
| V14 | `V14__app_users_employee_fk.sql` | Desmantelamiento de guarda temporal V7 y FK empresarial compuesta (organization_id, employee_id) -> employees(organization_id, id) |
| V15 | `V15__planning_policy_versions.sql` | Tabla planning_policy_versions, modelo append-only, clave candidata (organization_id, id), checks de umbrales y motivo |
| V16 | `V16__organizations_policy_fk.sql` | Desmantelamiento de guarda V3 y FK empresarial compuesta (id, current_policy_version_id) -> planning_policy_versions(organization_id, id) |
| V17 | `V17__employee_unavailabilities.sql` | Tabla employee_unavailabilities, ciclo ACTIVA/CANCELADA, intervalos [starts_at, ends_at), FK compuesta (organization_id, employee_id), lock_version e índices filtrados |

---

## 4. Versiones efectivas (BOM Spring Boot 4.0.0)

| Componente | Versión |
|---|---|
| Spring Boot Flyway Starter | 4.0.0 |
| Flyway core (transitivo) | 11.14.1 |
| Flyway SQL Server | 11.14.1 |
| SQL Server JDBC | 13.2.1.jre11 |
| Spring Security | 7.0.0 |
| Spring Session JDBC | 4.0.0 |
| Password4j | 1.8.4 |
| Testcontainers | 2.0.2 |
| JUnit Jupiter | 6.0.1 |
| HikariCP | 7.0.2 |
| maven-failsafe-plugin | 3.5.4 |

---

## 5. Estado de I2 y próximos subincrementos

- **I2 — Gestión de personal y estructura organizativa:** **COMPLETADO** (2026-09-25).
  - Componentes cerrados: **I2.1–I2.4** (persistencia, dominio, API HTTP e idempotencia y contratos OpenAPI) y **Frontend F1–F4** (Autenticación/Layout/Setup F1.1–F1.5, Configuración/Puestos F2.1–F2.3, Equipo F3.1–F3.3 e Indisponibilidades F4.1–F4.3), junto con la verificación manual integrada del MVP en entorno local.
- **I2.1 — Configuración y puestos:** Backend, contrato OpenAPI y Frontend F2 COMPLETADOS.
- **I2.2 — Colaboradores y vínculo con cuentas:** Backend, contrato OpenAPI y Frontend F3 COMPLETADOS (2026-09-25).
  - I2.2-a — Persistencia de empleados (V13 y V14): COMPLETADO. V13 employees y V14 FK app_users ↔ employees; pruebas focalizadas de persistencia y upgrades aprobadas.
  - I2.2-b — Dominio, casos de uso y persistencia JDBC de empleados: COMPLETADO. Núcleo de empleados y corrección transaccional/timestamps aprobados.
  - I2.2-c — API HTTP de empleados: COMPLETADO. EmployeeControllerIT previamente aprobado; EmployeeIdempotentCommandTest + EmployeeControllerTest: 50/50 PASS.
  - I2.2-d — Contrato OpenAPI de empleados: COMPLETADO. OpenAPI v0.2.2-i2.2 validado por Redocly con 0 errores y 0 advertencias.
- **I2.3 — Política operativa inicial:** COMPLETADO (2026-09-25).
  - I2.3-a — Persistencia (V15 y V16): COMPLETADO. Tabla append-only `planning_policy_versions` y FK compuesta `organizations.current_policy_version_id`.
  - I2.3-b — Dominio, casos de uso y persistencia JDBC: COMPLETADO. Value objects, casos de uso, `JdbcPolicyRepository` con candado transaccional `sp_getapplock` por organización. Evidencia: 58 unitarias + 10 IT JDBC aprobadas.
  - I2.3-c — API HTTP segura: COMPLETADO. `PolicyController` con endpoints current, history, preview y activate, RBAC, CSRF, Problem Details e idempotencia con replay determinista. Evidencia: I2.3-c1 (21 HTTP + 11 casos de uso) e I2.3-c2 (47 pruebas focalizadas) aprobadas.
  - I2.3-d — Contrato OpenAPI: COMPLETADO. OpenAPI v0.2.3-i2.3 documentando las 4 rutas bajo `policies` y validado por Redocly con 0 errores y 0 advertencias.
- **I2.4 — Indisponibilidades:** COMPLETADO (2026-09-25).
  - I2.4-a — Persistencia (V17): COMPLETADO. Tabla `employee_unavailabilities`, aislamiento por organización, FKs compuestas `(organization_id, employee_id)`, ciclo activo/cancelado, intervalos `[starts_at, ends_at)`, `lock_version` e índices filtrados.
  - I2.4-b — Dominio, casos de uso y persistencia JDBC: COMPLETADO. Modelo puro e inmutable, intervalos semiabiertos, notas restringidas UTF-16, casos de uso de creación, consulta, listado, actualización y cancelación con timestamps SQL Server; RN-013 (update/cancel rechazados si startsAt <= now; update rechaza newStartsAt <= now; creación admite intervalo en curso con endsAt > now y rechaza período finalizado).
  - I2.4-c — API HTTP e Idempotencia: COMPLETADO. `UnavailabilityController` con 6 endpoints REST (3 de lectura, 3 de mutación), soporte de intervalo parcial UTC y día completo organizacional con `OrganizationTimezonePort` y `UnavailabilityTemporalParser`, deduplicación con `BusinessIdempotentOperationExecutor` y `UnavailabilityIdempotentCommand`, RBAC estricto (ADMINISTRADOR/SUPERVISOR; COLABORADOR y PLATFORM_OPERATOR 403; sin sesión 401), concurrencia optimista (`expectedLockVersion`) y Problem Details RFC 9457.
  - I2.4-d — Contrato OpenAPI: COMPLETADO. OpenAPI v0.2.4-i2.4 y `docs/api/README.md` actualizados y validados por Redocly CLI (0 errores, 0 advertencias).
- **Frontend F1 — Autenticación y Layout:** COMPLETADO (2026-09-25).
  - F1.1 — Proxy Vite y tipos/cliente base: proxy dev `/api` a `http://localhost:8081`, Problem Details RFC 9457 y gestión de CSRF solo en memoria.
  - F1.2 — authApi y AuthContext: bootstrap, sesión en memoria, rotación de CSRF tras login, logout seguro; sin persistencia en localStorage/sessionStorage.
  - F1.3 — Enrutador SPA nativo, layout base y navegación visual: Web History API (`Router`, `useRouter`, `<Link>`), guards por rol, layout responsivo Stitch (`AppHeader`, `AppSidebar`, `AppLayout`), inicio (`/app/inicio` con datos reales de sesión, cero datos simulados), acceso restringido (`/acceso-restringido`) y placeholders honestos.
  - F1.4 — Login real, seguro y accesible: integración con `useAuth().login()`, campos `organizationCode`, `email` y `password`, respuestas de error seguras (401, 429, 503, 400 y sesión expirada), botón mostrar/ocultar contraseña accesible por teclado, exclusiones de Stitch respetadas.
  - F1.5 — Establecimiento de contraseña inicial desde enlace seguro: ruta `/setup` con lectura exclusiva del fragmento `#token=...`, consumo de `POST /api/v1/auth/password-reset/complete`, limpieza del hash con `history.replaceState` y redirección a `/acceso`.
- **Frontend F2 — Configuración organizacional y puestos:** COMPLETADO (2026-09-25).
  - F2.1 — Tipos TypeScript, clientes organizationApi/positionApi e Idempotency-Key Web Crypto con capa HTTP (CSRF en memoria y cookies HttpOnly).
  - F2.2 — Pantalla de configuración organizacional: consulta de organización/sede, edición idempotente con expectedLockVersion, timezoneId solo lectura, reintento con misma clave solo tras error de red.
  - F2.3 — Catálogo de puestos de trabajo y mutaciones seguras: ruta `/app/puestos`, permisos por rol (admin muta, supervisor consulta, colaborador restringido), modales accesibles en portal, intenciones idempotentes con operation, payload, positionId y expectedLockVersion, descripciones neutrales sin consecuencias inventadas.
- **Frontend F3 — Directorio y gestión de colaboradores:** COMPLETADO (2026-09-25).
  - F3.1 — Tipos TypeScript (EmployeeResponse, CreateEmployeeRequest, UpdateEmployeeRequest, ChangeEmployeeStatusRequest, DeactivationPreviewResponse, EmployeeConflictCode, PendingEmployeeIntent) y cliente employeeApi con las siete operaciones reales. Sin campos no contractuales (correo, foto, teléfono).
  - F3.2 — Directorio de colaboradores, filtros y rutas: ruta `/app/equipo` para ADMINISTRADOR y SUPERVISOR (COLABORADOR restringido), carga coordinada Promise.all de empleados, puestos y sede, filtros de estado/puesto, búsqueda en memoria, estados de carga, error, vacío y sin resultados. Supervisor en modo consulta estricto.
  - F3.3 — Modales y mutaciones seguras: EmployeeModal (alta con validación y puestos activos; edición de nombre y puesto sin permitir guardar sin cambios ni reenviar positionId idéntico), EmployeeStatusModal (activación con confirmación directa; desactivación en 2 pasos con previewDeactivation y blockers reales), controles de mutación exclusivos para ADMINISTRADOR, unión discriminada PendingEmployeeIntent y reintento de Idempotency-Key únicamente ante fallo de red e intención idéntica.
- **Frontend F4 — Indisponibilidades de colaboradores (HU-08 / HU-10):** COMPLETADO (2026-09-25).
  - F4.1 — Tipos TypeScript y cliente API: contratos reales en `frontend/src/types/unavailability.ts` y cliente `unavailabilityApi.ts` para las 6 operaciones del backend (`getOrganizationUnavailabilities`, `getEmployeeUnavailabilities`, `getUnavailabilityById`, `createUnavailability`, `updateUnavailability`, `cancelUnavailability`). Soporte de formato parcial UTC y día completo. Mutaciones con cabecera `Idempotency-Key` obligatoria. Tipos sin conflicto artificial `UNAVAILABILITY_OVERLAP` (los intervalos solapados están permitidos en base de datos; la compatibilidad contra turnos corresponde a I3).
  - F4.2 — Listado y consulta de indisponibilidades: ruta `/app/indisponibilidades` integrada en enrutador SPA nativo y navegación con permisos de consulta para `ADMINISTRADOR` y `SUPERVISOR` (`COLABORADOR` redirigido a `/acceso-restringido`). Carga coordinada en paralelo con catálogo de colaboradores para resolución mediante `employeeMap`. Controles de ventana temporal (presets de 30, 60 y 90 días con parámetros `from`/`to` UTC) y filtro toggle para incluir canceladas. Filtros locales en memoria por colaborador, motivo y texto. Las notas restringidas (RN-014) nunca se renderizan en la tabla, filtros, resultados de búsqueda, mensajes de error ni registros del cliente. El frontend solo las utiliza dentro del modal autorizado cuando el contrato de la API las entrega; no se persisten en almacenamiento local ni se exponen en la vista de listado.
  - F4.3 — Modales y mutaciones seguras de indisponibilidades:
    - Alta, edición y cancelación mediante diálogos modales accesibles (`UnavailabilityModal` y `UnavailabilityCancelModal`).
    - Control de concurrencia optimista mediante `expectedLockVersion` obligatorio en edición y cancelación.
    - Manejo de idempotencia estricto con `PendingUnavailabilityIntent`: generación de UUID v4 con Web Crypto API; retención y reutilización de la misma `Idempotency-Key` únicamente ante error de red (`err.isNetworkError`) cuando la operación, el recurso, la versión y el payload coinciden exactamente; generación de nueva clave si cambian los datos o se inicia otra acción; limpieza tras éxito o error de servidor.
    - Gestión compartida y simétrica para `ADMINISTRADOR` y `SUPERVISOR` (RN-012, RN-014).
    - Regla temporal real aplicada en cliente y servidor: el alta permite registrar indisponibilidades con periodos en curso si `endsAt` está en el futuro, pero rechaza periodos finalizados; la edición y cancelación rechazan indisponibilidades cuyo periodo ya haya iniciado o finalizado (`startsAt <= now`, RN-013), con bloqueo visual en modales y tooltips descriptivos en la tabla.
    - Corrección de día completo: no se infiere `allDay` desde instantes UTC (la entidad backend no persiste la bandera y la medianoche local en organizaciones como America/Lima se almacena como 05:00:00Z). La edición abre siempre en modo parcial UTC por defecto sin derivar fechas con `slice(0, 10)`. La conversión a día completo requiere selección explícita y consciente de fechas locales por el usuario, mostrando la aclaración honesta: *"El formato original no se conserva. Para guardar como día completo, selecciona las fechas locales."*. Para el alta, la fecha inicial de día completo se calcula en la zona horaria del tenant (`timeZone`) usando `Intl.DateTimeFormat(..., { timeZone }).formatToParts()`, sin `now.toISOString().slice(0, 10)` ni `new Date('YYYY-MM-DD')`. La conversión a `endDate` exclusiva (+1 día) se realiza por aritmética de calendario numérico con `Date.UTC`.
- **Verificación manual integrada del MVP (F1–F4 / I2) y validaciones de compilación (2026-09-25):**
  - Entorno local levantado e integrado con SQL Server en Docker, backend Spring Boot local y frontend Vite.
  - Base demo aprovisionada mediante el operador de plataforma (`POST /api/v1/operator/setup/organizations` y `initial-access-link`) y establecimiento de contraseña mediante enlace de acceso inicial (`/setup#token=...`).
  - Inicio de sesión exitoso del administrador empresarial con la organización `DEMO-2026` y la cuenta `admin.demo@turnoshift.local`.
  - Navegación funcional comprobada por las vistas de Configuración (`/app/configuracion`), Puestos (`/app/puestos`), Equipo (`/app/equipo`) e Indisponibilidades (`/app/indisponibilidades`).
  - Dependencia operativa comprobada en flujo real: para seleccionar un colaborador al registrar indisponibilidades debe existir previamente un puesto activo y un colaborador asignado a dicho puesto.
  - Corrección visual y responsive validada manualmente: los modales de Puestos, Equipo e Indisponibilidades se renderizan mediante portal en `document.body` y cubren el viewport completo; no existe desbordamiento horizontal global; los scrollbars claros/cálidos se integran al tema visual; comportamiento revisado a 80 %, 100 % y 125 % de zoom.
  - Validación de compilación frontend: `npm run build` exitoso (`72 modules transformed`, 0 errores TypeScript, 0 advertencias Vite).
- **Pendientes fuera del alcance de I2:**
  - Evolución futura del dashboard operativo (`/app/inicio`).
  - Mejoras visuales futuras y pulido adicional de interfaz.
  - Preparación de datos/escenarios extendidos de presentación o demo comercial.
- **Incremento I3 — Planificación de turnos:** **EN CURSO** (2026-10-29).
  - **Subincremento I3.1-a — Persistencia y migraciones de planificación de turnos: COMPLETADO (2026-10-29).**
    - **V18 (`V18__schedule_planning_tables.sql`):** Definición de las 9 tablas del dominio en orden topológico acíclico (`schedules`, `schedule_drafts`, `assignments`, `assignment_revisions`, `draft_assignments`, `planning_change_sets`, `planning_warning_acknowledgements`, `schedule_publications`, `publication_assignments`).
      - Aislamiento multi-tenant estricto por `organization_id` y claves candidatas compuestas empresariales `(organization_id, schedule_id, ...)`.
      - Inicio de semana restringido a lunes canónico mediante `DATEDIFF(DAY, '19000101', week_start) % 7 = 0` (invariante frente a `@@DATEFIRST` e idioma).
      - Duración de turnos entre 15 y 960 minutos (`DATEDIFF(MINUTE, starts_at, ends_at) BETWEEN 15 AND 960`), con soporte completo para turnos nocturnos que cruzan la medianoche.
      - Restricciones de estados de borrador (`BORRADOR`, `PUBLICADO`, `DESCARTADO`) y coherencia con `result_publication_id`.
      - `change_type` restringido estrictamente a operaciones de planificación (`PUBLICACION_BORRADOR`, `REVISION_PUBLICACION`, `APROBACION_INTERCAMBIO`), excluyendo `ACTIVACION_POLITICA`.
      - Publicación de semana vacía permitida bajo confirmación explícita y motivo obligatorio no vacío (`DEC-03`).
      - Reconocimientos de advertencias operativas para exceso de minutos semanales (`DEC-04`) con validación de umbrales y estructura JSON.
      - Índices únicos filtrados: `uix_schedule_drafts_one_open` (como máximo un solo borrador abierto por cuadrante, **RN-027**) y `uix_draft_assignments_unique_published_ref`.
      - Ausencia de columna huérfana `shift_type_id` en `draft_assignments` y `assignment_revisions` (diferida a evolución futura).
    - **V19 (`V19__schedule_planning_circular_fks.sql`):** Resolución de dependencias circulares mediante dos pasos:
      - Validaciones *preflight fail-closed* (`IF EXISTS (...) THROW 50000, ...`) sobre `schedules.current_publication_id`, `schedule_drafts.base_publication_id` y `schedule_drafts.result_publication_id`.
      - Instalación de las 3 claves foráneas compuestas diferidas hacia `schedule_publications (organization_id, schedule_id, id)`: `fk_schedules_current_publication`, `fk_schedule_drafts_base_publication` y `fk_schedule_drafts_result_publication`.
    - **Cobertura de pruebas de persistencia (`SchedulePlanningPersistenceIT.java`):**
      - 9 bloques exhaustivos sobre SQL Server 2022 real (Testcontainers):
        1. Migración limpia V1–V19 e inspección física de las 9 tablas, columnas, constraints (`pk_*`, `uq_*`, `fk_*`, `chk_*`) e índices.
        2. Ciclo de publicación en dos pasos y claves circulares de V19.
        3. Aislamiento multi-tenant estricto entre organizaciones distintas.
        4. Rechazo de referencias cruzadas entre distintas semanas (`W1` vs `W2`) del mismo tenant.
        5. Unicidad de semana por sede (`uq_schedules_org_site_week`).
        6. Unicidad de un solo borrador abierto (`uix_schedule_drafts_one_open`).
        7. Checks de dominio: lunes canónico, duración de turno (15–960 min), cruce de medianoche, coherencia de borrador y `change_type`.
        8. Publicación vacía (`DEC-03`) y reconocimientos de advertencia (`DEC-04`).
        9. Upgrade progresivo V17 → V19 preservando intactos los datos de organizaciones, sedes, puestos, empleados, usuarios, políticas e indisponibilidades.
    - **Sincronización de pruebas de versión Flyway (V17 → V19):**
      - Actualizadas las aserciones de historial Flyway y catálogo de tablas en `SchemaUpgradeV3ToLatestIT`, `SpringBootStartupIT`, `PolicyPersistenceIT`, `UnavailabilityPersistenceIT` y `OrganizationsSitesSchemaIT`.
    - **Evidencia exacta de verificación:**
      - Comando:
        `cd backend; $env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test "-Dtest=SchedulePlanningPersistenceIT,SchemaUpgradeV3ToLatestIT,SpringBootStartupIT"`
      - Resultado:
        `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`
        `BUILD SUCCESS` (duración total 10:41 min).
    - **Aclaración de alcance de I3:**
      - **I3 sigue EN CURSO.**
      - Únicamente está completada su capa física de persistencia y migraciones de base de datos (`I3.1-a`).
      - No existen aún entidades de dominio Java, motor `ScheduleValidationEngine`, repositorios JDBC funcionales, casos de uso transaccionales, candados `sp_getapplock` (`TurnoShift_OrgPlanning_<organizationId>`), controladores HTTP, endpoints en `openapi.yaml` ni frontend de planificación (F5/F6).
  - **Subincremento I3.1-b — Dominio de Planificación, Motor de Validación y Persistencia JDBC: COMPLETADO (2026-10-29).**
    - **Modelo de dominio inmutable y value object propio (`com.turnoshift.schedule.domain`):**
      - `ShiftInterval`: value object propio del dominio de planificación con evaluación de intervalos semiabiertos $[startsAt, endsAt)$, duración en minutos ($15 \le \text{min} \le 960$) y solapamiento; utiliza excepciones propias (`InvalidShiftIntervalException`, `ScheduleDomainException`) sin propagar excepciones de indisponibilidades.
      - Entidades y value objects inmutables: `Schedule` (inicio en lunes canónico, `lockVersion >= 0`), `ScheduleDraft` (estados `BORRADOR`, `PUBLICADO`, `DESCARTADO` y coherencia estricta con `resultPublicationId`), `DraftAssignment` (coherencia de `assignmentId` y `baseRevisionId`), `Assignment`, `AssignmentRevision` (inmutable versionado), `SchedulePublication` (numeración, origen `INICIAL`/`REVISION`/`INTERCAMBIO` e inmutabilidad de referencias), `PublishedAssignmentRef`, `PublishedShiftRecord`, `PlanningChangeSet` (sin `ACTIVACION_POLITICA`, soporte de publicación vacía conforme a DEC-03), y `PlanningWarningAcknowledgement` (período de 7 días, umbral excedido conforme a DEC-04).
      - Inmutabilidad y seguridad de copias defensivas: arrays de huellas (`byte[]`) y listas (`List.copyOf`) encapsulados con clonación defensiva y métodos `equals`/`hashCode` con `Arrays.equals`.
    - **Motor determinista puro `ScheduleValidationEngine` (`com.turnoshift.schedule.domain.validation`):**
      - Evaluación en orden determinista libre de efectos secundarios y desacoplada de base de datos o HTTP:
        1. `POLICY_NOT_CONFIGURED`: error bloqueante si no existe versión de política activa para la organización.
        2. `PAST_SHIFT_MODIFICATION` (RN-031):
           - Turno nuevo (`assignmentId == null`) con `startsAt <= now`: bloqueado.
           - Turno derivado de publicación base previa ya iniciado (`startsAt <= now`) que permanece idéntico en horario, empleado y puesto: permitido sin falsos positivos.
           - Turno publicado ya iniciado que ha sido modificado o eliminado del borrador: bloqueado.
        3. `EMPLOYEE_INACTIVE`, `POSITION_INACTIVE` y `POSITION_MISMATCH` (RN-010): validación de catálogos y obligatoriedad de coincidencia exacta con el puesto principal del colaborador.
        4. `EMPLOYEE_UNAVAILABLE` (RN-019): intersección con indisponibilidades activas (`cancelled_at IS NULL`) mediante intervalos semiabiertos. Invariante de confidencialidad estricta: nunca expone ni incluye `restricted_note` (RN-014).
        5. `SHIFT_OVERLAP` (RN-017): solapamientos entre turnos del mismo colaborador dentro de la semana y contra turnos de frontera publicados de semanas adyacentes ($W-1$ y $W+1$).
        6. `MINIMUM_REST_VIOLATION` (RN-018): descanso continuo mínimo entre turnos consecutivos en la semana y a través de las fronteras de $W-1$ y $W+1$.
        7. `WEEKLY_PLANNED_MINUTES_EXCEEDED` (RN-023 / DEC-04): exceso de minutos semanales evaluado como advertencia operativa reconocible; no bloquea (`canPublish == true`). Atribución íntegra a la semana $W$ de los turnos nocturnos que inician en domingo dentro de $W$ y cruzan la medianoche finalizando el lunes de $W+1$ (PROP-I3-01 / PROP-I3-02).
    - **Huellas criptográficas canónicas SHA-256 (`byte[32]`):**
      - `WarningFingerprintCalculator`: cálculo canónico SHA-256 (`"w1|<orgId>|<employeeId>|<periodStart>|<periodEndExclusive>|<plannedMinutes>|<thresholdMinutes>"`) retornado como `byte[32]`.
      - `ValidationFingerprintCalculator`: cálculo canónico SHA-256 normalizado e invariante frente al orden de las asignaciones y advertencias suministradas, retornado como `byte[32]`. La representación hexadecimal (`String` de 64 caracteres) se expone exclusivamente como método de conveniencia para la futura capa HTTP.
    - **Puertos y repositorios JDBC (`com.turnoshift.schedule.domain.ports` y `com.turnoshift.schedule.infrastructure.persistence`):**
      - `ScheduleRepository` / `JdbcScheduleRepository`: consultas por ID y sede/semana, aislamiento multi-tenant estricto por `organization_id`, y actualización condicional del puntero vigente con avance de `lock_version`.
      - `ScheduleDraftRepository` / `JdbcScheduleDraftRepository`: creación y consulta de borradores con carga de líneas, e inserción/actualización/eliminación de asignaciones que incrementa obligatoriamente el `lock_version` del borrador, rechazando versiones obsoletas con `ScheduleConcurrentModificationException`.
      - `SchedulePublicationRepository` / `JdbcSchedulePublicationRepository`: método `createPublicationAtomic` que ejecuta la secuencia transaccional atómica en 7 pasos (`planning_change_sets` $\rightarrow$ `acknowledgements` $\rightarrow$ `assignments/revisions` $\rightarrow$ `schedule_publications` $\rightarrow$ `publication_assignments` $\rightarrow$ `UPDATE schedule_drafts` $\rightarrow$ `UPDATE schedules`), satisfaciendo íntegramente las claves foráneas compuestas circulares de V19 sin violaciones de integridad ni estados transitorios inconsistentes.
      - `PlanningContextSnapshotPort` / `JdbcPlanningContextSnapshotAdapter`: consulta desacoplada de instantáneas operativas (política activa, catálogo de empleados, catálogo de puestos, indisponibilidades activas y turnos publicados de frontera).
      - `ScheduleInfrastructureConfiguration`: configuración de infraestructura Spring Boot.
    - **Evidencia exacta de verificación focalizada:**
      - Comando:
        `cd backend; $env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test "-Dtest=ScheduleValidationEngineTest,ValidationFingerprintCalculatorTest,JdbcScheduleRepositoryIT,JdbcScheduleDraftRepositoryIT,JdbcSchedulePublicationRepositoryIT"`
      - Resultado:
        `Tests run: 32, Failures: 0, Errors: 0, Skipped: 0`
        `BUILD SUCCESS` (duración total 07:50 min).
    - **Aclaración y delimitación estricta de alcance:**
      - **I3 sigue EN CURSO.**
      - Las subfases `I3.1-a` e `I3.1-b` están COMPLETADAS.
      - Aún no existen casos de uso transaccionales de aplicación, idempotencia HTTP (`BusinessIdempotentOperationExecutor` / `api_operations`), adquisición del candado exclusivo de organización `TurnoShift_OrgPlanning_<organizationId>` (`sp_getapplock`), controladores REST, rutas en `docs/api/openapi.yaml` ni pantallas Frontend (F5/F6).
- **Siguiente paso del proyecto:**
  - Diseño técnico del subincremento **`I3.1-c — Casos de Uso, Idempotencia, Candado Operativo sp_getapplock y API HTTP`** (casos de uso transaccionales de borrador y publicación, deduplicación idempotente, candado `TurnoShift_OrgPlanning_<organizationId>`, integración coordinada con I2 para proteger asignaciones publicadas frente a mutaciones operativas, y controladores REST con respuestas RFC 9457).
- **Puntos transversales pendientes:**
  - DEC-06: procedimiento operativo de entrega fuera de banda del enlace de acceso inicial durante la fase piloto.
  - Rate limiting distribuido (migración futura de rate limiters en memoria a solución persistente/Redis al escalar horizontalmente).

---

## 6. Entorno de desarrollo

- JDK: Temurin 21.0.10 en `C:\Users\juan9\.jdks\temurin-21.0.10`
- Comando verify: `$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"; cmd /c "mvnw.cmd verify -B"`
- ryuk.disabled=true en `~/.testcontainers.properties`
- Puerto dev SQL Server: 127.0.0.1:11433 → contenedor `turnoshift-sqldev`
- Puerto backend por defecto: 8081 (usar 8082 si está ocupado vía `-Dserver.port=8082`)
- Perfil local (HTTP sin TLS): `SPRING_PROFILES_ACTIVE=local` o `application-local.properties`
