# TAREA ACTUAL — TURNOSHIFT

## Estado: I3 EN CURSO — Subincrementos I3.1-a (Persistencia y migraciones) e I3.1-b (Dominio, validación y persistencia JDBC) COMPLETADOS; Siguiente trabajo: diseño técnico de I3.1-c — Casos de uso, idempotencia, candado operativo sp_getapplock y API HTTP.

I0, I1 e I2 están **COMPLETADOS** (2026-09-25). El incremento **I3 — Planificación de turnos** se encuentra **EN CURSO** (2026-10-29). Sus dos primeras subfases, **I3.1-a — Persistencia y migraciones** y **I3.1-b — Dominio, validación y persistencia JDBC**, han sido **COMPLETADAS** y verificadas con 32 pruebas aprobadas. Siguiente trabajo: **diseño técnico de I3.1-c (Casos de uso transaccionales, idempotencia, candado operativo `sp_getapplock` por organización, integración con I2 y controladores REST)**. Los cambios son locales, sin commit.

---

## I1 — Acceso seguro e incorporación asistida

### Alcance completo de I1
- Estado de sesión actual (`GET /api/auth/session`)
- Inicio y cierre de sesión (`POST /api/auth/login`, `POST /api/auth/logout`)
- Obtención de token CSRF (`GET /api/auth/csrf`)
- Alta de organización + sede inicial + primer administrador (incorporación asistida)
- Inspección y completado de enlace de incorporación

### Subtarea completada: persistencia de identidad empresarial (correcciones incluidas)
- V4 `roles`: catálogo global (ADMINISTRADOR, SUPERVISOR, COLABORADOR). ✅
- V5 `app_users`: identidad empresarial con FK a organizations y roles. ✅
- V6 `password_reset_tokens`: tokens de configuración inicial y recuperación. ✅
- V7 `employee_id` guard: CHECK `employee_id IS NULL` mientras `employees` no existe. ✅
- V8 `normalized_email` collation: `COLLATE Latin1_General_100_BIN2`. ✅
- EmailNormalizer, PasswordPolicy, PasswordBlocklist (interfaz), Argon2PasswordService: implementados y probados. ✅
- DEC-08-IDENTITY, DEC-08-DEFER-FK-EMPLOYEE, DEC-08-EMPLOYEE-ID-GUARD, DEC-08-NORMALIZED-EMAIL-COLLATION, DEC-08-ARGON2ID en DECISIONS.md. ✅
- 35 pruebas unitarias (Surefire) + 51 IT = 86 PASS (estado al cerrar esta subtarea). ✅

### Subtarea completada: PoC-02 — Spring Security, Spring Session JDBC y CSRF (2026-08-31)
- V9 `spring_session`: tablas SPRING_SESSION y SPRING_SESSION_ATTRIBUTES. Esquema oficial de spring-session-jdbc:4.0.0. ✅
- `spring-boot-starter-security` (Spring Security 7.0.0) añadido al pom. ✅
- `spring-boot-starter-session-jdbc` (Spring Session 4.0.0) añadido al pom. ✅
- `spring-boot-webmvc-test` y `spring-security-test` añadidos (test). ✅
- `UserDetailsServiceAutoConfiguration` excluida explícitamente en `@SpringBootApplication`. Nombre verificado en el JAR real de `spring-boot-security:4.0.0`. ✅
- `SecurityConfig`: SecurityFilterChain con CSRF (`HttpSessionCsrfTokenRepository`, cabecera `X-CSRF-TOKEN`), rutas públicas/protegidas, manejadores 401/403 Problem+JSON. ✅
- `SecurityProblemHandlers`: `SessionRequiredEntryPoint` (401) y `TurnoShiftAccessDeniedHandler` (403 ACCESS_DENIED / 403 CSRF_INVALID). ✅
- `CsrfController`: `GET /api/v1/auth/csrf` → `{"token":"..."}`, materializa token diferido, Cache-Control: no-store. ✅
- `application.properties`: `spring.session.jdbc.initialize-schema=never`, timeout 1800s, cookie TSESSIONID HttpOnly SameSite=Lax Path=/api Secure=true. ✅
- `application-local.properties`: `Secure=false` para entornos HTTP locales (perfil `local`). ✅
- `DEC-08-SPRING-SESSION` y `DEC-08-SECURITY-INFRA` añadidos a DECISIONS.md. ✅
- `SecurityInfrastructureIT`: 18 tests IT sobre SQL Server real (Testcontainers). ✅
- Versiones en `SpringBootStartupIT`, `OrganizationsSitesSchemaIT`, `SchemaUpgradeV3ToLatestIT` actualizadas a V1–V9. ✅
- **BUILD SUCCESS: 35 unitarios + 69 IT = 104/104 PASS.** ✅

### Subtarea completada: I1-AUTH-CORE — Núcleo de autenticación empresarial multi-tenant y correcciones (2026-09-15)
- Corrección contractual en `docs/api/openapi.yaml`: `Path=/api` en Set-Cookie de respuesta login (verificado con Redocly: 0 errores). ✅
- Dominio puro: `AuthenticatedIdentity`, `AuthenticationCandidate`, `AuthenticationOutcome`, `AuthenticationResult`, `AuthenticationDataAccessException`. Sin dependencias de Spring, JDBC ni HTTP; toString nunca expone credenciales ni hashes. ✅
- Puerto de verificación `PasswordVerifier` implementado por `Argon2PasswordService`. ✅
- Caso de uso `AuthenticateUser`: normalización estricta de código y correo, validación de contraseña (sin blocklist en login, <=128 code points, espacios conservados), mitigación de diferencias de coste mediante hash señuelo (`decoyHash`) Argon2id generado una sola vez por instancia, y fail-closed ante estados o datos corruptos. ✅
- Manejo de disponibilidad en persistencia: `JdbcAuthenticationCandidateRepository` no enmascara `DataAccessException` como vacío; propaga `AuthenticationDataAccessException` de forma técnica y segura para habilitar 503 en HTTP, manteniendo `Optional.empty()` para usuario/org inexistente o UUID corrupto en fila. ✅
- ObjectMapper gestionado en seguridad: `SecurityConfig` y `SecurityProblemHandlers` migrados para usar el bean gestionado por el contenedor `JsonMapper` (Jackson 3.x nativo de Spring Boot 4.0.0), eliminando cualquier fallback manual con `new ObjectMapper()`. ✅
- Pruebas unitarias dirigidas: 16 pruebas en `AuthenticateUserTest` (incluyendo propagación y formato seguro ante indisponibilidad de BD) + 3 pruebas en `SecurityProblemHandlersTest` = 19 PASS en 0.6s. ✅
- Pruebas de integración dirigidas sobre SQL Server real (Testcontainers):
  - `JdbcAuthenticationCandidateRepositoryIT`: 8 pruebas PASS (incluyendo verificación de propagación de `AuthenticationDataAccessException` ante fallo real de conexión). ✅
  - `SecurityInfrastructureIT`: 18 pruebas PASS demostrando arranque y manejo de excepciones RFC 9457 con `JsonMapper` inyectado por Spring. ✅
- `DEC-08-AUTH-CORE` actualizado en `DECISIONS.md`. ✅

### Subtarea completada: I1-AUTH-HTTP — Endpoint HTTP de login, rate limiting y protección de sesión (2026-09-15)
- In-memory rate limiting por IP (`LoginRateLimiter`, `InMemoryLoginRateLimiter`): 5 intentos por 60s, evaluado con `Clock` inyectable determinista. Las peticiones rechazadas devuelven RFC 9457 `429 RATE_LIMITED` con `Retry-After` sin tocar `AuthenticateUser` (mitigando DoS por consumo de CPU en Argon2id). Contador restablecido tras login exitoso. ✅
- Resolución segura de IP para rate limiting: usa exclusivamente `HttpServletRequest.getRemoteAddr()`, sin confiar en `X-Forwarded-For` ni en cabeceras reenviadas controlables por el cliente. Probado contra intentos de evasión por manipulación de cabeceras. ✅
- Validación estructural pura y compartida: componente de dominio `LoginInputValidator` y `LoginValidationError` reutilizado por `LoginController` y `AuthenticateUser` sin duplicación de reglas ni parseo de texto. Devuelve 400 `VALIDATION_FAILED` con `errors` RFC 9457 ante request ausente, campos obligatorios faltantes, código de org inválido (<2, >32, caracteres no permitidos), formato email inválido, capacidad UTF-16 excedida o password >128 puntos de código. ✅
- DTOs de contrato OpenAPI 3.1: `LoginRequest`, `FieldErrorDto`, `SessionResponse`. Validación de payload con respuestas Problem Details `400 VALIDATION_FAILED` y detalle de `errors`. ✅
- Controlador `LoginController` (`POST /api/v1/auth/login`):
  - Integración sin duplicación con `AuthenticateUser`.
  - Rotación real del identificador de sesión vía `request.changeSessionId()` tras autenticación exitosa (previene fijación de sesión).
  - Persistencia explícita del `SecurityContext` a través de `SecurityContextRepository` (`HttpSessionSecurityContextRepository`).
  - `UsernamePasswordAuthenticationToken` inicializado con rol `ROLE_<ROL>` y credenciales `null`.
  - Fallo de autenticación mapeado a `401 AUTHENTICATION_FAILED` idéntico e indistinguible para cualquier causa (usuario inexistente, contraseña errónea, cuenta inactiva o hash nulo/vacío), impidiendo enumeración de usuarios o tenants.
  - Organización suspendida o cerrada con credenciales válidas mapeada a `403 ACCESS_DENIED`.
  - Traducción segura de `AuthenticationDataAccessException` a `503 SERVICE_UNAVAILABLE` sin filtrar detalles de SQL Server ni clases internas.
  - Ausencia total de creación de sesión en fallos (no se llama `getSession(true)` en rutas de rechazo). ✅
- Identidad de sesión y serialización JDBC:
  - `AuthenticatedIdentity` implementa `Principal` (`getName()` retorna `userId.toString()`, 36 chars) evitando truncamiento en la columna `SPRING_SESSION.PRINCIPAL_NAME VARCHAR(100)` de V9.
  - `AuthenticatedIdentity` implementa `Serializable` con `serialVersionUID = 1L`, permitiendo persistencia sin fallos en `SPRING_SESSION_ATTRIBUTES`. ✅
- Configuración de seguridad (`SecurityConfig`):
  - Bean explícito `SecurityContextRepository` (`HttpSessionSecurityContextRepository`) inyectado en `http.securityContext(...)`.
  - Inyección estricta de `tools.jackson.databind.ObjectMapper` administrado por Spring (Jackson 3) sin fallbacks manuales. ✅
- DEC-08-AUTH-HTTP añadido y actualizado en `DECISIONS.md`. ✅
- Pruebas unitarias dirigidas:
  - `LoginInputValidatorTest`: 12 tests PASS. ✅
  - `InMemoryLoginRateLimiterTest`: 6 tests PASS. ✅
  - `LoginControllerTest`: 17 tests PASS (incluyendo pruebas contra manipulación de `X-Forwarded-For` y todos los casos de 400). ✅
  - Total unitarias backend: 89 PASS. ✅
- Pruebas de integración sobre SQL Server real (Testcontainers):
  - `SecurityInfrastructureIT`: 21 tests PASS (incluyendo 3 tests completos de login HTTP end-to-end con rotación de sesión, verificación de persistencia en `SPRING_SESSION_ATTRIBUTES` e indistinguibilidad de 401). ✅
  - `JdbcAuthenticationCandidateRepositoryIT`: 8 tests PASS. ✅
  - Total IT ejecutados en I1: 29 PASS. ✅

### Subtarea completada: I1-AUTH-SESSION — Estado de sesión empresarial y cierre de sesión (2026-09-15)
- Factoría compartida `SessionResponse.from(AuthenticatedIdentity)` eliminando duplicación entre login y session. ✅
- Controlador `SessionController`:
  - `GET /api/v1/auth/session`: consulta de identidad derivada exclusivamente del `SecurityContext` activo (`AuthenticatedIdentity`), responde `200 OK` con `SessionResponse` (`user`, `organization`, `landingPage`), cabecera `Cache-Control: no-store`. Si la sesión está ausente o no autenticada, responde `401 SESSION_REQUIRED` con Problem Details RFC 9457. Garantizado que no crea, rota ni renueva sesiones (sin `getSession(true)`). ✅
  - `POST /api/v1/auth/logout`: requiere sesión activa y token CSRF válido. Invalida la sesión del servidor (`session.invalidate()`), limpia el `SecurityContext` (`clearContext()`), revoca la cookie de sesión `TSESSIONID` (`Max-Age=0`, `Path=/api`, `HttpOnly`, `SameSite=Lax`, `Secure` dinámico según propiedad `${server.servlet.session.cookie.secure:true}`), devuelve `204 No Content` con `Cache-Control: no-store`. ✅
  - Validación de cuerpo de logout: cuerpo ausente, en blanco o `{}` permitido; rechazo con `400 VALIDATION_FAILED` ante JSON malformado, tipos no objeto o campos adicionales, sin invalidar la sesión existente ni tocar la cookie. ✅
- DEC-08-AUTH-SESSION añadido a `DECISIONS.md`. ✅
- Pruebas unitarias dirigidas:
  - `SessionControllerTest`: 12 tests PASS (incluyendo admin, colaborador, 401 unauthenticated/anonymous, no creación de sesión, 204 con revocación de cookie Secure/insecure, 400 por payload con campos adicionales, array o malformado). ✅
  - Total unitarias backend: 101 PASS. ✅
- Pruebas de integración sobre SQL Server real (Testcontainers):
  - `SecurityInfrastructureIT`: 25 tests PASS (incluyendo ciclo completo login -> session 200 -> logout 204 -> session 401; logout sin sesión 401; logout con CSRF manipulado 403 y conservación de sesión; logout con campos adicionales 400 y conservación de sesión). ✅
  - `JdbcAuthenticationCandidateRepositoryIT`: 8 tests PASS. ✅
  - Total IT ejecutados en I1: 33 PASS. ✅

### Subtarea completada: I1-SESSION-VALIDATION — Revalidación continua de sesión y límite absoluto de 8 horas (2026-09-15)
- Límite absoluto de sesión de 8 horas (`authenticatedAt`):
  - Atributo registrado en la sesión HTTP en `LoginController` tras autenticación exitosa usando `Clock` inyectable (epoch millis `Long`).
  - Constantes centralizadas en `SessionSecurityConstants`: `AUTHENTICATED_AT_ATTRIBUTE`, `DEFAULT_MAX_LIFETIME = Duration.ofHours(8)`, `CLOCK_SKEW_TOLERANCE = Duration.ofSeconds(60)`.
  - Si el atributo falta, es corrupto/no numérico, excede el futuro (>60s) o sobrepasa las 8 horas de vida, el filtro invalida la sesión, limpia el `SecurityContext`, revoca la cookie `TSESSIONID` (`Max-Age=0`) y devuelve `401 SESSION_REQUIRED` RFC 9457 Problem Details. ✅
- Revalidación por petición contra base de datos (`SessionRevalidationFilter` + `RevalidateSession`):
  - Componente de dominio `SessionRevalidationCandidate` y puerto `SessionRevalidationRepository`.
  - Adaptador `JdbcSessionRevalidationRepository` ejecutando consulta parametrizada uniendo `app_users`, `organizations` y `roles` por `(u.id = ? AND u.organization_id = ?)`. Errores de SQL Server traducidos a `AuthenticationDataAccessException` técnica.
  - Servicio de aplicación `RevalidateSession` retornando `RevalidationOutcome` tipado (`VALID`, `ROLE_CHANGED`, `EXPIRED`, `INVALID`, `ORGANIZATION_ACCESS_DENIED`, `DATA_ACCESS_ERROR`).
  - Filtro `SessionRevalidationFilter` (`OncePerRequestFilter`) registrado en `SecurityConfig` antes de `AuthorizationFilter.class`.
  - Sesión válida: continúa la cadena sin alterar ni rotar el ID de sesión.
  - Mutación de rol en caliente: actualiza el principal `AuthenticatedIdentity` y la autoridad `ROLE_<NUEVO_ROL>`, lo persiste explícitamente en `SecurityContextRepository` y continúa sin rotar la sesión.
  - Usuario inactivo / dado de baja / `auth_version` alterada: invalida la sesión en el servidor, limpia el contexto, revoca la cookie y responde `401 SESSION_REQUIRED`.
  - Empresa suspendida o cancelada (`service_status != 'ACTIVA'`): invalida la sesión, revoca la cookie y responde `403 ACCESS_DENIED`.
  - Indisponibilidad de base de datos (`AuthenticationDataAccessException`): responde `503 SERVICE_UNAVAILABLE` Problem Details sin filtrar detalles internos y sin invalidar la sesión del cliente.
  - Exclusiones (`shouldNotFilter`): rutas públicas (`/api/health`, `/api/v1/auth/csrf`, `/api/v1/auth/login`) y logout (`POST /api/v1/auth/logout`) para permitir cierre de sesión limpio incluso con sesión vencida o tenant suspendido. ✅
- Inyección de dependencias en Spring Boot:
  - `IdentityConfiguration` provee `@Bean Clock` y `@Bean RevalidateSession`.
  - `@Autowired` explícito en el constructor de 4 argumentos de `LoginController`. ✅
- DEC-08-SESSION-VALIDATION registrado en `DECISIONS.md`. ✅
- Pruebas unitarias dirigidas:
  - `RevalidateSessionTest`: 14 tests PASS (límite 8h, skew, nulos/corruptos, fallo DB 503, usuario inactivo 401, auth_version 401, empresa suspendida 403, mutación de rol). ✅
  - `SessionRevalidationFilterTest`: 10 tests PASS (continuación en sesión válida, mutación de rol persistida, 401 cookie revocada, 403 cookie revocada, 503 sesión preservada, exclusiones públicas y logout). ✅
  - `LoginControllerTest`: 18 tests PASS (incluyendo verificación de guardado de `authenticatedAt`). ✅
  - Total unitarias backend: 126 PASS. ✅
- Pruebas de integración sobre SQL Server real (Testcontainers):
  - `JdbcSessionRevalidationRepositoryIT`: 6 tests PASS (consulta de usuario activo/inactivo, empresa suspendida, inexistente, fallo de conexión propagando `AuthenticationDataAccessException`). ✅
  - `SecurityInfrastructureIT`: 30 tests PASS (incluyendo suite completa de sesión válida, límite 8 horas superado 401 con cookie revocada, mutación de rol reflejada inmediatamente en `/api/v1/auth/session`, revocación inmediata por desactivación de usuario `is_active=0` y revocación inmediata por suspensión de empresa con 403). ✅
  - Total pruebas suite completa: 126 unitarias + 36 IT = 162 PASS. ✅

### Subtarea completada: I1-PASSWORD-BLOCKLIST — Proveedor Have I Been Pwned k-anonymity (2026-09-15)
- `PasswordBlocklistUnavailableException`: excepción técnica de dominio segura (`com.turnoshift.identity.domain`) para fallos del proveedor. Mensaje predeterminado inmutable y fijo (`[IDENTITY_BLOCKLIST_UNAVAILABLE]`), sin constructores públicos que acepten mensajes externos arbitrarios para prevenir cualquier fuga sensible. ✅
- Implementación de producción `HibpPasswordBlocklist` (`com.turnoshift.identity.infrastructure.client`):
  - Protocolo k-anonymity: cálculo de SHA-1 en hexadecimal mayúsculo (40 caracteres) de la contraseña ya normalizada (NFC).
  - Solo envía los primeros 5 caracteres a `https://api.pwnedpasswords.com/range/{PREFIJO}`.
  - La contraseña en texto plano y el hash completo jamás salen del servidor.
  - Búsqueda local de sufijo de 35 caracteres insensible a mayúsculas/minúsculas.
  - Cabeceras: `Add-Padding: true` y `User-Agent: TurnoShift/0.1`.
  - Configuración: `HttpClient` con `Redirect.NEVER` y timeout configurable (default 2000 ms).
  - Validación de cuerpo y formato estricto: rechaza body null, vacío (`""`) o en blanco (`body.isBlank()`); exige al menos una línea no vacía procesada con formato estricto `SUFFIX:COUNT`; ante cualquier línea no vacía corrupta o respuesta HTTP != 200/timeout/error de red/interrupción (restaurando `Thread.currentThread().interrupt()`), lanza `PasswordBlocklistUnavailableException`. ✅
- Integración en `PasswordPolicy`:
  - Si la contraseña está en la lista: produce `ViolacionPolitica.CONTRASENA_BLOQUEADA` (`IDENTITY_PASSWORD_BLOCKED`).
  - Si no está: validación exitosa.
  - Comprobaciones previas estructurales (longitud 15–128 puntos de código, vacío): evitan invocar la blocklist ante contraseñas estructuralmente inválidas (fail-fast).
  - Si la blocklist falla: propaga `PasswordBlocklistUnavailableException` (fail-closed, preparado para 503 en futuros endpoints). ✅
- Configuración Spring (`IdentityConfiguration`):
  - Bean `@Bean PasswordBlocklist` registrado con propiedades configurables (`turnoshift.security.password-blocklist.hibp.*`).
  - Cero llamadas de red durante el arranque del contexto Spring. ✅
- DEC-08-PASSWORD-BLOCKLIST registrado y actualizado en `DECISIONS.md`. ✅
- Pruebas unitarias dirigidas:
  - `HibpPasswordBlocklistTest`: 17 tests PASS (contraseña comprometida, ausente, envío exclusivo de 5 caracteres SHA-1, insensible a mayúsculas/minúsculas, respuestas corruptas/no numéricas/sufijo inválido, body vacío/espacios/saltos de línea, timeout, IOException, InterruptedException restaurando flag, HTTP 400/429/500/503, propagación en PasswordPolicy e inmutabilidad de mensaje de excepción). ✅
  - `PasswordPolicyTest`: 15 tests PASS (incluyendo pruebas de propagación fail-closed y short-circuit estructural). ✅
  - Total unitarias backend: 145 PASS. ✅

### Subtarea completada: I1-TOKEN-LIFECYCLE-CORE — Núcleo del ciclo de vida de tokens de primer acceso (2026-09-15)
- Generador criptográfico `SecureTokenGenerator`:
  - 256 bits de entropía (32 bytes) generados con `SecureRandom`.
  - Token en texto plano codificado en Base64 URL-safe sin relleno (`withoutPadding()`, 43 caracteres).
  - Cálculo de hash SHA-256 (32 bytes) para almacenamiento y consulta en `password_reset_tokens.token_hash BINARY(32)`.
  - Secreto en texto plano jamás persistido en base de datos.
  - `InitialSetupTokenEmission.toString()` redacta el secreto y todos los identificadores (`tokenId`, `organizationId`, `userId`, `rawToken`) a `[PROTECTED]` para prevenir fugas en logs y diagnósticos accidentales. ✅
- Emisión de tokens de primer acceso (`IssueInitialSetupToken`):
  - Verifica aptitud previa mediante consulta con bloqueo pesimista `findEligibilityForEmission` (`UPDLOCK, HOLDLOCK` sobre `app_users` y `organizations`): `organizations.service_status = 'ACTIVA'`, `app_users.is_active = 1` y `app_users.password_hash IS NULL`.
  - Serialización estricta ante emisiones paralelas: probado contra 10 emisiones concurrentes para el mismo usuario, garantizando exactamente 1 token activo al finalizar.
  - Expiración estricta de 30 minutos (`Duration.ofMinutes(30)`).
  - Revocación atómica dentro de la misma transacción (`@Transactional`): revoca todos los tokens no consumidos y no revocados del mismo `(organizationId, userId, INITIAL_SETUP)`, incluso si ya estaban vencidos por tiempo.
  - Inserción del nuevo token con hash SHA-256 de 32 bytes y clave foránea compuesta referenciando a `app_users(organization_id, id)`. ✅
- Inspección de solo lectura (`InspectResetToken`):
  - Validación estructural rápida (43 caracteres Base64 URL-safe) sin tocar la base de datos ante entradas malformadas.
  - Consulta por hash SHA-256 uniendo `password_reset_tokens`, `app_users` y `organizations`.
  - Validación de estado integral: token inválido si ya fue consumido, revocado, si ha expirado, si el propósito no es `INITIAL_SETUP`, si el usuario fue desactivado, si la organización no está `ACTIVA`, o si el usuario ya configuró contraseña (`password_hash IS NOT NULL`). ✅
- Consumo atómico y condicional (`ConsumeResetToken`):
  - Consumo en una sola sentencia condicional con revalidación integral:
    `UPDATE prt SET prt.consumed_at = ? FROM password_reset_tokens prt JOIN app_users u ON prt.organization_id = u.organization_id AND prt.user_id = u.id JOIN organizations o ON prt.organization_id = o.id WHERE prt.token_hash = ? AND prt.purpose = 'INITIAL_SETUP' AND prt.consumed_at IS NULL AND prt.revoked_at IS NULL AND prt.expires_at > ? AND u.is_active = 1 AND u.password_hash IS NULL AND o.service_status = 'ACTIVA'`.
  - Si el usuario fue desactivado, la organización no está activa o el usuario ya tiene contraseña, 0 filas son actualizadas, `consumed_at` permanece `NULL` en base de datos y se devuelve `TokenConsumptionResult.Failure`.
  - Bloqueo de fila exclusivo en SQL Server garantiza un único ganador ante concurrencia masiva (probado con 10 hilos simultáneos).
  - `TokenConsumptionResult.Failure` es un registro completamente vacío sin datos sensibles ni identificadores de usuario/organización, mitigando oráculos y enumeración.
  - Javadoc explícito documentando la participación transaccional y su futura composición atómica con el caso de uso de cambio de contraseña. ✅
- Persistencia y resiliencia (`JdbcPasswordResetTokenRepository`):
  - SQL 100% parametrizado.
  - Eliminación de bloques `catch (Exception)` silenciosos en RowMappers; fallos de datos corruptos o mapeo lanzan `SQLException` traducida a `TokenDataAccessException`.
  - Captura `DataAccessException` y traduce a `TokenDataAccessException` técnica y segura con mensaje constante e inmutable, sin filtrar sentencias SQL ni detalles internos. ✅
- Integración en Spring Boot (`IdentityConfiguration`):
  - Declaración del `@Bean SecureTokenGenerator` inyectado en los servicios de aplicación. ✅
- DEC-08-TOKEN-LIFECYCLE registrado y detallado en `DECISIONS.md`. ✅
- Pruebas unitarias dirigidas:
  - `SecureTokenGeneratorTest`: 4 tests PASS. ✅
  - `IssueInitialSetupTokenTest`: 6 tests PASS. ✅
  - `InspectResetTokenTest`: 12 tests PASS. ✅
  - `ConsumeResetTokenTest`: 4 tests PASS. ✅
  - `JdbcPasswordResetTokenRepositoryTest`: 6 tests PASS. ✅
  - Total unitarias backend: 177 PASS (32 nuevas). ✅
- Pruebas de integración sobre SQL Server real (Testcontainers):
  - `JdbcPasswordResetTokenRepositoryIT`: 17 tests PASS (aptitud de emisión, inserción y recuperación por hash, revocación de previos, consumo atómico, expiración, rechazo de propósitos cruzados, composite FK violation capturada, rechazo de consumo con usuario inactivo / org suspendida / password ya configurado con `consumed_at` permaneciendo NULL, concurrencia de consumo con 10 hilos simultáneos garantizando un solo ganador, concurrencia de emisión con 10 hilos simultáneos garantizando un solo token activo vía `UPDLOCK, HOLDLOCK`, y fallo inducido de conexión propagando `TokenDataAccessException`). ✅
  - Total pruebas suite completa backend: 177 unitarias + 53 IT = 230 PASS. ✅

### Subtarea completada: I1-PASSWORD-SETUP-CORE — Núcleo transaccional de primera contraseña (2026-09-15)
- Caso de uso orquestador `CompleteInitialPasswordSetup`:
  - Entrada: `rawToken` y `newPassword`. Sin contraseñas ni tokens en logs ni excepciones.
  - Validación estructural y consulta HIBP (`PasswordPolicy.validar`) ejecutadas **antes** de abrir transacción de base de datos para no retener conexiones JDBC ni bloqueos SQL durante latencia de red.
  - Normalización NFC y codificación de contraseña Argon2id ejecutadas fuera de transacción.
  - Propaga `PasswordBlocklistUnavailableException` de forma transparente hacia arriba (para posterior HTTP 503).
- Componente transaccional `TransactionalInitialPasswordSetupApplier`:
  - Anotado con `@Transactional(propagation = Propagation.REQUIRED)`.
  - Consume el token mediante `ConsumeResetToken`. Si falla, retorna `PasswordSetupResult.InvalidToken` sin modificar `app_users`.
  - Persiste el hash Argon2id e incrementa `auth_version = auth_version + 1` en una única sentencia atómica defensiva.
  - Si el `UPDATE` a `app_users` afecta 0 filas, lanza `InitialPasswordSetupConditionFailedException` para provocar el rollback automático de la transacción (dejando `consumed_at` del token como `NULL`).
  - Al completar exitosamente, revoca tokens `INITIAL_SETUP` restantes del usuario mediante `tokenRepository.revokeUnconsumedTokens`.
- Persistencia defensiva (`JdbcAppUserPasswordRepository`):
  - `UPDATE app_users` con `JOIN` a `organizations` exigiendo coincidencia de `organization_id`, `id`, `is_active = 1`, `password_hash IS NULL` y `o.service_status = 'ACTIVA'`.
  - Restricción de longitud de hash `<= 512`.
  - Mapeo estricto de `DataAccessException` a `PasswordSetupDataAccessException` con mensaje controlado.
- Invalidación de sesiones activas:
  - Logrado exclusivamente mediante el incremento atómico de `auth_version` sin borrado físico en `SPRING_SESSION`, compatible con `SessionRevalidationFilter`.
- DEC-08-PASSWORD-SETUP documentado en `DECISIONS.md`. ✅
- Pruebas unitarias dirigidas:
  - `CompleteInitialPasswordSetupTest`: 11 tests PASS. ✅
  - `JdbcAppUserPasswordRepositoryTest`: 3 tests PASS. ✅
- Pruebas de integración en SQL Server real (Testcontainers):
  - `CompleteInitialPasswordSetupIT`: 11 tests PASS (flujo feliz, Argon2id verificado, auth_version incrementado, tokens restantes revocados, rechazo de contraseñas inválidas/bloqueadas, rollback completo con `consumed_at` permaneciendo `NULL` ante usuario inactivo/org suspendida/hash existente, e invalidación de sesión previa en `RevalidateSession`). ✅
- Suite completa del backend: 191 unitarias + 72 IT en I1 (11 en `CompleteInitialPasswordSetupIT`) = 100% PASS. ✅

### Subtarea completada: I1-PASSWORD-SETUP-HTTP — Endpoints de inspección y completado de primera contraseña (2026-09-15)
- Alineación estricta con `docs/api/openapi.yaml`:
  - `INITIAL_SETUP` establecido explícitamente como el único propósito soportado y habilitado en esta versión.
  - `PASSWORD_RESET` conservado en el esquema/enumeración para compatibilidad futura (HU-04.2), pero explícitamente no habilitado en `inspect` ni `complete` (respondiendo `422 RESET_LINK_INVALID`).
  - Redocly CLI lint: 0 errores, 0 advertencias. ✅
- In-memory rate limiting por IP (`PasswordResetRateLimiter`, `InMemoryPasswordResetRateLimiter`):
  - 10 intentos por 60s para inspección (`tryAcquireInspect`).
  - 5 intentos por 60s para completado (`tryAcquireComplete`).
  - Evaluación determinista mediante `Clock` inyectable.
  - Resolución segura de IP vía `HttpServletRequest.getRemoteAddr()` (rechazando / ignorando `X-Forwarded-For`).
  - Respuestas `429 RATE_LIMITED` con cabecera `Retry-After` y `Cache-Control: no-store` antes de invocar la lógica de dominio o Argon2id.
  - Restablecimiento de cuota de completado (`resetComplete`) tras éxito legítimo. ✅
- Manejo HTTP pre-controlador y parseo seguro en `PasswordResetController`:
  - Validación de cabecera `Content-Type`: exige compatibilidad con `application/json`; rechaza `text/plain`, `application/xml` o ausencia de cabecera con `400 VALIDATION_FAILED` RFC 9457 y `Cache-Control: no-store`.
  - Cuerpos vacíos o en blanco rechazados con `400 VALIDATION_FAILED` tipando los campos requeridos (`REQUIRED`).
  - Parseo seguro con Jackson `ObjectMapper` gestionado por Spring, validando nodo raíz objeto (`isObject()`).
  - Validación estricta de tipos de campo (`isTextual()`), rechazando números, booleanos, arrays u objetos con `INVALID_FORMAT` sin eco de datos.
  - Manejador `@ExceptionHandler` acotado a `PasswordResetController` para `HttpMessageNotReadableException`, `HttpMediaTypeNotSupportedException` y `HttpMediaTypeNotAcceptableException`, impidiendo delegación en el error handler por defecto de Spring Boot o respuestas HTML.
  - Cero eco de secretos, contraseñas, fragmentos de JSON ni trazas internas de Jackson. ✅
- DTOs de capa web (`com.turnoshift.identity.infrastructure.web`): `InspectLinkRequest`, `InspectLinkResponse`, `CompletePasswordSetupRequest`. ✅
- Controlador `PasswordResetController`:
  - `POST /api/v1/auth/password-reset/inspect`:
    - Token válido: `200 OK` con `Cache-Control: no-store` y `{"valid": true, "purpose": "INITIAL_SETUP"}`.
    - Token inválido / expirado / revocado / consumido / usuario inactivo / org suspendida / password existente / `PASSWORD_RESET`: `422` con Problem Details `RESET_LINK_INVALID` (`/problems/reset-link-invalid`, `detail: "El enlace ha vencido o ya fue utilizado."`, `errors: []`).
    - Error de base de datos (`TokenDataAccessException`): `503 SERVICE_UNAVAILABLE` sin filtrar detalles internos. ✅
  - `POST /api/v1/auth/password-reset/complete`:
    - **Pre-inspección optimizada:** ejecuta `InspectResetToken.inspect` antes de validar la política de contraseñas o calcular Argon2id; si el enlace no es utilizable (incluyendo `PASSWORD_RESET`), responde inmediatamente `422 RESET_LINK_INVALID`, ahorrando llamadas de red a HIBP y cómputo pesado de CPU (~500ms).
    - Ejecución de `CompleteInitialPasswordSetup`:
      - Éxito: `204 No Content` sin cuerpo, `Cache-Control: no-store`. No crea sesión, no inicia sesión automáticamente ni altera el `SecurityContext`. Restablece rate limit de la IP.
      - Carrera concurrente / token consumido: `422 RESET_LINK_INVALID`.
      - Violación de política de contraseñas: `400 VALIDATION_FAILED` con errores tipados en el campo `newPassword` (`IDENTITY_PASSWORD_TOO_SHORT`, `IDENTITY_PASSWORD_BLOCKED`, etc.).
      - Indisponibilidad de HIBP (`PasswordBlocklistUnavailableException`) o base de datos (`PasswordSetupDataAccessException`, `TokenDataAccessException`): `503 SERVICE_UNAVAILABLE`. ✅
- Exclusión en filtro de revalidación (`SessionRevalidationFilter.shouldNotFilter`):
  - Añadidas `/api/v1/auth/password-reset/inspect` y `/api/v1/auth/password-reset/complete` para evitar revalidación espuria en peticiones con cookies residuales. ✅
- Protección CSRF:
  - Ambos endpoints requieren cabecera `X-CSRF-TOKEN` y sesión anónima o autenticada válida; sin CSRF, Spring Security responde `403 CSRF_INVALID` (`/problems/csrf-invalid`). ✅
- DEC-08-PASSWORD-SETUP-HTTP añadido y actualizado en `DECISIONS.md`. ✅
- Pruebas unitarias dirigidas:
  - `InMemoryPasswordResetRateLimiterTest`: 7 tests PASS. ✅
  - `PasswordResetControllerTest`: 42 tests PASS (incluyendo pruebas de JSON roto, cuerpos vacíos, tipos no texto, Content-Type incompatible/ausente, y contrato de `PASSWORD_RESET` no habilitado). ✅
  - `SessionRevalidationFilterTest`: 10 tests PASS (incluyendo verificación de exclusión de ambas rutas). ✅
  - Total unitarias backend: 240 PASS (49 nuevas en este bloque). ✅
- Pruebas de integración sobre SQL Server real (Testcontainers):
  - `PasswordResetHttpIT`: 7 tests PASS (flujo completo inspect 200 -> complete 204 -> BD actualizada con hash Argon2id y auth_version incrementado -> re-inspect 422 -> login 200 con nueva clave; rechazo 403 sin CSRF en inspect y complete; rechazo 400 por contraseña corta sin consumo de token; rechazo 422 por token inexistente; prueba de contrato para token `PASSWORD_RESET` respondiendo 422 en inspect y complete sin consumo; pre-controlador de errores de parseo y Content-Type respondiendo 400). ✅
  - Total IT ejecutados en I1: 79 PASS (7 en `PasswordResetHttpIT`). ✅

### Subtarea completada: I1-OPERATOR-PERSISTENCE — Persistencia y modelo de operadores de plataforma (2026-09-16)
- Migración `V10__platform_operators.sql`:
  - Tabla `platform_operators`: `id` (UUID, PK), `email` (NVARCHAR(254)), `normalized_email` (NVARCHAR(254) COLLATE Latin1_General_100_BIN2, UNIQUE), `display_name` (NVARCHAR(150)), `password_hash` (VARCHAR(512)), `is_active` (BIT, default 1), `auth_version` (BIGINT, default 1), `lock_version` (BIGINT, default 0), `created_at` (DATETIMEOFFSET), `updated_at` (DATETIMEOFFSET).
  - Restricciones CHECK no vacías para email, normalized_email, display_name, password_hash.
  - Restricciones CHECK no negativas para auth_version y lock_version.
  - Índice `ix_platform_operators_active_lookup` sobre `(id, is_active, auth_version)`.
  - Aislamiento absoluto: sin `organization_id`, sin FK a `roles` ni referencias a `app_users`.
- Dominio puro `PlatformOperator`:
  - Invariantes de validación defensiva (campos no nulos, no vacíos/blancos, capacidades físicas UTF-16, versiones no negativas).
  - `toString()` redacta `email=[PROTECTED]`, `normalizedEmail=[PROTECTED]`, `passwordHash=[PROTECTED]` para evitar fugas en logs y diagnósticos.
- Excepción técnica `OperatorDataAccessException`:
  - Mensaje técnico fijo `[OPERATOR_DATA_ACCESS_ERROR] Error al acceder a los datos de persistencia del operador de plataforma.`, sin constructores con mensajes externos arbitrarios.
- Puerto `PlatformOperatorRepository` y adaptador `JdbcPlatformOperatorRepository`:
  - Consultas parametrizadas por `id` y `normalizedEmail` (con collation binaria exacta BIN2).
  - Operaciones atómicas: `save`, `updateAuthVersion` (bloqueo condicional optimista), `updateActiveStatus` y `count`.
  - Traducción segura de cualquier `DataAccessException` a `OperatorDataAccessException`.
- Pruebas unitarias dirigidas:
  - `PlatformOperatorTest`: 8 tests PASS.
  - `JdbcPlatformOperatorRepositoryTest`: 9 tests PASS.
  - Total unitarias backend: 257 PASS (17 nuevas).
- Pruebas de integración en Testcontainers SQL Server:
  - `JdbcPlatformOperatorRepositoryIT`: 13 tests PASS.
  - `SpringBootStartupIT`, `SchemaUpgradeV3ToLatestIT`, `SecurityInfrastructureIT`, `OrganizationsSitesSchemaIT` actualizados a V10: 100% PASS.

- Autenticación y bootstrap de operadores de plataforma (I1-OPERATOR-AUTH-CORE-BOOTSTRAP):
  - Identidad desacoplada `PlatformOperatorIdentity` (`Principal`, `Serializable`, `toString()` redactado seguro).
  - Caso de uso `AuthenticateOperator` con mitigación estricta de temporización (consume `decoyHash` ante operador no encontrado, hash ausente o hash malformado/excepción de verificador, para cuentas activas e inactivas), fail-closed indistinguible y propagación transparente de `OperatorDataAccessException`.
  - Puerto `OperatorInserter` y componente `@Transactional` `TransactionalOperatorInserter` con exclusión mutua real mediante `sp_getapplock` (`LockOwner = 'Transaction'`). Captura acotada de `OperatorDataAccessException` alrededor de `count()` y `save()` registrando mensaje estático seguro y lanzando `BootstrapConfigurationException` sin causa encadenada para evitar fugas de SQL/driver.
  - Runner seguro `OperatorBootstrapRunner` (ApplicationRunner) condicionado a `turnoshift.operator.bootstrap.enabled=true`, lectura exclusiva desde variables de entorno dedicadas, validación con `PasswordPolicy` y fail-closed ante indisponibilidad de HIBP (`BootstrapConfigurationException`).
  - Pruebas unitarias dirigidas:
    - `PlatformOperatorIdentityTest`: 14 tests PASS.
    - `AuthenticateOperatorTest`: 11 tests PASS (verificación explícita de `decoyHash` ante hash corrupto en cuenta activa e inactiva).
    - `OperatorBootstrapRunnerTest`: 11 tests PASS (incluye fallos en `count` y `save`).
    - `TransactionalOperatorInserterTest`: 5 tests PASS (aislamiento de fallos en `count`, `save`, `sp_getapplock`, omisión y éxito).
    - Total unitarias backend: 298 PASS (41 nuevas).
  - Pruebas de integración en Testcontainers SQL Server:
    - `OperatorBootstrapIT`: 7 tests PASS (bootstrap desactivado, creación válida con hash Argon2id verificado, operador preexistente, contraseña corta, blocklist caída, concurrencia real serializada con `sp_getapplock` verificada con `Future.get(30, s)` estricto sin tolerancia de excepciones, uso de blocklist inyectada).
    - Total integración backend: 99 PASS (7 nuevas).

- Autenticación, sesión y seguridad del operador de plataforma (I1-OPERATOR-SECURITY-SESSION):
  - Contrato OpenAPI (`docs/api/openapi.yaml`): endpoints `POST /api/v1/operator/auth/login`, `GET /api/v1/operator/auth/session` y `POST /api/v1/operator/auth/logout`. Esquemas `OperatorLoginRequest`, `OperatorSessionResponse`, `OperatorSessionData`. Seguridad `operatorSession` (cookie `TSESSIONID`, rol `ROLE_PLATFORM_OPERATOR`). Lint de Redocly: 0 errores.
  - Rate limiting por IP en memoria (`InMemoryOperatorLoginRateLimiter`): 5 intentos por 60s con `Clock` determinista. Rechaza con 429 `RATE_LIMITED` antes de Argon2id mitigando ataques DoS. Resolución de IP exclusivamente con `request.getRemoteAddr()`.
  - Validación estructural de entrada (`OperatorLoginInputValidator`): email <= 254 chars UTF-16, password <= 128 puntos de código. Rechazo con 400 `VALIDATION_FAILED` RFC 9457 con detalle de campos erróneos.
  - Controlador `OperatorLoginController`: integra `AuthenticateOperator`, rotación real de sesión con `request.changeSessionId()`, persistencia explícita en `SecurityContextRepository` con `PlatformOperatorIdentity` y única autoridad `ROLE_PLATFORM_OPERATOR`, marca de tiempo `authenticatedAt`. Mapeo unificado e indistinguible a 401 `AUTHENTICATION_FAILED` y resiliencia 503 ante `OperatorDataAccessException` sin fugas.
  - Controlador `OperatorSessionController`: `GET` devuelve datos seguros del operador y `landingPage: "/operator/inicio"` con `Cache-Control: no-store`. `POST` invalida sesión, limpia contexto y revoca cookie `TSESSIONID` (`Max-Age=0`).
  - Revalidación polimórfica en `SessionRevalidationFilter`:
    - `RevalidateOperatorSession` evalúa límite absoluto de 8 horas (`authenticatedAt`) y consulta en BD por ID, `is_active = 1` y `auth_version`.
    - Revocación inmediata: operador desactivado o cambio de `auth_version` produce 401 con cookie revocada.
    - Error de BD produce 503 sin invalidar sesión.
    - Exclusión en `shouldNotFilter` para login y logout de operador.
  - Frontera estricta de autorización en `SecurityConfig`:
    - `/api/v1/operator/**` exige exclusivamente `ROLE_PLATFORM_OPERATOR`. Usuario empresarial que intenta acceder recibe 403 `ACCESS_DENIED`.
    - `/api/v1/auth/session` y `/api/v1/auth/logout` exigen roles empresariales. Operador que intenta acceder recibe 403 `ACCESS_DENIED`.
    - `.anyRequest().denyAll()`.
  - Pruebas unitarias dirigidas:
    - `OperatorLoginInputValidatorTest`: 7 tests PASS.
    - `InMemoryOperatorLoginRateLimiterTest`: 6 tests PASS.
    - `RevalidateOperatorSessionTest`: 9 tests PASS.
    - `OperatorLoginControllerTest`: 7 tests PASS.
    - `OperatorSessionControllerTest`: 8 tests PASS.
    - `SessionRevalidationFilterTest`: 13 tests PASS (incluyendo 3 nuevas pruebas de revalidación polimórfica de operador).
    - Total unitarias backend: 338 PASS (40 nuevas).
  - Pruebas de integración sobre Testcontainers SQL Server real:
    - `OperatorSecuritySessionIT`: 7 tests PASS (flujo completo login/session/logout con rotación y persistencia de sesión en SQL Server, CSRF ausente/inválido 403, rate limiting 429, revocación inmediata por desactivación `is_active=0`, revocación inmediata por avance de `auth_version`, bloqueo cruzado de usuario empresarial accediendo a rutas de operador 403, y bloqueo cruzado de operador accediendo a rutas empresariales 403).
    - Total integración backend en I1: 106 PASS (7 nuevas).

- Incorporación asistida de organizaciones e idempotencia mono-transaccional (I1-ORGANIZATION-ONBOARDING):
  - Contrato OpenAPI (`docs/api/openapi.yaml`): añadido endpoint `POST /api/v1/operator/setup/organizations`, respuestas 201, 400, 401, 403, 409 (`IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_KEY_EXPIRED`, `ORGANIZATION_CODE_CONFLICT`), 503. Validado con Redocly CLI: 0 errores, 0 advertencias.
  - Migración `V11__api_operations.sql`: tabla `api_operations` para deduplicación idempotente, índices únicos filtrados por actor (operador y usuario de negocio), restricción de coherencia de actor `chk_api_operations_actor_coherence`, coherencia de estado `chk_api_operations_status_coherence` y FK compuesta `(organization_id, actor_user_id)`.
  - Dominio y validación:
    - Comando inmutable tipado `IncorporateOrganizationCommand`.
    - Huella canónica inequívoca `RequestFingerprintCalculator`: serialización prefijada por longitud (`<len>:<val>;`) en UTF-8 y hash SHA-256 BINARY(32).
    - Validador puro `IncorporateOrganizationInputValidator`: verifica Idempotency-Key UUID, código de organización (2-32 caracteres `[A-Z0-9-]`), displayNames (1-150), zona horaria IANA válida y correo RFC 5322 simplificado (<=254 unidades UTF-16).
  - Configuración: `OrganizationProperties` con validación `@PostConstruct` de `defaultMaxActiveEmployees > 0` (fallback seguro 50).
  - Caso de uso mono-transaccional `IncorporateOrganization`:
    - Adquiere Lock 1 de idempotencia (`TurnoShift_Idem_<opId>_<key>`).
    - Si existe la operación: verifica TTL de 24h (`IDEMPOTENCY_KEY_EXPIRED`), valida coincidencia de huella (`IDEMPOTENCY_KEY_REUSED`), y emite replay idéntico (`201` o `409`).
    - Si es nueva: adquiere Lock 2 (`TurnoShift_OnboardingOrgCode_<code>`), comprueba disponibilidad del código; si ya existe, registra `REJECTED/409` en `api_operations` sin crear entidades de dominio y confirma la transacción. Si está libre, crea `organizations`, `sites` (`PRINCIPAL`) y `app_users` (`password_hash = NULL`), registra `SUCCEEDED/201` y confirma la transacción en un único commit. Rollback total ante error técnico no controlado.
  - Controlador `OperatorOrganizationSetupController`:
    - Manejo de cabeceras de respuesta: `X-Request-ID` fresco, `Idempotency-Replayed: true` en replays, omisión de `Location`.
    - Replay de rechazos reconstruye dinámicamente `requestId` e `instance` a partir de la plantilla segura en BD.
  - Pruebas unitarias dirigidas:
    - `RequestFingerprintCalculatorTest`: 4 tests PASS.
    - `IncorporateOrganizationInputValidatorTest`: 7 tests PASS.
    - `OrganizationPropertiesTest`: 3 tests PASS.
    - `IncorporateOrganizationTest`: 6 tests PASS.
    - `OperatorOrganizationSetupControllerTest`: 10 tests PASS.
    - Total unitarias backend: 368 PASS (30 nuevas).
  - Pruebas de integración sobre Testcontainers SQL Server real:
    - `OperatorOrganizationOnboardingIT`: 12 tests PASS (happy path 201, replay exitoso 201 con Idempotency-Replayed, conflicto de código 409 con plantilla segura en api_operations sin crear entidades, replay de conflicto 409 con nuevo requestId, reuso con distinta huella 409, clave expirada 409, concurrencia misma clave serializada con applock, concurrencia mismo código con applock, fallo técnico con rollback total comprobando 0 filas en 4 tablas, bloqueo cruzado a usuario de negocio 403, operador sin CSRF 403, validación de clave no UUID 400 y campos inválidos 400).
    - Flyway V11 verificado en `SpringBootStartupIT`, `OrganizationsSitesSchemaIT`, `SecurityInfrastructureIT`, `OperatorSecuritySessionIT`: 56 tests IT adicionales PASS (total 68 IT PASS en esta sesión).

- Emisión y reemplazo seguro del enlace de acceso inicial (I1-INITIAL-ACCESS-LINK-HTTP):
  - Contrato OpenAPI (`docs/api/openapi.yaml`): añadido endpoint `POST /api/v1/operator/setup/organizations/{organizationId}/initial-access-link`, con parámetros de ruta y cabecera `Idempotency-Key` (UUID). Respuestas 200 (`EmitInitialLinkResponse` con `setupLink`), 400 (`VALIDATION_FAILED`), 401 (`SESSION_REQUIRED`), 403 (`ACCESS_DENIED`, `CSRF_INVALID`), 404 (`RESOURCE_NOT_FOUND`), 409 (`INITIAL_LINK_ALREADY_EMITTED`, `CONCURRENT_MODIFICATION`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_KEY_EXPIRED`, `INVALID_STATE_TRANSITION`), 503 (`SERVICE_UNAVAILABLE`). Redocly CLI lint: 0 errores, 0 advertencias.
  - Dominio y validación:
    - Comando inmutable tipado `EmitInitialAccessLinkCommand` (`organizationId`, `adminUserId`, `expectedVersion`).
    - Huella canónica inequívoca `RequestFingerprintCalculator`: serialización `<len>:<val>;` en UTF-8 con SHA-256 BINARY(32).
    - Validador puro `EmitInitialAccessLinkInputValidator`: valida formato UUID para `organizationId`, `adminUserId`, `Idempotency-Key`, y `expectedVersion >= 0`.
    - Resultado del caso de uso `EmitInitialAccessLinkResult` fuertemente tipado mediante interfaz sellada (`sealed interface`).
  - Persistencia y control de concurrencia:
    - `JdbcOperatorInitialLinkRepository`: consulta SQL unificada con `WITH (UPDLOCK, HOLDLOCK)` sobre `organizations` y `app_users`, comprobando pertenencia tenant-user, roles y estados en un solo paso. Actualización condicional atómica `UPDATE app_users SET lock_version = lock_version + 1, updated_at = SYSUTCDATETIME() WHERE id = ? AND lock_version = ?`.
  - Caso de uso mono-transaccional `EmitInitialAccessLink`:
    - Adquiere Lock 1 (`TurnoShift_Idem_<opId>_<key>`) con `sp_getapplock` (`LockOwner = 'Transaction'`).
    - Deduplica idempotencia: si la clave ya existe, valida TTL de 24h (`IDEMPOTENCY_KEY_EXPIRED`), valida huella (`IDEMPOTENCY_KEY_REUSED`), y si fue exitosa, devuelve `409 INITIAL_LINK_ALREADY_EMITTED` con `Idempotency-Replayed: true` (sin reproducir el secreto en texto plano ni generar enlaces colaterales).
    - Si es nueva: adquiere Lock 2 (`TurnoShift_AdminSetup_<adminUserId>`), inspecciona existencia y pertenencia (404), valida versión optimista `expectedVersion == lock_version` (409 `CONCURRENT_MODIFICATION`), valida estado activo y `password_hash IS NULL` (409 `INVALID_STATE_TRANSITION`).
    - Emite token criptográfico de 256 bits mediante `IssueInitialSetupToken.issue()` (revocando previos de tipo `INITIAL_SETUP`), incrementa `lock_version` de forma atómica y registra en `api_operations` la operación con `response_body` sanitizado (`secretRedacted: true`, cero texto plano y cero URLs completas). Commit mono-transaccional único.
    - Manejo seguro de fallos técnicos: si la persistencia/emisión lanza `TokenDataAccessException`, se traduce a `OperatorDataAccessException` forzando rollback total de la transacción managed por Spring.
  - Controlador web `OperatorInitialAccessLinkController`:
    - Genera la URL con fragmento hash `#token=...` garantizando que el secreto no se transmita en cabeceras `Referer` ni líneas de petición HTTP.
    - Respuestas con cabeceras `Cache-Control: no-store` y `X-Request-ID` fresco.
    - Manejo de excepciones `@ExceptionHandler({OperatorDataAccessException.class, TokenDataAccessException.class})`: responde HTTP 503 `SERVICE_UNAVAILABLE` con Problem Details, cabecera `X-Request-ID` y cero exposición de detalles internos.
  - Configuración: `InitialSetupProperties` (`turnoshift.auth.initial-setup-base-url`) con validación de URL y perfiles `default` (`https://app.turnoshift.example/setup`) y `local` (`http://localhost:5173/setup`).
  - Pruebas unitarias dirigidas:
    - `EmitInitialAccessLinkInputValidatorTest`: 6 tests PASS.
    - `RequestFingerprintCalculatorTest`: 5 tests PASS.
    - `EmitInitialAccessLinkTest`: 14 tests PASS (incluye traducción de `TokenDataAccessException` a `OperatorDataAccessException`).
    - `OperatorInitialAccessLinkControllerTest`: 13 tests PASS (incluye respuesta 503 segura ante `TokenDataAccessException`).
    - Total unitarias backend: 402 PASS (34 nuevas en esta subtarea).
  - Pruebas de integración sobre Testcontainers SQL Server real:
    - `OperatorInitialAccessLinkIT`: 14 tests PASS (emisión exitosa 200 con formato `#token=`, no persistencia de secretos en `api_operations`, replay 409 `INITIAL_LINK_ALREADY_EMITTED` sin nuevo enlace, reemplazo deliberado con nueva clave y version incrementada, rechazo por versión desactualizada 409 `CONCURRENT_MODIFICATION`, rechazo por usuario que ya tiene contraseña 409 `INVALID_STATE_TRANSITION`, 404 admin no encontrado o de otra organización, 404 organización no encontrada, concurrencia de la misma clave serializada con `sp_getapplock`, reuso con distinta huella 409, clave expirada 409, bloqueo a usuario de negocio 403, operador sin CSRF 403, validaciones 400, y fallo técnico por `TokenDataAccessException` con respuesta 503 segura y rollback total comprobado: 0 tokens nuevos, revocación previa revertida, lock_version 0 y 0 registros en api_operations).
    - Total integración backend en I1: 132 PASS (14 de enlace inicial + 12 de onboarding + 106 anteriores).

### Cierre formal de I1 — Evidencia de verificación (2026-09-16)
- **Incremento 1 (I1): COMPLETADO.**
- **Comando de verificación de cierre:**
  ```powershell
  $env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'
  .\mvnw.cmd verify -B
  ```
- **Resultado:** `BUILD SUCCESS`, exit code 0, duración 38:44 min.
- **Pruebas unitarias (Surefire):** 402/402 PASS (0 fallos, 0 errores, 0 omitidos).
- **Pruebas de integración (Failsafe):** 183/183 PASS (0 fallos, 0 errores, 0 omitidos).
- **Total pruebas de backend:** 585/585 PASS.
- **Esquema y persistencia:** Flyway V1–V11, SQL Server 2022 (Testcontainers) aplicados y validados limpiamente.
- **Seguridad y contratos:** Endpoints de autenticación empresarial y de operador, sesiones JDBC en BD, CSRF, revalidación continua con límite de 8h, onboarding asistido mono-transaccional, emisión de enlace inicial y OpenAPI v0.1.0-i1 verificados sin regresiones.
- **Estado de control de versiones:** Cambios locales íntegros, sin `git add`, `git commit` ni `git push`.

---

## I2 — Gestión de personal y estructura organizativa

### Subincremento completado: I2.1 — Configuración y puestos (solo backend y contrato OpenAPI) (2026-09-16)
- **Alcance backend y API cerrado:**
  - **HU-05 (Configuración de organización y sede única activa):**
    - Consulta general de organización y sede activa (`GET /api/v1/organization/configuration`). `timezoneId` en modo solo lectura para contextualización (mutación diferida hasta I3). Campos internos/restringidos omitidos.
    - Actualización idempotente del nombre visible de organización (`PATCH /api/v1/organization/configuration/display-name`) con control de concurrencia optimista (`expectedLockVersion`).
    - Actualización idempotente del nombre de sede activa (`PATCH /api/v1/organization/configuration/active-site/name`) con control de concurrencia optimista (`expectedLockVersion`).
  - **HU-06 (Catálogo de puestos de trabajo):**
    - Consulta con filtro por estado (`GET /api/v1/positions?active=`).
    - Creación de puesto (`POST /api/v1/positions`) con código inmutable mayúsculo (`^[A-Z0-9-]+$`), activo por defecto y `lockVersion = 0`.
    - Actualización de nombre (`PUT /api/v1/positions/{positionId}`) con control de concurrencia optimista (`expectedLockVersion`).
    - Activación/desactivación de estado (`PATCH /api/v1/positions/{positionId}/status`) con control de concurrencia optimista (`expectedLockVersion`).
  - **Seguridad, transversalidad y persistencia:**
    - Migración `V12__positions.sql` (clave candidata empresarial `(organization_id, id)` y unicidad binaria `(organization_id, code)` BIN2).
    - Aislamiento multi-tenant estricto derivado exclusivamente del `SecurityContext`.
    - RBAC: consultas para `ADMINISTRADOR` y `SUPERVISOR` (403 para `COLABORADOR`); mutaciones exclusivas de `ADMINISTRADOR` (403 para `SUPERVISOR` y `COLABORADOR`).
    - Protección CSRF obligatoria en mutaciones (`X-CSRF-TOKEN`) y `Cache-Control: no-store` en todas las respuestas.
    - Idempotencia empresarial coordinada con `BusinessIdempotentOperationExecutor`: soporte de replays para éxitos (200/201) y rechazos deterministas (404/409) con cabecera `Idempotency-Replayed: true` y `requestId` fresco, detección de claves expiradas (24h) y huella alterada.
    - Problem Details RFC 9457 sin cabecera HTTP `X-Request-ID` y con `requestId` en el cuerpo JSON.
  - **Contrato OpenAPI v0.2.1-i2.1:**
    - 7 rutas actualizadas y componentes reutilizables dedicados `I21ValidationFailed`, `I21SessionRequired`, `I21AccessDeniedOrCsrf` e `I21ServiceUnavailable` sin cabecera `X-Request-ID`.
    - Validado con Redocly CLI (`npx @redocly/cli lint docs/api/openapi.yaml`: 0 errores, 0 advertencias).
- **Pendientes explícitos de I2.1:**
  - Frontend de HU-05 (pantallas de configuración de organización y sede única activa).
  - Frontend de HU-06 (interfaz de catálogo y administración de puestos de trabajo).

## Subincremento completado: I2.2 — Colaboradores y vínculo con cuentas (2026-09-24)

- **I2.2-a — Persistencia de empleados: COMPLETADO.**
  - Migración `V13__employees.sql` creada con tabla `employees`, PK simple `id`, clave candidata empresarial `uq_employees_org_id (organization_id, id)`, unicidad por código `uq_employees_org_code (organization_id, code)` en collation `Latin1_General_100_BIN2`, FK a `organizations(id)`, FKs compuestas a `positions` y `sites`, checks de código y display_name no vacíos, lock_version >= 0, timestamps con `SYSUTCDATETIME()`.
  - Migración `V14__app_users_employee_fk.sql` creada: desmantela el guard temporal `chk_app_users_employee_id_null_guard` e instala la clave foránea compuesta `fk_app_users_employee ((organization_id, employee_id) -> employees(organization_id, id))` con `ON DELETE NO ACTION` y `ON UPDATE NO ACTION`.
  - Pruebas focalizadas de persistencia y upgrades aprobadas (`EmployeePersistenceIT`, `SchemaUpgradeV3ToLatestIT`, `SchemaUpgradeV11ToV12IT`).
- **I2.2-b — Dominio, casos de uso y persistencia JDBC de empleados: COMPLETADO.**
  - Entidad de dominio pura `Employee` con validación y normalización estricta de `code` (<=50) y `displayName` (<=150), y `lockVersion`.
  - Casos de uso de aplicación: `CreateEmployeeUseCase`, `GetEmployeesUseCase`, `GetEmployeeUseCase`, `UpdateEmployeeUseCase`, `ActivateEmployeeUseCase`, `DeactivateEmployeeUseCase` (incluyendo preview de impacto).
  - Integración multi-tenant con `OrganizationConfigurationRepository` (para validar sede activa) y `PositionRepository` (para verificar que el puesto pertenezca a la misma org y esté activo).
  - Concurrencia de licencia serializada vía `sp_getapplock` (`TurnoShift:Employees:Org:{orgId}`, `@LockOwner = 'Transaction'`, `@LockTimeout = 10000`) dentro de la misma transacción y conexión Spring `@Transactional`.
  - Adaptador `JdbcEmployeeRepository` con SQL Server 2022, `OUTPUT inserted.*`, multi-tenancy estricto por `organization_id`, y manejo de violación de unicidad `uq_employees_org_code`.
  - Corrección transaccional: eliminación de cualquier `BEGIN TRANSACTION` manual en el repositorio, exigiendo transacción Spring activa, y timestamps UTC con `SYSUTCDATETIME()`.
  - Núcleo de empleados y corrección transaccional/timestamps aprobados.
- **I2.2-c — API HTTP de empleados: COMPLETADO.**
  - Implementado `EmployeeController` con 7 endpoints bajo `/api/v1/employees` (listado, detalle, creación, actualización parcial, activación, desactivación, previsualización de impacto).
  - Deduplicación idempotente con `BusinessIdempotentOperationExecutor` y `EmployeeIdempotentCommand`, serializando huellas canónicas inequívocas y persistiendo resultados exitosos y rechazos deterministas (`api_operations`).
  - Serialización y deserialización tipada de respuestas rechazadas con `ObjectMapper` inyectado (eliminando strings JSON manuales y `.contains()`).
  - Matriz de autorización por roles en `SecurityConfig`: GET permitido para `ADMINISTRADOR` y `SUPERVISOR`; POST/PATCH reservado para `ADMINISTRADOR`; rechazo 403 `ACCESS_DENIED` para `COLABORADOR` y operadores de plataforma.
  - Protección estricta de sesión (401 `SESSION_REQUIRED`) y CSRF (403 `CSRF_INVALID` en mutaciones).
  - Respuestas Problem Details RFC 9457 con `requestId` en el body y sin cabecera `X-Request-ID`; `Cache-Control: no-store`.
  - Excepciones de persistencia manejadas con 503 `SERVICE_UNAVAILABLE` seguro sin fugas de SQL.
  - `EmployeeControllerIT` previamente aprobado (12/12 PASS).
  - `EmployeeIdempotentCommandTest` + `EmployeeControllerTest`: 50/50 PASS (5/5 en `EmployeeIdempotentCommandTest`, 45/45 en `EmployeeControllerTest`).
- **I2.2-d — Contrato OpenAPI de empleados: COMPLETADO.**
  - `docs/api/openapi.yaml` actualizado a versión `0.2.2-i2.2` con título `TurnoShift API — Contrato MVP (I1 + I2.1 + I2.2)`.
  - 7 endpoints de colaboradores documentados bajo `/api/v1` con tag `employees`.
  - Seguridad AND de cookie de sesión + header CSRF expresada en el mismo objeto `security` (`sessionCookie: []` y `csrfHeader: []`).
  - `Idempotency-Key` declarado como parámetro de header obligatorio en mutaciones, y omitido en `deactivation-preview` (operación evaluativa sin modificación de estado).
  - Esquemas requeridos implementados: `EmployeeResponse` (sin datos de cuenta ni linkedUserId), `CreateEmployeeRequest`, `UpdateEmployeeRequest`, `ChangeEmployeeStatusRequest`, `DeactivationPreviewResponse`.
  - Componentes de error reutilizables I22 (`I22ValidationFailed`, `I22SessionRequired`, `I22AccessDeniedOrCsrf`, `I22ServiceUnavailable`, `EmployeeNotFound`, `EmployeeCreationConflict`, `EmployeeMutationConflict`, `EmployeeActivateConflict`, `EmployeeDeactivateConflict`) cubriendo todos los códigos de error requeridos.
  - Validación con Redocly CLI (`npx @redocly/cli lint docs/api/openapi.yaml`): 0 errores y 0 advertencias.

## Subincremento completado: I2.3 — Política operativa inicial (2026-09-25)

- **I2.3-a — Persistencia de políticas: COMPLETADO.**
  - Migración `V15__planning_policy_versions.sql` creada con tabla `planning_policy_versions`, PK técnica simple `id`, modelo estrictamente append-only, clave candidata empresarial `uq_planning_policy_versions_org_id (organization_id, id)`, secuencia incremental `version_number INT NOT NULL CHECK (version_number > 0)`, unicidad `uq_planning_policy_versions_org_version (organization_id, version_number)`, FK a `organizations(id)`, checks de umbrales `minimum_rest_minutes >= 1 AND minimum_rest_minutes <= 1440`, `weekly_warning_minutes >= 1 AND weekly_warning_minutes <= 10080`, motivo no vacío `LEN(LTRIM(RTRIM(change_reason))) > 0` (hasta 500 caracteres), y timestamps UTC con `SYSUTCDATETIME()`.
  - Migración `V16__organizations_policy_fk.sql` creada: desmantela la guarda temporal V3 `chk_organizations_current_policy_version_id_null_guard` e instala la clave foránea empresarial compuesta `fk_organizations_current_policy_version ((id, current_policy_version_id) -> planning_policy_versions(organization_id, id))` con `ON DELETE NO ACTION` y `ON UPDATE NO ACTION`.
  - Pruebas focalizadas de persistencia y upgrades verificadas.
- **I2.3-b — Dominio, casos de uso y persistencia JDBC de políticas: COMPLETADO.**
  - Value objects de dominio puros: `PolicyThresholds` (descanso 1..1440 min, aviso 1..10080 min), `PolicyReason` (no nulo, 1..500 caracteres, rechazo estricto de solo espacios Unicode), `PolicyDelta` y `ValidationFingerprint` (huella canónica SHA-256 en formato hexadecimal minúsculo estricto `^[0-9a-f]{64}$`).
  - Entidades de dominio: `NewPolicyVersion` y `PolicyVersion` inmutables.
  - Casos de uso de aplicación: `GetCurrentPolicyUseCase` (retorna versión actual o empty), `GetPolicyHistoryUseCase` (orden descendente por versionNumber), `PreviewPolicyChangeUseCase` (cálculo determinista de delta, fingerprint y requirements sin persistencia) y `ActivatePolicyUseCase` (activación transaccional).
  - Persistencia JDBC (`JdbcPolicyRepository`): inserción append-only, obtención de versión actual, historial ordenado, avance atómico del puntero `organizations.current_policy_version_id` con incremento de `organizations.lock_version`, y adquisición de bloqueo pesimista transaccional exclusivo por organización mediante `sp_getapplock` (`TurnoShift:Policy:Org:{orgId}`, `@LockOwner = 'Transaction'`, `@LockTimeout = 10000`) dentro de transacción Spring activa.
  - Evidencia: 58 pruebas unitarias y 10 IT JDBC aprobadas.
- **I2.3-c — API HTTP de políticas operativas: COMPLETADO.**
  - **I2.3-c1 (Lectura y previsualización):**
    - `GET /api/v1/policies/current`: roles `ADMINISTRADOR` y `SUPERVISOR`, respuesta 200 `{"policy": null}` si no hay versión previa o representación completa, `Cache-Control: no-store`.
    - `GET /api/v1/policies/history`: roles `ADMINISTRADOR` y `SUPERVISOR`, respuesta 200 `{"items": [...]}` ordenada deterministamente por `versionNumber DESC`.
    - `POST /api/v1/policies/preview`: rol exclusivo `ADMINISTRADOR`, requiere `X-CSRF-TOKEN`, validación obligatoria de `expectedCurrentPolicyVersionId` (nullable explícito; 400 si falta), rechazo con 409 `POLICY_CONCURRENT_MODIFICATION` si la versión actual difiere, cálculo de `validationFingerprint`, delta y requisitos sin persistencia.
    - Evidencia I2.3-c1: 21 pruebas HTTP + 11 pruebas de casos de uso aprobadas.
  - **I2.3-c2 (Activación idempotente):**
    - `POST /api/v1/policies/activate`: rol exclusivo `ADMINISTRADOR`, requiere `X-CSRF-TOKEN` y cabecera obligatoria `Idempotency-Key` (UUID).
    - Deduplicación determinista con `BusinessIdempotentOperationExecutor` y `PolicyIdempotentCommand` (registro en `api_operations`).
    - Validación de `validationFingerprint` (`^[0-9a-f]{64}$`), `reason` (1..500 chars) y `expectedCurrentPolicyVersionId`.
    - Ejecución mono-transaccional delegando a `ActivatePolicyUseCase` bajo bloqueo `sp_getapplock`. Si el bloqueo no se obtiene de inmediato, responde 503 `POLICY_OPERATION_BUSY` con cabecera `Retry-After: 1`.
    - Respuesta 201 Created con la nueva versión de política; replays devuelven la respuesta original acompañada de `Idempotency-Replayed: true`.
    - Evidencia I2.3-c2: 47 pruebas focalizadas aprobadas.
- **I2.3-d — Contrato OpenAPI de políticas: COMPLETADO.**
  - `docs/api/openapi.yaml` y `docs/api/README.md` actualizados a versión `0.2.3-i2.3` con tag `policies`.
  - Documentadas las 4 rutas: `GET /policies/current`, `GET /policies/history`, `POST /policies/preview`, `POST /policies/activate`.
  - Seguridad declarada: `sessionCookie` en GET; AND (`sessionCookie` + `csrfHeader`) en POST.
  - `Idempotency-Key` declarado como parámetro obligatorio en `activate`. Cabecera `Idempotency-Replayed: true` documentada para replay sin indicar que viaje en la primera respuesta.
  - DTOs reales modelados: `PolicyResponse`, `CurrentPolicyResponse` (con policy nullable), `PolicyHistoryResponse`, `ProposedValuesDto`, `PolicyDeltaDto`, `PolicyRequirementsDto`, `PolicyPreviewRequest`, `PolicyPreviewResponse`, `ActivatePolicyRequest`.
  - Respuestas de error RFC 9457: 400 (`VALIDATION_FAILED`, `REASON_REQUIRED`, `INVALID_VALIDATION_FINGERPRINT`), 401 (`SESSION_REQUIRED`), 403 (`ACCESS_DENIED`, `CSRF_INVALID`), 409 (`POLICY_CONCURRENT_MODIFICATION`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_KEY_EXPIRED`), 503 (`POLICY_OPERATION_BUSY` con `Retry-After: 1`, `SERVICE_UNAVAILABLE`).
  - Validación con Redocly CLI (`npx @redocly/cli lint docs/api/openapi.yaml`): 0 errores y 0 advertencias.

## Subincremento completado: I2.4 — Indisponibilidades (HU-08) (2026-09-25)

- **I2.4-a — Persistencia de indisponibilidades: COMPLETADO.**
  - Migración `V17__employee_unavailabilities.sql` creada con tabla `employee_unavailabilities`, PK técnica simple `id UNIQUEIDENTIFIER`, clave candidata empresarial `uq_employee_unavailabilities_org_id (organization_id, id)`, clave foránea empresarial compuesta `fk_employee_unavailabilities_org_employee ((organization_id, employee_id) -> employees(organization_id, id))` con `ON DELETE NO ACTION` y `ON UPDATE NO ACTION`.
  - Ciclo de vida con estados válidos `ACTIVA` y `CANCELADA` (`chk_employee_unavailabilities_status`), intervalo semiabierto `[starts_at, ends_at)` con restricción `chk_employee_unavailabilities_interval (starts_at < ends_at)`.
  - Motivo tipado `reason_code` (`chk_employee_unavailabilities_reason_code`: `VACACIONES`, `ENFERMEDAD`, `PERMISO_RETRIBUIDO`, `PERMISO_NO_RETRIBUIDO`, `FORMACION`, `OTRO`), nota restringida opcional `restricted_note NVARCHAR(500)`.
  - Campos de auditoría de cancelación: `cancelled_at DATETIMEOFFSET NULL`, `cancelled_by_user_id UNIQUEIDENTIFIER NULL`, `cancellation_reason NVARCHAR(500) NULL` y coherencia de cancelación `chk_employee_unavailabilities_cancellation_coherence`.
  - Control de concurrencia optimista `lock_version BIGINT NOT NULL DEFAULT 0` (`chk_employee_unavailabilities_lock_version`), timestamps UTC automáticos con `SYSUTCDATETIME()`.
  - Índices filtrados: `ix_employee_unavailabilities_active_lookup` sobre `(organization_id, employee_id, starts_at, ends_at) WHERE status = 'ACTIVA'` e `ix_employee_unavailabilities_org_window` sobre `(organization_id, starts_at, ends_at) WHERE status = 'ACTIVA'`.
  - Pruebas focalizadas de persistencia y upgrades verificadas (`UnavailabilityPersistenceIT`, `SchemaUpgradeV3ToLatestIT`, `SchemaUpgradeV11ToV12IT`).
- **I2.4-b — Dominio, casos de uso y persistencia JDBC de indisponibilidades: COMPLETADO.**
  - Entidades y value objects de dominio puros: `EmployeeUnavailability`, `UnavailabilityStatus`, `UnavailabilityReasonCode`, `UnavailabilityInterval` (intervalo semiabierto `[startsAt, endsAt)` con `startsAt.isBefore(endsAt)`), `RestrictedNote` (hasta 500 unidades UTF-16, rechazo estricto de solo espacios en blanco Unicode).
  - Casos de uso de aplicación: `CreateUnavailabilityUseCase`, `GetUnavailabilityUseCase`, `ListEmployeeUnavailabilitiesUseCase`, `ListOrganizationUnavailabilitiesUseCase`, `UpdateUnavailabilityUseCase` y `CancelUnavailabilityUseCase`.
  - Adaptador `JdbcEmployeeUnavailabilityRepository` sobre SQL Server 2022 con consultas parametrizadas, aislamiento multi-tenant estricto por `organization_id`, y actualización condicional atómica. Timestamps UTC gestionados con `SYSUTCDATETIME()`.
  - Protección contra carreras en base de datos: las sentencias `UPDATE` para actualizar o cancelar incluyen la cláusula de guardia `AND starts_at > SYSUTCDATETIME()`, garantizando que la regla de indisponibilidad pasada no pueda vulnerarse por desfases o carreras de tiempo entre la aplicación y el motor de BD.
  - Regla de negocio RN-013: actualización y cancelación rechazadas con 400 `PAST_INTERVAL_MODIFICATION_PROHIBITED` si el intervalo existente ya inició (`startsAt <= now`); la actualización tampoco permite que el nuevo intervalo inicie en el pasado (`newStartsAt <= now`). La creación admite indisponibilidades en curso (`startsAt <= now`) siempre que su finalización sea futura (`endsAt > now`), y rechaza intervalos ya concluidos (`endsAt <= now`).
  - Pruebas unitarias de dominio/casos de uso y pruebas de integración `JdbcEmployeeUnavailabilityRepositoryIT` (14/14 PASS) aprobadas.
- **I2.4-c — API HTTP e idempotencia de indisponibilidades: COMPLETADO.**
  - Implementado `UnavailabilityController` con 6 endpoints bajo `/api/v1`:
    - `GET /api/v1/employees/{employeeId}/unavailabilities`: listado por colaborador, con parámetro opcional `includeCancelled` estrictamente booleano (`true`/`false`), ordenado deterministamente por `startsAt ASC`.
    - `GET /api/v1/unavailabilities`: listado organizacional por ventana temporal con parámetros obligatorios `from` y `to` validados como `Instant` con `from < to`, parámetro `limit` (1..200, default 50) y cursor opcional `cursorCreatedAt` para paginación estable por `(startsAt ASC, createdAt ASC, id ASC)`.
    - `GET /api/v1/unavailabilities/{unavailabilityId}`: consulta de detalle por ID con aislamiento de organización.
    - `POST /api/v1/employees/{employeeId}/unavailabilities`: creación idempotente (201 Created con cabecera `Location: /api/v1/unavailabilities/{id}`).
    - `PUT /api/v1/unavailabilities/{unavailabilityId}`: modificación idempotente (200 OK) con `expectedLockVersion`.
    - `POST /api/v1/unavailabilities/{unavailabilityId}/cancel`: cancelación idempotente (200 OK) con motivo obligatorio `cancellationReason` y `expectedLockVersion`.
  - Soporte dual de formato temporal (parcial UTC vs. día completo organizacional):
    - Parcial: `startsAt` y `endsAt` como `Instant` UTC (`startsAt < endsAt`).
    - Día completo: `allDay = true`, `startDate` (inclusiva) y `endDate` (exclusiva) en formato `YYYY-MM-DD`. Ambos formatos son mutuamente excluyentes.
    - Conversión con `OrganizationTimezonePort`, `OrganizationTimezoneAdapter` y `UnavailabilityTemporalParser`, resolviendo `organizations.timezone_id` de la sesión mediante `LocalDate.atStartOfDay(zoneId).toInstant()`.
  - Deduplicación idempotente con `BusinessIdempotentOperationExecutor` y `UnavailabilityIdempotentCommand`: cabecera obligatoria `Idempotency-Key` (UUID), registro atómico en `api_operations`, huella canónica inequívoca (`method:path:canonicalJson`), y replay determinista con cabecera `Idempotency-Replayed: true` para éxitos (200/201) y rechazos persistidos (404/409).
  - Matriz de autorización por roles en `SecurityConfig`: consultas y mutaciones permitidas para `ADMINISTRADOR` y `SUPERVISOR`; rechazo 403 `ACCESS_DENIED` para `COLABORADOR` y operadores de plataforma; 401 `SESSION_REQUIRED` sin sesión activa; CSRF obligatorio (`403 CSRF_INVALID`) en mutaciones.
  - Control de concurrencia optimista y manejo de errores RFC 9457 Problem Details: 409 `CONCURRENT_MODIFICATION` ante conflicto de versión, 409 `UNAVAILABILITY_ALREADY_CANCELLED` ante intento de cancelar una ya cancelada, 400 `PAST_INTERVAL_MODIFICATION_PROHIBITED` ante violación de RN-013, 400 `OVERLAPPING_UNAVAILABILITY` ante solapamiento de intervalos activos del mismo colaborador, y 503 `SERVICE_UNAVAILABLE` ante fallos de persistencia sin fugas de datos internos. Respuestas con `requestId` en el body y sin cabecera `X-Request-ID`.
  - Evidencia de pruebas: suite focalizada de 63/63 PASS (`UnavailabilityControllerIT` 14/14, `UnavailabilityControllerTest` 21/21, `UnavailabilityTemporalParserTest` 5/5, `OrganizationTimezoneAdapterTest` 5/5, `UnavailabilityIdempotentCommandTest` 4/4, `JdbcEmployeeUnavailabilityRepositoryIT` 14/14).
- **I2.4-d — Contrato OpenAPI de indisponibilidades: COMPLETADO.**
  - `docs/api/openapi.yaml` y `docs/api/README.md` actualizados a versión `0.2.4-i2.4` con tag `unavailabilities`.
  - 6 rutas documentadas con esquemas de request/response y seguridad AND (`sessionCookie` + `csrfHeader` en mutaciones; `sessionCookie` en lecturas).
  - Cabeceras `Idempotency-Key` (en requests de mutación), `Location` (en creación 201) e `Idempotency-Replayed: true` (en replays) documentadas.
  - Componentes de error reutilizables I24 RFC 9457 modelados con precisión según comportamiento real: `I24ValidationFailed`, `I24SessionRequired`, `I24AccessDeniedOrCsrf`, `I24ServiceUnavailable`, `UnavailabilityNotFound`, `UnavailabilityMutationConflict`, `UnavailabilityPastIntervalProhibited`.
  - Validación contractual con Redocly CLI (`npx @redocly/cli lint docs/api/openapi.yaml`): 0 errores y 0 advertencias.

## Subincremento completado: Frontend F1 — Autenticación y Layout (2026-09-25)

- **F1.1 — Proxy de desarrollo y tipos base/API: COMPLETADO.**
  - Proxy Vite configurado en `frontend/vite.config.ts`: redirige peticiones `/api` al backend local (`http://localhost:8081`) durante el desarrollo (`vite dev`) sin alterar el build de producción.
  - Tipos base en `frontend/src/types/api.ts`: esquemas tipados para Problem Details RFC 9457 (`ProblemDetails`, `FieldError`), clase de error `ApiProblemError` con soporte para fallos de red seguros (`isNetworkError`, status 0) sin filtrar URLs ni detalles técnicos internos.
  - Tipos de autenticación en `frontend/src/types/auth.ts`: esquemas de sesión (`SessionResponse`, `SessionUser`, `SessionOrganization`, `UserRole`) y petición de login (`LoginRequest` con soporte dual para `organizationCode` y `orgCode`).
  - Cliente HTTP centralizado en `frontend/src/api/client.ts`: envoltorio sobre `fetch` nativo con `credentials: 'include'` (envío de cookie `TSESSIONID`), cabeceras `Accept: application/json, application/problem+json`, inyección de cabecera `X-CSRF-TOKEN` solo en memoria volátil para métodos de mutación (`POST`, `PUT`, `PATCH`, `DELETE`), y mapeo exhaustivo a `ApiProblemError`.
- **F1.2 — Servicio de autenticación y AuthContext: COMPLETADO.**
  - Módulo de API en `frontend/src/api/authApi.ts`: funciones tipadas consumiendo `apiClient` (`getCsrfToken`, `getSessionState`, `login`, `logout`), normalizando el payload de login al formato requerido por el backend (`organizationCode`).
  - Contexto de autenticación en `frontend/src/context/AuthContext.tsx`:
    - Proveedor `AuthProvider` y hook `useAuth()`.
    - Bootstrap inicial automático: obtiene token CSRF inicial para sesión anónima y consulta el estado de sesión activa (`GET /api/v1/auth/session`). Si responde 401, pasa limpiamente al estado no autenticado.
    - Login empresarial: solicita token CSRF previo si no existe, ejecuta `POST /api/v1/auth/login`, refresca el token CSRF tras la rotación de sesión en el servidor (`changeSessionId`), sincroniza el estado autenticado y limpia banderas de expiración.
    - Logout seguro: ejecuta `POST /api/v1/auth/logout`, limpia el estado de sesión local y token CSRF tras éxito o respuesta 401; ante fallos de red u otros errores no-401 conserva el estado de sesión local y permite el reintento.
    - Invariante de seguridad: ningún token, credencial ni dato de sesión se persiste en `localStorage` o `sessionStorage`.
- **F1.3 — Enrutador SPA nativo, layout base y navegación visual: COMPLETADO.**
  - Enrutador nativo en `frontend/src/components/routing/Router.tsx`: contexto `RouterContext`, hook `useRouter()` y componente `<Link to="...">` basados en la Web History API (`window.location.pathname`, `pushState`, `replaceState`, `popstate`), sin librerías externas.
  - Colección de iconos SVG vectoriales y accesibles en `frontend/src/components/common/Icons.tsx`.
  - Layout empresarial inspirado en Stitch:
    - `AppHeader`: barra superior persistente con breadcrumb (`TurnoShift / Operaciones / [Sección]`), pastilla con la organización (`displayName`) y su zona horaria oficial (`timeZone`), indicador de rol (`Administrador`, `Supervisor`, `Colaborador`) y botón para menú móvil.
    - `AppSidebar`: barra lateral clara (240px) con el glifo de marca `TS` en ámbar corporativo (`var(--accent)` `#b45309`), sección `OPERACIONES`, navegación condicionada estrictamente por rol con pastilla activa en ámbar sólido y distintivo "Consulta" para rol Supervisor en equipo; bloque inferior con organización, avatar de iniciales, rol y botón de cierre de sesión seguro.
    - `AppLayout`: contenedor responsivo con drawer lateral colapsable y backdrop oscurecido para pantallas móviles (`< 768px`).
  - Páginas y vistas operativas:
    - `HomePage` (`/app/inicio`): saludo contextualizado, franja de datos reales de sesión y tarjetas de acceso a módulos según rol. Cero datos fabricados (sin métricas inventadas, sin falsos ERP ni biometría).
    - `ForbiddenPage` (`/acceso-restringido`): pantalla sobria de acceso denegado (UI-04) con desglose del usuario/rol y acciones para volver o cerrar sesión.
    - `PlaceholderPage`: tarjetas honestas de "Módulo en preparación" para `/app/configuracion` (F2), `/app/equipo` (F3) y `/app/mi-horario`, explicando el alcance planificado sin tablas ni formularios simulados.
  - Enrutamiento centralizado en `frontend/src/App.tsx`: redirección automática de usuarios no autenticados hacia `/acceso` (con alias `/login` redirigiendo a `/acceso`), ruta pública `/setup` y protección con guardias de rol.
- **F1.4 — Login real, seguro y accesible: COMPLETADO.**
  - Integración en `frontend/src/pages/login/LoginPage.tsx` con `useAuth().login()` y `useRouter()`. Ruta oficial de interfaz: `/acceso`.
  - Campos reales del formulario: código de organización (`organizationCode`), correo corporativo (`email`) y contraseña (`password`).
  - Diseño alineado con la referencia visual de Stitch (`acceso_turnoshift/screen.png`): tarjeta blanca centrada, fondo suave `var(--stone-50)`, glifo `TS` ámbar y foco visible (`--focus-ring`).
  - Accesibilidad total: etiquetas asociadas con `htmlFor`, autocompletado estándar (`organization`, `email`, `current-password`), foco automático en el primer campo, y botón conmutador de visibilidad de contraseña accesible por teclado (sin `tabIndex={-1}`, con foco nativo y `aria-label` dinámico).
  - Manejo seguro de errores HTTP y RFC 9457:
    - 401 (`AUTHENTICATION_FAILED`): mensaje genérico indistinguible sin enumeración de usuario ni organización.
    - 429 (`RATE_LIMITED`): aviso sobrio de límite de intentos sin inventar tiempos no expuestos.
    - 503 (`SERVICE_UNAVAILABLE`) y error de red (status 0 / `isNetworkError`): mensaje genérico de servicio no disponible que permite reintento.
    - 400 (`VALIDATION_FAILED`): errores contextuales mapeados únicamente a los campos reconocidos (`organizationCode`, `email`, `password`) con `aria-invalid` y `aria-describedby`.
    - Sesión expirada (`sessionExpired`): aviso amigable superior en `/acceso` que se limpia automáticamente al interactuar con el formulario.
  - Exclusiones estrictas del diseño Stitch aplicadas: sin checkbox de recordar equipo/dispositivo, sin SSO, sin versiones técnicas ni certificaciones TLS, sin enlaces ficticios a funcionalidades no implementadas, y sin simulador interactivo de estados.
- **F1.5 — Establecimiento de contraseña inicial desde enlace seguro: COMPLETADO.**
  - Ruta pública `/setup` y componente `SetupPage` con estilos dedicados (`SetupPage.css`) coherentes con `LoginPage`.
  - Extracción segura del token exclusivamente desde `window.location.hash` (`#token=...`); sin query params, sin persistencia en almacenamiento local ni exposición en mensajes, título o registros. Si no hay token o es inválido, muestra estado seguro de enlace no disponible sin contactar al servidor.
  - Consumo del endpoint real `POST /api/v1/auth/password-reset/complete` vía `authApi.completePasswordSetup` con inyección de CSRF en memoria (solicitado automáticamente si estuviera ausente).
  - Validación local mínima: campos requeridos y coincidencia de confirmación; validación de reglas de contraseña (mínimo 15 caracteres, fail-closed con HIBP) delegada al backend.
  - Manejo seguro de Problem Details: 422 (`RESET_LINK_INVALID`) muestra pantalla segura de enlace no disponible sin revelar estado interno; 400 mapea errores contextuales a `newPassword`; 429, 503 y de red con mensajes reintentables.
  - Éxito: eliminación inmediata del hash con `history.replaceState`, confirmación visual y redirección controlada a `/acceso` sin iniciar sesión automática.
- **Evidencia de compilación:**
  - `npm run build` en `frontend/`: BUILD SUCCESS (0 errores TypeScript, 0 advertencias Vite).
  - *Nota de verificación:* La compilación y empaquetado de producción de toda la aplicación frontend fueron verificados satisfactoriamente. La prueba manual end-to-end interactiva contra el backend local queda reservada para la fase de preparación de demo.

---

## Subincremento completado: Frontend F2 — Configuración organizacional y puestos (2026-09-25)

- **F2.1 — Tipos TypeScript, generador de idempotencia y clientes de API: COMPLETADO.**
  - Tipos de contrato en `frontend/src/types/organization.ts`: `OrganizationView`, `ActiveSiteView`, `OrganizationConfigurationResponse`, `UpdateOrganizationDisplayNameRequest` y `UpdateActiveSiteNameRequest`.
  - Tipos de catálogo en `frontend/src/types/position.ts`: `PositionResponse`, `CreatePositionRequest`, `UpdatePositionRequest`, `ChangePositionStatusRequest` y `PositionStatusFilter` (`'all' | 'active' | 'inactive'`).
  - Utilidad criptográfica en `frontend/src/utils/idempotency.ts`: `generateIdempotencyKey()` implementada con Web Crypto nativo (`crypto.randomUUID()`) sin dependencias de paquetes externos.
  - Módulos de API:
    - `frontend/src/api/organizationApi.ts`: `getConfiguration`, `updateDisplayName`, `updateActiveSiteName`.
    - `frontend/src/api/positionApi.ts`: `getPositions`, `createPosition`, `updatePosition`, `changePositionStatus`.
  - Reutilización de la infraestructura HTTP centralizada (`apiClient`): envío de cookies HttpOnly (`credentials: 'include'`), inyección de cabecera `X-CSRF-TOKEN` solo en memoria para métodos de mutación e integración con Problem Details RFC 9457 (`ApiProblemError`).

- **F2.2 — Pantalla de configuración organizacional: COMPLETADO.**
  - Página `/app/configuracion` (`frontend/src/pages/configuration/ConfigurationPage.tsx` y `.css`):
    - Carga de datos reales mediante `GET /api/v1/organization/configuration`.
    - Consulta de nombre visible de organización, código inmutable y nombre de la sede activa.
    - Exposición de la zona horaria institucional (`timezoneId`) estrictamente en modo de solo lectura.
    - Edición idempotente e independiente de los nombres de la organización y de la sede activa con control de concurrencia optimista (`expectedLockVersion`).
    - Protección RBAC: acceso exclusivo de edición para `ADMINISTRADOR`; `SUPERVISOR` y `COLABORADOR` redirigidos a `/acceso-restringido`.
    - Manejo de reintentos y errores: retención de `Idempotency-Key` ante fallo de red (`isNetworkError`) permitiendo reintento idéntico seguro; banner contextual y botón de recarga ante colisión concurrente (409 `CONCURRENT_MODIFICATION`).
    - Eliminación de falsedades del diseño Stitch: sin áreas organizativas inexistentes, reglas simuladas de descanso, certificaciones ISO ni sincronización ficticia con ERP/relojes biométricos.

- **F2.3 — Catálogo de puestos y mutaciones seguras: COMPLETADO.**
  - Ruta `/app/puestos` integrada en enrutador SPA nativo (`frontend/src/App.tsx`), navegación (`AppSidebar` con distintivo "Consulta" para supervisor, `AppHeader`) y accesos en `HomePage`.
  - Matriz de permisos RBAC:
    - `ADMINISTRADOR`: consulta y mutaciones completas (alta, edición, activación/desactivación).
    - `SUPERVISOR`: consulta del catálogo con banner explicativo "Modo consulta" y controles de mutación completamente omitidos del DOM.
    - `COLABORADOR`: acceso restringido (`/acceso-restringido`).
  - Vista del catálogo (`frontend/src/pages/positions/PositionsPage.tsx` y `.css`): filtros por estado (`Todos`, `Activos`, `Inactivos`), búsqueda reactiva por código o nombre, estados visuales de carga, catálogo vacío general, vacío por búsqueda y error con reintento (`handleReload`).
  - Diálogos modales accesibles:
    - `PositionModal`: alta de puestos con validación de código inmutable (`^[A-Z0-9-]+$`, 2..32 caracteres) y nombre (1..100 caracteres); edición con código en solo lectura y nombre editable; captura de 409 `POSITION_CODE_ALREADY_EXISTS`.
    - `PositionStatusModal`: diálogo de confirmación para activación/desactivación con texto neutral y verificable ("El puesto quedará marcado como activo/inactivo"), sin afirmar consecuencias operativas no garantizadas por el backend (sin asegurar bloqueo de contrataciones o asignaciones futuras).
  - Manejo de idempotencia y control de concurrencia (`PendingOperationIntent`):
    - Unión discriminada que incluye `operation`, `positionId` (en edit y status), payload semántico (`code`, `name`, `targetStatus`) y `expectedLockVersion` (en edit y status).
    - Reutilización de la clave únicamente si la operación, el puesto, los datos y la versión coinciden exactamente; generación de nueva clave si cambia cualquier dato o se inicia otra operación.
    - Retención de clave únicamente ante error de red (`err.isNetworkError`) y limpieza (`pendingIntentRef.current = null`) tras éxito, error devuelto por el servidor o recarga manual.

- **Evidencia de compilación:**
  - `npm run build` en `frontend/`: BUILD SUCCESS (0 errores TypeScript, 0 advertencias Vite, última ejecución: 1.26 s).
  - *Nota de verificación:* La compilación y empaquetado de producción de toda la aplicación frontend fueron verificados satisfactoriamente. La prueba manual end-to-end interactiva contra el backend local queda reservada para la fase de preparación de demo.

---

## Subincremento completado: Frontend F3 — Directorio y gestión de colaboradores (2026-09-25)

- **F3.1 — Tipos TypeScript y capa de API de colaboradores: COMPLETADO.**
  - Tipos de contrato en `frontend/src/types/employee.ts`:
    - `EmployeeResponse` (`id`, `code`, `displayName`, `positionId`, `siteId`, `active`, `lockVersion`).
    - `CreateEmployeeRequest` (`code`, `displayName`, `positionId`).
    - `UpdateEmployeeRequest` (`displayName?`, `positionId?`, `expectedLockVersion`).
    - `ChangeEmployeeStatusRequest` (`expectedLockVersion`).
    - `DeactivationPreviewResponse` (`employeeId`, `displayName`, `canDeactivate`, `blockers`).
    - `EmployeeStatusFilter` (`'all' | 'active' | 'inactive'`).
    - `EmployeeConflictCode`: unión con códigos RFC 9457 reales (`EMPLOYEE_CODE_ALREADY_EXISTS`, `POSITION_NOT_FOUND`, `POSITION_INACTIVE`, `NO_ACTIVE_SITE`, `EMPLOYEE_LICENSE_LIMIT_REACHED`, `CONCURRENT_MODIFICATION`, `EMPLOYEE_NOT_FOUND`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_KEY_EXPIRED`).
    - `PendingEmployeeIntent`: unión discriminada para `create`, `edit`, `activate` y `deactivate`.
    - Poda contractual: sin campos inexistentes (sin correos electrónicos de cuenta, sin fotos de perfil, sin teléfonos ni linkedUserId).
  - Cliente HTTP en `frontend/src/api/employeeApi.ts`: siete operaciones tipadas con `apiClient` (`getEmployees`, `getEmployeeById`, `createEmployee`, `updateEmployee`, `activateEmployee`, `deactivateEmployee`, `previewDeactivation`).
  - Mutaciones protegidas con inyección de cabecera `Idempotency-Key` (UUID v4) y CSRF; `previewDeactivation` como consulta POST sin cuerpo ni clave de idempotencia.

- **F3.2 — Directorio de colaboradores, filtros y rutas: COMPLETADO.**
  - Ruta `/app/equipo` integrada en enrutador SPA nativo (`frontend/src/App.tsx`) con RBAC: `ADMINISTRADOR` y `SUPERVISOR` acceden a la vista; `COLABORADOR` redirigido a `/acceso-restringido`.
  - Carga coordinada en paralelo mediante `Promise.all`: colaboradores con filtro de estado (`employeeApi.getEmployees`), catálogo de puestos (`positionApi.getPositions`) y configuración con sede activa (`organizationApi.getConfiguration`). Si alguna fuente esencial falla, se captura de forma segura en `loadError` y se muestra estado de error con botón de reintento, sin renderizar datos parciales o inventados.
  - Resolución contextual en memoria: mapeo de `positionId` al nombre del puesto y `siteId` al nombre de la sede activa.
  - Filtros y búsqueda reactiva: filtro por estado (`Todos`, `Activos`, `Inactivos`), selector dropdown de puestos con puestos activos e inactivos asignados, y búsqueda local en memoria por `code` o `displayName`.
  - Estados visuales completos: carga (`employees-spinner-large`), error estructurado (`ApiProblemError`), directorio vacío general y búsqueda sin coincidencias con botón para limpiar filtros.
  - Supervisor en modo consulta estricto: renderizado de banner informativo de supervisión sin ningún control de mutación en el DOM.
  - Actualización de accesos y tarjeta de Equipo en `HomePage.tsx`.

- **F3.3 — Modales y mutaciones seguras de colaboradores: COMPLETADO.**
  - Matriz de permisos RBAC: controles de mutación exclusivos para `ADMINISTRADOR` (botón "Nuevo colaborador" en cabecera y estado vacío, y columna de acciones con botones "Editar" y "Activar/Desactivar"). Controles completamente omitidos del DOM para `SUPERVISOR`.
  - Diálogos modales accesibles:
    - `EmployeeModal`: alta con validación de código inmutable (`^[A-Z0-9-]+$`, 2..32 caracteres, sin conversión silenciosa a mayúsculas), nombre visible (1..100 caracteres) y puesto (solo activos); edición con código bloqueado en solo lectura, nombre editable y puesto (si el actual es inactivo se muestra como valor legible pero no se reenvía si no cambia; al cambiar solo admite puestos activos; no permite guardar sin cambios). Foco automático inicial, cierre con Escape y backdrop, y semántica ARIA.
    - `EmployeeStatusModal`: activación con confirmación directa enviando `expectedLockVersion`; desactivación en dos pasos mediante pre-evaluación automática con `previewDeactivation`. Si `canDeactivate` es falso, muestra los `blockers` reales devueltos por el servidor y deshabilita la confirmación; si es verdadero, habilita la confirmación neutral. Explicaciones sobrias sin consecuencias inventadas.
  - Control de idempotencia y concurrencia estricto (`PendingEmployeeIntent`): unión discriminada que incluye `operation`, `employeeId` (en edit, activate y deactivate), payload semántico (`code`, `displayName`, `positionId`) y `expectedLockVersion` (en edit, activate y deactivate). Reutilización de `Idempotency-Key` exclusivamente ante error de red (`err.isNetworkError`) si todos los parámetros e intención son idénticos; generación de nueva clave ante cambios de formulario o nueva operación; limpieza de la intención tras éxito, error devuelto por servidor o recarga manual.
  - Actualización local de la lista únicamente tras confirmación de respuesta `EmployeeResponse` del servidor; ante conflicto 409 `CONCURRENT_MODIFICATION` no se sobrescribe el estado y se despliega banner con botón "Recargar directorio"; captura y mapeo de errores RFC 9457 reales (`EMPLOYEE_CODE_ALREADY_EXISTS`, `POSITION_INACTIVE`, `POSITION_NOT_FOUND`, `NO_ACTIVE_SITE`, `EMPLOYEE_LICENSE_LIMIT_REACHED`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_KEY_EXPIRED`).

- **Evidencia de compilación:**
  - `npm run build` en `frontend/`: BUILD SUCCESS (0 errores TypeScript, 0 advertencias Vite, última ejecución: 1.30 s, 64 módulos transformados).
  - *Nota de verificación:* La compilación y empaquetado de producción de toda la aplicación frontend fueron verificados satisfactoriamente. La prueba manual end-to-end interactiva contra el backend local queda reservada para la fase de preparación de demo.

---

## Subincremento completado: Frontend F4 — Indisponibilidades de colaboradores (HU-08 / HU-10) (2026-09-25)

- **F4.1 — Tipos TypeScript y capa de API de indisponibilidades: COMPLETADO.**
  - Tipos de contrato en `frontend/src/types/unavailability.ts`:
    - `UnavailabilityReason` (`'VACACIONES' | 'PERMISO_PERSONAL' | 'MEDICO' | 'FORMACION' | 'OTRO'`).
    - `EmployeeUnavailabilityResponse` (`id`, `employeeId`, `startsAt`, `endsAt`, `reasonCode`, `restrictedNote`, `cancelled`, `cancelledAt`, `cancelledByUserId`, `lockVersion`, `createdAt`, `updatedAt`).
    - `CreateUnavailabilityRequest`: tipos discriminados para intervalo parcial UTC (`startsAt`, `endsAt`) y día completo (`allDay: true`, `startDate`, `endDate`), con `reasonCode?` y `restrictedNote?`.
    - `UpdateUnavailabilityRequest`: mismos formatos con `expectedLockVersion` obligatorio.
    - `CancelUnavailabilityRequest` (`expectedLockVersion`).
    - `UnavailabilityConflictCode`: códigos RFC 9457 reales (`CONCURRENT_MODIFICATION`, `PAST_INTERVAL_MODIFICATION_PROHIBITED`, `UNAVAILABILITY_ALREADY_CANCELLED`, `UNAVAILABILITY_NOT_FOUND`, `EMPLOYEE_NOT_FOUND`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_KEY_EXPIRED`, `VALIDATION_FAILED`). Sin conflicto artificial `UNAVAILABILITY_OVERLAP` (los intervalos solapados están permitidos en base de datos; la compatibilidad contra turnos corresponde a I3).
    - `PendingUnavailabilityIntent`: unión discriminada para intenciones `create`, `update` y `cancel`.
  - Cliente HTTP en `frontend/src/api/unavailabilityApi.ts`: seis operaciones tipadas con `apiClient`:
    - Lectura: `getOrganizationUnavailabilities(from, to, includeCancelled)`, `getEmployeeUnavailabilities(employeeId, from, to, includeCancelled)` y `getUnavailabilityById(unavailabilityId)`.
    - Mutación: `createUnavailability`, `updateUnavailability` y `cancelUnavailability`, todas exigiendo cabecera `Idempotency-Key: string` obligatoria y token CSRF.

- **F4.2 — Listado y consulta de indisponibilidades: COMPLETADO.**
  - Ruta `/app/indisponibilidades` integrada en enrutador SPA nativo (`frontend/src/App.tsx`) y navegación (`AppSidebar`, `AppHeader`, tarjeta en `HomePage` y enlace secundario en `EmployeesPage`).
  - Matriz de permisos RBAC: acceso a la consulta y gestión para `ADMINISTRADOR` y `SUPERVISOR` (RN-012, RN-014); `COLABORADOR` redirigido a `/acceso-restringido`.
  - Carga coordinada en paralelo mediante `Promise.all`: listado organizacional de indisponibilidades (`unavailabilityApi.getOrganizationUnavailabilities`) y catálogo de colaboradores (`employeeApi.getEmployees`) para resolución local mediante `employeeMap`. Si falla alguna fuente, se captura en `loadError` y se presenta pantalla de error con botón de reintento.
  - Ventana temporal obligatoria para backend: selectores rápidos (presets) de 30, 60 y 90 días calculados en UTC y formateados en la zona horaria oficial del tenant (`timeZone`).
  - Filtros y búsqueda reactiva: toggle para incluir o excluir indisponibilidades canceladas, selector dropdown por colaborador, selector por motivo y búsqueda local por código o nombre del colaborador.
  - Confidencialidad estricta: Las notas restringidas (RN-014) nunca se renderizan en la tabla, filtros, resultados de búsqueda, mensajes de error ni registros del cliente. El frontend solo las utiliza dentro del modal autorizado cuando el contrato de la API las entrega; no se persisten en almacenamiento local ni se exponen en la vista de listado. Los badges de motivo y estado diferencian visualmente registros activos y cancelados.

- **F4.3 — Modales y mutaciones seguras de indisponibilidades: COMPLETADO.**
  - Matriz de permisos: creación, edición y cancelación habilitadas de forma simétrica para `ADMINISTRADOR` y `SUPERVISOR`.
  - Diálogos modales accesibles:
    - `UnavailabilityModal`: alta (con selección de colaborador activo) y edición (con colaborador bloqueado en solo lectura); alternancia de modalidad temporal mediante pestañas accesibles; nota restringida confidencial (RN-014) con advertencia de privacidad y límite de 500 caracteres UTF-16 sin recorte; foco inicial programado y cierre con Escape/backdrop.
    - `UnavailabilityCancelModal`: diálogo de confirmación neutral con paso obligatorio de `expectedLockVersion`; bloqueo visual e inhabilitación si la indisponibilidad ya fue cancelada o si su fecha de inicio ya transcurrió (`startsAt <= now`, RN-013); explicaciones neutras sin consecuencias ficticias de nómina ni aprobaciones.
  - Regla temporal real aplicada en cliente y servidor:
    - Alta: permite registrar indisponibilidades con periodos en curso si `endsAt` está en el futuro, pero bloquea periodos ya finalizados.
    - Edición y cancelación: bloquea y rechaza periodos ya iniciados o transcurridos (`startsAt <= now`, RN-013), con botones deshabilitados y tooltips explicativos en la tabla.
  - Corrección de día completo:
    - No se infiere `allDay` desde instantes UTC (la entidad backend no almacena dicha bandera y la medianoche local en husos como America/Lima se persiste como 05:00:00Z).
    - En edición, el modal abre siempre en modo "Horario parcial (UTC)" por defecto, sin derivar fechas con `slice(0, 10)`.
    - La conversión a día completo durante edición requiere selección explícita y consciente de fechas locales por el usuario, mostrando la aclaración honesta: *"El formato original no se conserva. Para guardar como día completo, selecciona las fechas locales."*.
    - En alta, la fecha inicial de día completo se calcula en la zona horaria del tenant (`timeZone`) usando `Intl.DateTimeFormat(..., { timeZone }).formatToParts()`, sin `now.toISOString().slice(0, 10)` ni `new Date('YYYY-MM-DD')`. La conversión a `endDate` exclusiva (+1 día) se realiza por aritmética de calendario numérico con `Date.UTC`.
  - Control de concurrencia e idempotencia estricto (`PendingUnavailabilityIntent`):
    - Generación de UUID v4 con Web Crypto API (`crypto.randomUUID()`).
    - Reutilización de `Idempotency-Key` exclusivamente ante fallo de red (`err.isNetworkError`) cuando la operación, identificador, versión de bloqueo y payload coinciden exactamente.
    - Cualquier cambio en datos o inicio de nueva acción genera una clave limpia. Limpieza de intención tras éxito o error del servidor.
    - Captura de colisiones 409 `CONCURRENT_MODIFICATION` con cierre de modal, recarga automática de datos y banner de conflicto interactivo.

- **Evidencia de compilación y verificación manual integrada del MVP (2026-09-25):**
  - `npm run build` en `frontend/`: BUILD SUCCESS (`72 modules transformed`, 0 errores TypeScript, 0 advertencias Vite).
  - Entorno local levantado con SQL Server en Docker, backend Spring Boot local y frontend Vite.
  - Base demo aprovisionada mediante el operador de plataforma y enlace de acceso inicial (`/setup#token=...`).
  - Inicio de sesión exitoso del administrador empresarial con organización `DEMO-2026` y `admin.demo@turnoshift.local`.
  - Navegación funcional por Configuración (`/app/configuracion`), Puestos (`/app/puestos`), Equipo (`/app/equipo`) e Indisponibilidades (`/app/indisponibilidades`).
  - Dependencia operativa comprobada: para seleccionar un colaborador en indisponibilidades debe existir previamente un puesto activo y un colaborador asignado.
  - Corrección visual validada manualmente: los modales de Puestos, Equipo e Indisponibilidades cubren el viewport completo mediante portal en `document.body`; no existe desbordamiento horizontal global; los scrollbars se integran al tema claro/cálido; comportamiento revisado a 80 %, 100 % y 125 % de zoom.

---

## Estado actual de I2

- **I2 — Gestión de personal y estructura organizativa: COMPLETADO (2026-09-25).**
  - **Componentes cerrados de I2:**
    - Backend y API: **I2.1** (Configuración y puestos), **I2.2** (Colaboradores y vínculo con cuentas), **I2.3** (Política operativa inicial) e **I2.4** (Indisponibilidades de colaboradores).
    - Frontend: **F1** (Autenticación, layout base y establecimiento de contraseña inicial `F1.1–F1.5`), **F2** (Configuración organizacional y puestos `F2.1–F2.3`), **F3** (Directorio y gestión de colaboradores `F3.1–F3.3`) y **F4** (Indisponibilidades de colaboradores `F4.1–F4.3`).
    - Verificación manual integrada del MVP completada en entorno local (`DEMO-2026`).
- **Pendientes fuera de I2:**
  - Evolución del dashboard operativo (`/app/inicio`).
  - Mejoras visuales futuras.
  - Preparación extendida de demo y datos de presentación.

---

## I3 — Planificación de turnos

### Alcance completo de I3 (EN CURSO)
- Consulta del estado de planificación semanal (`HU-11`).
- Creación y edición de borradores semanales con tolerancia progresiva (`HU-12`).
- Motor determinista de validación de restricciones y advertencias (`HU-13`).
- Previsualización y publicación atómica del cuadrante semanal (`HU-14`).
- Protección de compromisos publicados frente a mutaciones de I2 (`RN-011`, `RN-013`).
- Consulta personal del horario publicado por el colaborador (`HU-17`).

### Subtarea completada: I3.1-a — Persistencia y migraciones de planificación de turnos (2026-10-29)

1. **V18 — `V18__schedule_planning_tables.sql`:**
   - Creación de las 9 tablas del dominio en orden topológico acíclico:
     - `schedules`
     - `schedule_drafts`
     - `assignments`
     - `assignment_revisions`
     - `draft_assignments`
     - `planning_change_sets`
     - `planning_warning_acknowledgements`
     - `schedule_publications`
     - `publication_assignments`
   - Aislamiento multi-tenant estricto mediante `organization_id` y claves candidatas compuestas empresariales `(organization_id, schedule_id, ...)`.
   - Semana de inicio obligatoriamente lunes: `chk_schedules_week_start_monday` con `DATEDIFF(DAY, '19000101', week_start) % 7 = 0` (independiente de `@@DATEFIRST` e idioma).
   - Duración de turnos entre 15 y 960 minutos (`DATEDIFF(MINUTE, starts_at, ends_at) BETWEEN 15 AND 960`), permitiendo cruce de medianoche en turnos nocturnos.
   - Restricciones de estados de borrador (`BORRADOR`, `PUBLICADO`, `DESCARTADO`) y coherencia física con `result_publication_id`.
   - Restricción de `change_type` exclusivamente a operaciones de planificación (`PUBLICACION_BORRADOR`, `REVISION_PUBLICACION`, `APROBACION_INTERCAMBIO`), excluyendo `ACTIVACION_POLITICA`.
   - Soporte de publicación vacía conforme a `DEC-03` (`empty_schedule_confirmed = 0 OR (reason IS NOT NULL AND LEN(LTRIM(RTRIM(reason))) > 0)`).
   - Registro estructurado de advertencias operativas conforme a `DEC-04` (`chk_warning_acks_code`, `chk_warning_acks_minutes_exceeded`, `chk_warning_acks_period`, `chk_warning_acks_json`).
   - Índices filtrados: `uix_schedule_drafts_one_open` (un solo borrador abierto por cuadrante, **RN-027**) y `uix_draft_assignments_unique_published_ref`.
   - Ausencia total de columna huérfana `shift_type_id` en `draft_assignments` y `assignment_revisions` (diferida a evolución SaaS posterior).

2. **V19 — `V19__schedule_planning_circular_fks.sql`:**
   - Comprobaciones *preflight fail-closed* con `THROW 50000` sobre punteros no nulos huérfanos o cruzados en `schedules.current_publication_id`, `schedule_drafts.base_publication_id` y `schedule_drafts.result_publication_id`.
   - Instalación de las 3 claves foráneas compuestas diferidas hacia `schedule_publications (organization_id, schedule_id, id)`:
     - `fk_schedules_current_publication`
     - `fk_schedule_drafts_base_publication`
     - `fk_schedule_drafts_result_publication`

3. **Suite de pruebas de integración `SchedulePlanningPersistenceIT.java`:**
   - 9 escenarios ejecutados contra SQL Server 2022 real (Testcontainers):
     1. Migración limpia V1–V19 e inspección de catálogo de las 9 tablas, columnas, constraints (`pk_*`, `uq_*`, `fk_*`, `chk_*`) e índices operativos/filtrados.
     2. Ciclo de publicación en dos pasos y claves foráneas circulares (V19).
     3. Aislamiento multi-tenant estricto entre organizaciones distintas.
     4. Rechazo de referencias cruzadas entre distintas semanas (`W1` vs `W2`) del mismo tenant.
     5. Unicidad de semana por sede (`uq_schedules_org_site_week`).
     6. Unicidad de un solo borrador abierto (`uix_schedule_drafts_one_open`).
     7. Validaciones CHECK de dominio: lunes canónico, duración de turnos (15 a 960 min), cruce de medianoche, coherencia de borrador y `change_type`.
     8. Publicación vacía permitida con motivo (`DEC-03`) y reconocimientos de advertencia (`DEC-04`).
     9. Upgrade progresivo V17 → V19 preservando intactos todos los datos de entidades previas (organizaciones, sedes, puestos, colaboradores, cuentas, políticas e indisponibilidades).

4. **Sincronización de pruebas Flyway existentes (V17 → V19):**
   - Actualización de listas y aserciones de versiones en `SchemaUpgradeV3ToLatestIT`, `SpringBootStartupIT`, `PolicyPersistenceIT`, `UnavailabilityPersistenceIT` y `OrganizationsSitesSchemaIT`.

5. **Evidencia exacta de verificación:**
   ```powershell
   cd backend; $env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test "-Dtest=SchedulePlanningPersistenceIT,SchemaUpgradeV3ToLatestIT,SpringBootStartupIT"
   ```
   - **Resultado:**
     - `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`
     - `BUILD SUCCESS` (duración total 10:41 min).

6. **Delimitación estricta de alcance:**
   - **I3 sigue EN CURSO.**
   - Únicamente se ha completado su capa de persistencia y migraciones de base de datos (`I3.1-a`).
   - No existen aún entidades de dominio Java, motor `ScheduleValidationEngine`, repositorios JDBC funcionales, casos de uso transaccionales, candados `sp_getapplock` (`TurnoShift_OrgPlanning_<organizationId>`), controladores REST, endpoints en `docs/api/openapi.yaml` ni pantallas Frontend (F5/F6).

---

### Subtarea completada: I3.1-b — Dominio de Planificación, Motor de Validación y Persistencia JDBC (2026-10-29)

1. **Modelo de dominio inmutable y value object propio (`com.turnoshift.schedule.domain`):**
   - Value object local `ShiftInterval`: encapsula intervalo semiabierto $[startsAt, endsAt)$, valida duración estricta ($15 \le \text{min} \le 960$) y solapamiento; excepciones propias del dominio (`InvalidShiftIntervalException`, `ScheduleDomainException`) sin propagar excepciones de indisponibilidades.
   - Entidades y value objects inmutables:
     - `Schedule`: contenedor semanal de sede, valida lunes canónico y `lockVersion >= 0`.
     - `ScheduleDraft` y `DraftStatus`: borrador (`BORRADOR`, `PUBLICADO`, `DESCARTADO`), coherencia con `resultPublicationId` e inmutabilidad en asignaciones.
     - `DraftAssignment`: línea editable del borrador, coherencia obligatoria de `assignmentId` y `baseRevisionId`.
     - `Assignment` y `AssignmentRevision`: identidad de turno y revisión inmutable con snapshots de nombres.
     - `SchedulePublication`, `PublicationOrigin`, `PublishedAssignmentRef` y `PublishedShiftRecord`: instantánea oficial con numeración positiva y origen (`INICIAL`, `REVISION`, `INTERCAMBIO`).
     - `PlanningChangeSet` y `PlanningChangeType`: auditoría de publicación sin `ACTIVACION_POLITICA`, con confirmación de publicación vacía conforme a `DEC-03`.
     - `PlanningWarningAcknowledgement`: reconocimiento de advertencia con validación de período de 7 días y exceso de umbral conforme a `DEC-04`.
   - Seguridad e inmutabilidad: encapsulamiento con `List.copyOf`, clonación defensiva de arrays binarios (`byte[]`) y sobrescritura de `equals`/`hashCode` con `Arrays.equals`.

2. **Motor determinista puro `ScheduleValidationEngine` (`com.turnoshift.schedule.domain.validation`):**
   - Servicio sin efectos secundarios ni dependencias de infraestructura:
     - `POLICY_NOT_CONFIGURED`: bloquea publicación si no hay política activa.
     - `PAST_SHIFT_MODIFICATION` (RN-031):
       - turno nuevo en el pasado (`assignmentId == null`, `startsAt <= now`): bloqueado;
       - turno derivado de publicación base previa ya iniciado (`startsAt <= now`) idéntico en horario, empleado y puesto: permitido sin falsos positivos;
       - turno publicado ya iniciado alterado o eliminado del borrador: bloqueado.
     - `EMPLOYEE_INACTIVE`, `POSITION_INACTIVE` y `POSITION_MISMATCH` (RN-010): validación de catálogos y coincidencia obligatoria con puesto principal.
     - `EMPLOYEE_UNAVAILABLE` (RN-019): cruce semiabierto con indisponibilidades activas (`cancelled_at IS NULL`) sin exponer notas restringidas (`restricted_note`, RN-014).
     - `SHIFT_OVERLAP` (RN-017) y `MINIMUM_REST_VIOLATION` (RN-018): descanso mínimo continuo dentro de la semana y contra fronteras $W-1$ / $W+1$.
     - `WEEKLY_PLANNED_MINUTES_EXCEEDED` (RN-023 / DEC-04): advertencia operativa que no bloquea (`canPublish == true`). Atribución íntegra a $W$ de turnos nocturnos de domingo que cruzan medianoche (PROP-I3-01 / PROP-I3-02).

3. **Huellas criptográficas canónicas SHA-256 (`byte[32]`):**
   - `WarningFingerprintCalculator`: cálculo canónico SHA-256 (`"w1|<orgId>|<employeeId>|<periodStart>|<periodEndExclusive>|<plannedMinutes>|<thresholdMinutes>"`) retornado como `byte[32]`.
   - `ValidationFingerprintCalculator`: cálculo canónico SHA-256 normalizado e invariante frente al orden de las asignaciones, turnos de frontera y advertencias, retornado como `byte[32]`.
   - Representación hexadecimal (`String` de 64 caracteres) expuesta únicamente como método de conveniencia para la futura capa HTTP.

4. **Puertos y adaptadores JDBC (`com.turnoshift.schedule.domain.ports` y `com.turnoshift.schedule.infrastructure.persistence`):**
   - `ScheduleRepository` / `JdbcScheduleRepository`: consultas por ID y sede/semana, aislamiento multi-tenant estricto por `organization_id`, y actualización condicional de puntero con control optimista de `lock_version`.
   - `ScheduleDraftRepository` / `JdbcScheduleDraftRepository`: inserción/actualización/eliminación de líneas que avanza obligatoriamente el `lock_version` del borrador, rechazando versiones obsoletas con `ScheduleConcurrentModificationException`.
   - `SchedulePublicationRepository` / `JdbcSchedulePublicationRepository`: método `createPublicationAtomic` con secuencia atómica de 7 pasos (`planning_change_sets` $\rightarrow$ `acknowledgements` $\rightarrow$ `assignments/revisions` $\rightarrow$ `schedule_publications` $\rightarrow$ `publication_assignments` $\rightarrow$ `UPDATE schedule_drafts` $\rightarrow$ `UPDATE schedules`), satisfaciendo las claves foráneas compuestas circulares de V19 sin violaciones de integridad.
   - `PlanningContextSnapshotPort` / `JdbcPlanningContextSnapshotAdapter`: consulta desacoplada de instantáneas operativas (políticas, empleados, puestos, indisponibilidades activas y turnos publicados adyacentes).
   - `ScheduleInfrastructureConfiguration`: configuración Spring Boot de persistencia.

5. **Evidencia exacta de verificación:**
   ```powershell
   cd backend; $env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test "-Dtest=ScheduleValidationEngineTest,ValidationFingerprintCalculatorTest,JdbcScheduleRepositoryIT,JdbcScheduleDraftRepositoryIT,JdbcSchedulePublicationRepositoryIT"
   ```
   - **Resultado:**
     - `Tests run: 32, Failures: 0, Errors: 0, Skipped: 0`
     - `BUILD SUCCESS` (duración `07:50 min`).

6. **Delimitación estricta de alcance:**
   - **I3 sigue EN CURSO.**
   - Las subfases `I3.1-a` e `I3.1-b` están COMPLETADAS.
   - Aún no existen casos de uso de aplicación, idempotencia HTTP (`BusinessIdempotentOperationExecutor`), adquisición del candado transaccional `TurnoShift_OrgPlanning_<organizationId>` (`sp_getapplock`), controladores REST, endpoints en `docs/api/openapi.yaml` ni frontend de planificación (F5/F6).

---

## Siguiente paso inmediato del proyecto

- **Diseño técnico del subincremento `I3.1-c — Casos de Uso, Idempotencia, Candado Operativo sp_getapplock y API HTTP`:**
  - Casos de uso transaccionales de planificación (creación de borrador, mutación de asignaciones, descarte y publicación atómica).
  - Deduplicación idempotente (`Idempotency-Key` y `api_operations`).
  - Candado operativo exclusivo `TurnoShift_OrgPlanning_<organizationId>` vía `sp_getapplock` para serializar publicaciones.
  - Integración coordinada con I2 para proteger compromisos publicados frente a mutaciones de colaboradores e indisponibilidades (`RN-011`, `RN-013`).
  - Controladores REST y respuestas estandarizadas RFC 9457 Problem Details.

---

## Puntos transversales pendientes
- **DEC-06:** Definición del procedimiento operativo de entrega fuera de banda del enlace inicial al administrador durante la fase piloto.
- **Rate limiting distribuido:** Migración futura de limitadores en memoria a solución distribuida/persistente al escalar horizontalmente.

---

## Referencia rápida: comandos verificados

```powershell
# Tests completos
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"; cmd /c "mvnw.cmd verify -B"

# Backend local (perfil local deshabilita Secure en cookie)
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
$env:SPRING_PROFILES_ACTIVE = "local"
cmd /c "mvnw.cmd spring-boot:run"
# → GET http://localhost:8081/api/health → {"status":"UP","service":"turnoshift-backend"}
# → GET http://localhost:8081/api/v1/auth/csrf → {"token":"..."}

# Frontend
cd frontend; npm run build
```
