
## 1. Propósito

Definir el modelo relacional del MVP de TurnoShift, incluyendo:

- Entidades y relaciones.
    
- Identificadores.
    
- Restricciones.
    
- Separación empresarial.
    
- Borradores y publicaciones.
    
- Revisiones de asignaciones.
    
- Intercambios.
    
- Políticas y advertencias reconocidas.
    
- Auditoría.
    
- Sesiones y recuperación.
    
- Avisos recuperables.
    
- Prevención de operaciones duplicadas.
    

Este es un modelo lógico con decisiones físicas preliminares para SQL Server 2022.

No contiene todavía las migraciones definitivas. Su implementación deberá comprobarse mediante pruebas de integridad, concurrencia y recuperación.

### 1.1. Cambios de esta revisión

Se mantiene el núcleo del modelo anterior y se incorporan:

1. `planning_change_sets`: decisiones confirmadas de publicación, intercambio y activación de políticas.
    
2. `planning_warning_acknowledgements`: evidencia de las advertencias reconocidas.
    
3. `api_operations`: registro temporal de idempotencia.
    
4. Identidad propia para las líneas del borrador.
    
5. Representación explícita de publicaciones vacías.
    
6. Reglas para huellas de revisión y consistencia de reportes.
    
7. Correspondencia con la versión consolidada del documento 04.
    

Esta versión sustituye el contenido anterior de la nota 07.

## 2. Documentos de referencia

- [[03 - Alcance MVP y Roadmap Comercial]]
    
- [[04 - Roles, Procesos y Reglas de Negocio]]
    
- [[05 - Requisitos y Casos de Uso]]
    
- [[06 - Arquitectura y Stack Tecnológico]]
    
- [[10 - Contratos API e Integraciones]]
    
- [[12 - Pruebas, Seguridad y Operación]]
    

Las relaciones representadas aquí no sustituyen la autorización.

Una base consistente puede seguir siendo insegura si la aplicación consulta información sin comprobar organización y permisos.

## 3. Decisiones principales

1. Base y esquema compartidos.
    
2. Pertenencia empresarial mediante `organization_id`.
    
3. Cuenta separada del colaborador.
    
4. Roles globales predefinidos.
    
5. Un puesto principal por colaborador.
    
6. Una sede operativa activa por organización.
    
7. Borradores separados de publicaciones.
    
8. Publicaciones y revisiones inmutables.
    
9. Solicitudes referenciadas a revisiones concretas.
    
10. Reservas para impedir solicitudes pendientes incompatibles.
    
11. Decisiones y advertencias conservadas como evidencia.
    
12. Auditoría y eventos pendientes dentro de las transacciones críticas.
    
13. Idempotencia persistente y separada del historial de negocio.
    
14. Sin tablas de asistencia, nómina ni facturación automática.
    

## 4. Convenciones generales

### 4.1. Nombres

- Tablas y columnas: inglés, `snake_case`.
    
- Propiedades de API: inglés, `camelCase`.
    
- Documentación funcional: español.
    
- Estados: códigos estables.
    
- Pertenencia empresarial: `organization_id`.
    

No se alternará entre `tenant_id`, `company_id` y `organization_id` para la misma relación.

### 4.2. Tipos físicos propuestos

|Información|Tipo|
|---|---|
|Identificadores de negocio|`uniqueidentifier`|
|Texto Unicode limitado|`nvarchar(n)`|
|Códigos técnicos|`varchar(n)`|
|Fecha de semana o periodo|`date`|
|Hora de plantilla|`time(0)`|
|Instantes|`datetimeoffset(3)`|
|Indicadores|`bit`|
|Minutos y contadores|`int`|
|Versión de edición|`bigint`|
|Huellas SHA-256|`binary(32)`|
|Datos auxiliares estructurados|`nvarchar(max)` con validación JSON y límites|

Los instantes se normalizarán a UTC. La zona horaria se conserva por separado.

`datetimeoffset` almacena un desplazamiento; no sustituye el identificador de zona con sus reglas. [Referencia: Microsoft, datetimeoffset](https://learn.microsoft.com/en-us/sql/t-sql/data-types/datetimeoffset-transact-sql?view=sql-server-ver16).

Las huellas se presentan en la API mediante una codificación textual definida. No son permisos ni secretos de autenticación.

### 4.3. Campos comunes

**Identificación empresarial:**

- `id`.
    
- `organization_id`.
    
- `created_at`, salvo que exista un campo temporal específico equivalente.
    

**Entidades mutables:**

- `updated_at`.
    
- `lock_version`, cuando exista edición concurrente.
    

Los registros inmutables no necesitan `updated_at`.

Las tablas de asociación pueden utilizar claves compuestas y omitir un identificador adicional.

### 4.4. Claves empresariales

Las entidades referenciadas tendrán una clave candidata compatible con:

`(organization_id, id)`

Las FKs empresariales incluirán ambas columnas.

Una FK por `id` y otra independiente por `organization_id` no garantizan que ambas referencias correspondan a la misma empresa. La relación debe expresar la combinación adecuada. [Referencia: Microsoft, claves primarias y foráneas](https://learn.microsoft.com/en-us/sql/relational-databases/tables/primary-and-foreign-key-constraints?view=sql-server-ver16).

### 4.5. Nulabilidad

Los campos son obligatorios salvo indicación contraria.

En relaciones opcionales:

- Se permite que el identificador relacionado sea nulo.
    
- Si varios identificadores opcionales representan una misma relación, se controla su presencia conjunta.
    
- `organization_id` y otros campos de ámbito obligatorio no se vuelven nulos para representar ausencia.
    

### 4.6. JSON

El JSON se reserva para información auxiliar limitada, no para reemplazar las relaciones principales.

Se comprobarán:

- Formato.
    
- Estructura esperada.
    
- Tipos.
    
- Tamaño.
    
- Campos permitidos.
    
- Referencias incluidas.
    

`ISJSON` comprueba validez JSON, pero no garantiza el esquema de negocio ni la unicidad de nombres de propiedades. [Referencia: Microsoft, ISJSON](https://learn.microsoft.com/en-us/sql/t-sql/functions/isjson-transact-sql?view=sql-server-ver17).

### 4.7. Eliminación

No se añadirá `deleted_at` indiscriminadamente.

Se distinguen:

- Desactivación.
    
- Cancelación.
    
- Descarte.
    
- Conservación histórica.
    
- Limpieza técnica.
    
- Eliminación o anonimización controlada.
    

No habrá cascadas de eliminación sobre el historial empresarial.

## 5. Inventario de entidades

|   |   |
|---|---|
|Área|Entidades|
|Plataforma|`platform_operators`, `organizations`|
|Identidad|`roles`, `app_users`, `password_reset_tokens`|
|Operación|`sites`, `positions`, `employees`, `employee_unavailabilities`|
|Configuración|`planning_policy_versions`, `shift_types`|
|Planificación|`schedules`, `assignments`, `assignment_revisions`, `schedule_drafts`, `draft_assignments`, `schedule_publications`, `publication_assignments`|
|Decisiones|`planning_change_sets`, `planning_warning_acknowledgements`|
|Intercambios|`swap_requests`, `swap_events`, `swap_assignment_reservations`|
|Trazabilidad y avisos|`audit_entries`, `outbox_events`, `notifications`|
|Idempotencia|`api_operations`|
|Infraestructura|Tablas de Spring Session JDBC e historial de migraciones|

Los registros comerciales detallados se mantienen fuera del modelo operativo inicial.

## 6. DER: organización, identidad y operación

```
erDiagram
    ORGANIZATIONS ||--o{ SITES : contiene
    ORGANIZATIONS ||--o{ APP_USERS : habilita
    ORGANIZATIONS ||--o{ POSITIONS : define
    ORGANIZATIONS ||--o{ EMPLOYEES : registra
    ORGANIZATIONS ||--o{ PLANNING_POLICY_VERSIONS : configura
    ROLES ||--o{ APP_USERS : clasifica
    SITES ||--o{ EMPLOYEES : agrupa
    POSITIONS ||--o{ EMPLOYEES : asigna
    EMPLOYEES o|--o| APP_USERS : vincula
    EMPLOYEES ||--o{ EMPLOYEE_UNAVAILABILITIES : restringe
    APP_USERS ||--o{ PASSWORD_RESET_TOKENS : recupera
```

Los diagramas muestran relaciones lógicas seleccionadas. El diccionario define las FKs, estados y restricciones completas.

La cardinalidad permite conservar sedes históricas, aunque el MVP limita a una sede activa.

### 6.1. `platform_operators`

Identifica a personas autorizadas para administrar el servicio.

Campos:

- `id`.
    
- `subject`: identidad administrativa.
    
- `display_name`.
    
- `is_active`.
    
- `created_at`.
    

Restricciones:

- `subject` único.
    
- No pertenece a una organización cliente.
    
- No se mezcla con `app_users`.
    
- No concede acceso empresarial por sí mismo.
    

No implica construir otro portal público de autenticación.

### 6.2. `organizations`

Campos:

- `id`.
    
- `code`: código normalizado de acceso.
    
- `display_name`.
    
- `service_status`: `ACTIVA`, `SUSPENDIDA`, `CERRADA`.
    
- `timezone_id`.
    
- `max_active_employees`.
    
- `max_active_sites`.
    
- `current_policy_version_id`: opcional durante configuración.
    
- `created_at`.
    
- `updated_at`.
    
- `lock_version`.
    

Restricciones:

- Código único global.
    
- Capacidades positivas.
    
- `max_active_sites = 1` durante el MVP.
    
- Política vigente perteneciente a la organización.
    
- Suspensión y cierre no eliminan dependencias.
    

La zona queda bloqueada para edición ordinaria desde la primera publicación.

Esa condición se deriva de la existencia de publicaciones históricas, no de que actualmente existan turnos.

Antes de la primera publicación, un cambio de zona debe revalidar los borradores sin alterar silenciosamente los instantes guardados.

### 6.3. `roles`

Catálogo global:

- `ADMINISTRADOR`.
    
- `SUPERVISOR`.
    
- `COLABORADOR`.
    

Campos:

- `code`: PK.
    
- `name`.
    
- `description`.
    

No contiene `organization_id`.

Los permisos se mantienen de forma versionada en la aplicación. No se incluyen roles personalizados.

El operador no es un cuarto rol empresarial.

### 6.4. `app_users`

Campos:

- Campos comunes empresariales.
    
- `email`.
    
- `normalized_email`.
    
- `display_name`.
    
- `password_hash`: opcional únicamente mientras no exista una contraseña establecida.
    
- `role_code`.
    
- `is_active`.
    
- `employee_id`: opcional.
    
- `password_changed_at`: opcional antes del establecimiento inicial.
    
- `must_change_password`.
    
- `auth_version`.
    

Restricciones:

- Único `(organization_id, normalized_email)`.
    
- FK hacia `roles`.
    
- FK empresarial hacia `employees`.
    
- Índice único `(organization_id, employee_id)` cuando `employee_id IS NOT NULL`.
    
- Normalización de correo definida por la aplicación.
    

El mismo correo puede existir en diferentes organizaciones.

Una cuenta sin contraseña establecida no puede autenticarse con contraseña. No se inventa una contraseña temporal compartida para completar el alta.

`auth_version` permite invalidar sesiones anteriores. No reemplaza la comprobación del rol y estado actuales.

Cambiar el vínculo con un colaborador se bloquea mientras existan solicitudes pendientes en las que participe la cuenta.

La desactivación invalida las solicitudes afectadas mediante el servicio transaccional.

### 6.5. `password_reset_tokens`

Sirve para establecimiento inicial y recuperación.

Campos:

- Identificación empresarial.
    
- `user_id`.
    
- `token_hash`.
    
- `purpose`: `INITIAL_SETUP` o `PASSWORD_RESET`.
    
- `expires_at`.
    
- `consumed_at`: opcional.
    
- `revoked_at`: opcional.
    

Restricciones:

- FK empresarial hacia `app_users`.
    
- Hash único.
    
- Vencimiento posterior a creación.
    
- No guardar el token original.
    
- Consumo atómico.
    
- No consumir después de revocación, vencimiento o consumo previo.
    

La nueva emisión revoca mecanismos anteriores según la política definida.

La inspección del enlace no lo consume.

Los registros se limpian según retención; la auditoría de seguridad no depende de conservarlos indefinidamente.

### 6.6. `sites`

Campos:

- Campos comunes empresariales.
    
- `code`.
    
- `name`.
    
- `is_active`.
    

Restricciones:

- Código único por organización.
    
- Índice único de `organization_id` filtrado por sede activa.
    
- No desactivar una sede con compromisos sin resolver.
    

La ampliación multisede requerirá revisar esta restricción y los procesos.

### 6.7. `positions`

Campos:

- Campos comunes empresariales.
    
- `code`.
    
- `name`.
    
- `is_active`.
    

Restricciones:

- Código único por organización.
    
- No eliminar puestos referenciados.
    
- Desactivar impide nuevos usos, sin destruir historial.
    
- Resolver compromisos incompatibles antes de cambios que afecten su operación.
    

### 6.8. `employees`

Campos:

- Campos comunes empresariales.
    
- `employee_code`.
    
- `display_name`.
    
- `site_id`.
    
- `position_id`.
    
- `is_active`.
    

Restricciones:

- Código único por organización.
    
- FKs empresariales a sede y puesto.
    
- Un puesto principal.
    
- Desactivación condicionada a compromisos.
    
- Cambios de puesto sujetos a validación.
    

Un colaborador activo puede recibir asignaciones sin cuenta.

La vinculación se guarda únicamente en `app_users.employee_id`.

No se agregan salario, datos bancarios, información médica o documentos de identidad como requisitos del MVP.

### 6.9. `employee_unavailabilities`

Campos:

- Campos comunes empresariales.
    
- `employee_id`.
    
- `starts_at`.
    
- `ends_at`.
    
- `reason_code`: opcional.
    
- `restricted_note`: opcional y limitada.
    
- `cancelled_at`: opcional.
    

Restricciones:

- FK empresarial al colaborador.
    
- `starts_at < ends_at`.
    
- La cancelación retira el intervalo del cálculo vigente.
    
- Crear o editar no puede contradecir turnos publicados.
    

Las superposiciones entre indisponibilidades representan unión de intervalos no disponibles. No se suman como horas de ausencia.

La validación se coordina con publicaciones concurrentes.

### 6.10. `planning_policy_versions`

Versiones inmutables de política.

Campos:

- Identificación empresarial.
    
- `version_number`.
    
- `minimum_rest_minutes`.
    
- `weekly_warning_minutes`.
    
- `effective_at`.
    
- `created_by_user_id`.
    
- `change_reason`.
    

Restricciones:

- Único `(organization_id, version_number)`.
    
- Descanso no negativo.
    
- Umbral positivo.
    
- Autor perteneciente a la organización.
    
- No editar una versión activada.
    
- Activación inmediata, sin programación futura en el MVP.
    

Una activación crea también un `planning_change_sets`.

El autor, motivo y momento de la versión deben coincidir con los de su decisión de activación. Son metadatos históricos duplicados de forma controlada, no editables por separado.

El puntero en `organizations` identifica la versión vigente.

Antes de activarla se comprueban compromisos futuros y se registran las advertencias reconocidas. La vista previa no cambia el puntero.

### 6.11. `shift_types`

Campos:

- Campos comunes empresariales.
    
- `code`.
    
- `name`.
    
- `start_local_time`.
    
- `end_local_time`.
    
- `end_day_offset`.
    
- `is_active`.
    

Restricciones:

- Código único por organización.
    
- `end_day_offset` limitado a los casos admitidos por la plantilla del MVP.
    
- La plantilla no determina por sí sola instantes definitivos.
    
- La fecha y zona se aplican al crear el turno.
    
- Se valida el intervalo resultante.
    

No son obligatorias para publicar.

## 7. DER: planificación, decisiones e historial

```
erDiagram
    SITES ||--o{ SCHEDULES : organiza
    SCHEDULES ||--o{ SCHEDULE_DRAFTS : prepara
    SCHEDULE_DRAFTS ||--o{ DRAFT_ASSIGNMENTS : contiene
    SCHEDULES ||--o{ ASSIGNMENTS : identifica
    ASSIGNMENTS o|--o{ DRAFT_ASSIGNMENTS : referencia
    ASSIGNMENTS ||--o{ ASSIGNMENT_REVISIONS : versiona
    SCHEDULES ||--o{ SCHEDULE_PUBLICATIONS : conserva
    PLANNING_CHANGE_SETS ||--o{ SCHEDULE_PUBLICATIONS : produce
    PLANNING_POLICY_VERSIONS ||--o{ PLANNING_CHANGE_SETS : aplica
    PLANNING_CHANGE_SETS ||--o{ PLANNING_WARNING_ACKNOWLEDGEMENTS : documenta
    EMPLOYEES ||--o{ PLANNING_WARNING_ACKNOWLEDGEMENTS : afecta
    SCHEDULE_PUBLICATIONS ||--o{ PUBLICATION_ASSIGNMENTS : contiene
    ASSIGNMENT_REVISIONS ||--o{ PUBLICATION_ASSIGNMENTS : integra
```

Un conjunto de cambio puede producir cero publicaciones cuando corresponde a la activación de una política.

Una publicación puede tener cero elementos: representa una semana publicada vacía.

### 7.1. Distinción fundamental

**Asignación:** identidad estable de un turno publicado.

**Revisión:** contenido inmutable de esa asignación.

**Publicación:** conjunto exacto de revisiones que define una versión semanal.

**Borrador:** propuesta editable.

**Conjunto de cambio:** decisión confirmada que explica quién, cuándo y por qué produjo el cambio.

**Reconocimiento:** evidencia de una advertencia concreta aceptada al confirmar.

### 7.2. `schedules`

Campos:

- Campos comunes empresariales.
    
- `site_id`.
    
- `week_start`.
    
- `current_publication_id`: opcional.
    

Restricciones:

- Único `(organization_id, site_id, week_start)`.
    
- Semana iniciada en lunes.
    
- Publicación vigente perteneciente a la misma planificación.
    
- `week_start` no se edita para trasladar una planificación.
    

La FK del puntero incluirá organización y planificación.

No se mantiene otro indicador independiente `is_current` en publicaciones.

La comprobación de lunes debe implementarse sin depender accidentalmente de la configuración regional de la conexión.

### 7.3. `assignments`

Identidad estable de un turno.

Campos:

- Identificación empresarial.
    
- `schedule_id`.
    

No contiene el colaborador ni horario vigente. Esos datos se obtienen desde la revisión seleccionada por una publicación.

La identidad se crea al publicar por primera vez una línea nueva.

No se cambia de planificación una identidad publicada. Si se requiere trasladar un turno futuro entre semanas, se retira la identidad original y se crea otra mediante los flujos autorizados de esas semanas.

Esto no introduce una función automática de traslado entre semanas.

### 7.4. `assignment_revisions`

Campos:

- Identificación empresarial.
    
- `schedule_id`.
    
- `assignment_id`.
    
- `revision_number`.
    
- `employee_id`.
    
- `position_id`.
    
- `shift_type_id`: opcional.
    
- `starts_at`.
    
- `ends_at`.
    
- `employee_name_snapshot`.
    
- `position_name_snapshot`.
    
- `created_by_user_id`.
    

Restricciones:

- Único `(organization_id, assignment_id, revision_number)`.
    
- FK a la identidad dentro de su planificación.
    
- FKs empresariales a colaborador, puesto, plantilla y autor.
    
- `starts_at < ends_at`.
    
- Inmutabilidad después de confirmar.
    

Se crea una nueva revisión al cambiar el contenido operativo.

Si no cambia, se reutiliza la revisión anterior.

Volver posteriormente a valores antiguos genera una revisión nueva; no se reutiliza una revisión histórica para ocultar el cambio intermedio.

Las instantáneas de nombres permiten interpretar el historial. No se actualizan al renombrar al colaborador o puesto.

### 7.5. `schedule_drafts`

Campos:

- Campos comunes empresariales.
    
- `schedule_id`.
    
- `base_publication_id`: opcional.
    
- `status`: `BORRADOR`, `PUBLICADO`, `DESCARTADO`.
    
- `result_publication_id`: opcional.
    
- `change_reason`: opcional durante preparación.
    
- `created_by_user_id`.
    

Restricciones:

- Un único borrador abierto por planificación mediante índice filtrado.
    
- Origen y resultado pertenecen a la misma planificación.
    
- `PUBLICADO` requiere resultado.
    
- `BORRADOR` y `DESCARTADO` no tienen publicación resultante.
    
- Descartar no modifica el horario vigente.
    

“Desactualizado” se deriva de comparar la publicación base con la vigente, incluyendo correctamente el caso de valores nulos.

No se guarda un indicador redundante.

### 7.6. `draft_assignments`

Cada línea tiene identidad propia, independiente de una asignación publicada.

Campos:

- `id`: identificador de línea.
    
- `organization_id`.
    
- `schedule_id`.
    
- `draft_id`.
    
- `assignment_id`: opcional para líneas nuevas.
    
- `base_revision_id`: opcional para líneas nuevas.
    
- `employee_id`.
    
- `position_id`.
    
- `shift_type_id`: opcional.
    
- `starts_at`.
    
- `ends_at`.
    

Claves:

- PK `id`.
    
- Clave candidata `(organization_id, id)`.
    
- Único `(organization_id, draft_id, assignment_id)` cuando `assignment_id IS NOT NULL`.
    

Restricciones:

- Borrador y asignación pertenecen a la misma planificación.
    
- Una línea nueva tiene `assignment_id` y `base_revision_id` nulos.
    
- Una línea basada en un turno publicado informa ambos.
    
- La revisión de origen corresponde a esa asignación.
    
- Debe estar contenida en la publicación base del borrador, comprobado transaccionalmente.
    
- FKs empresariales de colaborador, puesto y plantilla.
    
- Intervalo válido.
    

Toda modificación de líneas incrementa `lock_version` del borrador padre.

#### Motivo de este ajuste

Una línea nueva necesita un identificador para editarla antes de publicarse, pero todavía no es una asignación oficial.

Esto coincide con el contrato:

- `item.id`: línea del borrador.
    
- `assignmentId`: nulo hasta la primera publicación.
    

No se utiliza un identificador ficticio de asignación para representar ese estado.

Al publicar, se crean las identidades necesarias y se completan las referencias de las líneas nuevas antes de cerrar el borrador, dentro de la transacción.

El borrador publicado queda congelado.

#### Conflictos permitidos

Las restricciones físicas permiten guardar intervalos operativamente conflictivos para corregirlos.

No se crean restricciones que impidan guardar cualquier superposición en un borrador, porque eso contradiría RN-057.

La publicación sí ejecuta todas las validaciones bloqueantes.

### 7.7. `planning_change_sets`

Representa una decisión confirmada, no una petición HTTP ni una vista previa.

Campos:

- Identificación empresarial.
    
- `change_type`:
    
    - `PUBLICACION_BORRADOR`.
        
    - `APROBACION_INTERCAMBIO`.
        
    - `ACTIVACION_POLITICA`.
        
- `actor_user_id`.
    
- `policy_version_id`.
    
- `previous_policy_version_id`: opcional, solo para activación.
    
- `source_draft_id`: opcional según tipo.
    
- `source_swap_request_id`: opcional según tipo.
    
- `reason`: opcional cuando las reglas lo permiten.
    
- `empty_schedule_confirmed`.
    
- `validation_fingerprint`.
    
- `validation_schema_version`.
    
- `correlation_id`.
    
- `confirmed_at`.
    

`confirmed_at` sustituye a `created_at` como momento funcional del registro.

#### Coherencia por tipo

|   |   |   |
|---|---|---|
|Tipo|Referencia requerida|Referencias excluidas|
|Publicación de borrador|`source_draft_id`|`source_swap_request_id`, `previous_policy_version_id`|
|Aprobación de intercambio|`source_swap_request_id`|`source_draft_id`, `previous_policy_version_id`|
|Activación de política|Política nueva y anterior, si existe|Ambas referencias de origen|

Las reglas de nulabilidad se expresan con restricciones.

Todas las referencias son empresariales.

#### Unicidad

- Un borrador puede originar como máximo una decisión de publicación.
    
- Una solicitud puede originar como máximo una decisión de aprobación.
    
- Una versión de política puede activarse una sola vez.
    

Se utilizarán índices únicos filtrados para los casos pertinentes.

#### Motivo y autor

El motivo es obligatorio cuando:

- Se reemplaza una publicación mediante borrador.
    
- Se publica una semana vacía.
    
- Se reconocen advertencias.
    
- Se activa una política.
    

Una aprobación sin advertencias conserva siempre actor y solicitud, aunque no requiera un comentario libre.

Para políticas se conserva también la explicación en la versión, coherente con este registro.

#### Inmutabilidad

No se registra un conjunto de cambio confirmado si falla la transacción.

El identificador sustituye al `change_set_id` sin entidad del modelo anterior: ahora existe un padre persistente al que pueden apuntar publicaciones y solicitudes.

### 7.8. `planning_warning_acknowledgements`

Conserva las advertencias concretas reconocidas dentro de una decisión.

El MVP la utiliza para el umbral semanal de horas programadas.

Campos:

- Identificación empresarial.
    
- `change_set_id`.
    
- `employee_id`.
    
- `warning_code`: inicialmente `WEEKLY_PLANNED_MINUTES_EXCEEDED`.
    
- `period_start`.
    
- `period_end_exclusive`.
    
- `timezone_id_snapshot`.
    
- `planned_minutes`.
    
- `threshold_minutes`.
    
- `warning_fingerprint`.
    
- `context_schema_version`.
    
- `context_data`: JSON limitado con referencias de origen necesarias.
    

Actor, motivo y momento se obtienen del conjunto de cambio. No se duplican en cada advertencia.

Restricciones:

- FK empresarial al conjunto de cambio y colaborador.
    
- Periodo válido.
    
- Minutos no negativos.
    
- Para esta advertencia, `planned_minutes > threshold_minutes`.
    
- Unicidad por conjunto, colaborador, código y periodo.
    
- JSON estructuralmente válido y verificado por la aplicación.
    
- Inmutabilidad.
    

#### Qué demuestra

Permite responder:

- Qué advertencia se aceptó.
    
- A quién afectaba.
    
- Qué periodo se evaluó.
    
- Qué minutos y umbral se mostraron.
    
- Con qué política.
    
- Quién confirmó y por qué.
    

Los valores se calculan en servidor. No se aceptan como hechos porque el cliente los envíe.

Una semana vacía utiliza `empty_schedule_confirmed` y el motivo del conjunto de cambio; no crea una advertencia ficticia de horas.

En intercambios entre semanas, las advertencias pertenecen a la decisión común y no se duplican por cada publicación resultante.

### 7.9. `schedule_publications`

Campos:

- Identificación empresarial.
    
- `schedule_id`.
    
- `publication_number`.
    
- `previous_publication_id`: opcional.
    
- `policy_version_id`.
    
- `timezone_id_snapshot`.
    
- `origin`: `INICIAL`, `REVISION`, `INTERCAMBIO`.
    
- `change_set_id`.
    
- `published_at`.
    

Restricciones:

- Único `(organization_id, schedule_id, publication_number)`.
    
- Publicación anterior dentro de la misma planificación.
    
- Política de la misma organización.
    
- FK empresarial al conjunto de cambio.
    
- Política coincidente con la utilizada en ese conjunto.
    
- Único `(organization_id, change_set_id, schedule_id)`.
    
- Inmutabilidad después de confirmar.
    

La coincidencia de política puede reforzarse mediante una FK compuesta hacia una clave candidata del conjunto de cambio.

Autor y motivo se obtienen del conjunto de cambio. Los campos anteriores `published_by_user_id` y `change_reason` dejan de ser fuentes almacenadas independientes en esta tabla.

La API puede seguir devolviéndolos mediante la consulta correspondiente.

`published_at` coincide con el momento de confirmación del conjunto.

Una decisión de intercambio puede producir una o dos publicaciones según las semanas afectadas.

### 7.10. `publication_assignments`

Campos:

- `organization_id`.
    
- `schedule_id`.
    
- `publication_id`.
    
- `assignment_id`.
    
- `assignment_revision_id`.
    

PK:

`(organization_id, publication_id, assignment_id)`

Restricciones:

- Publicación, identidad y revisión pertenecen a la misma planificación.
    
- La revisión corresponde exactamente a la identidad indicada.
    
- No se incluye dos veces una asignación.
    
- Contenido inmutable después de publicar.
    

Se declara una clave candidata adicional que incluya:

`(organization_id, publication_id, assignment_id, assignment_revision_id)`

Esa combinación permite referenciar el contenido exacto desde solicitudes de intercambio.

#### Publicación vacía

Una publicación con cero filas es válida cuando se cumplen las reglas de confirmación.

No se crea una asignación ficticia “sin turnos”.

No se confunde:

- `current_publication_id = NULL`: nunca se ha publicado esa planificación.
    
- Puntero informado con cero elementos: semana publicada vacía.
    

La publicación vacía requiere confirmación y motivo en su conjunto de cambio, verificados transaccionalmente.

### 7.11. Obtención del horario vigente

1. Localizar la planificación.
    
2. Leer su `current_publication_id`.
    
3. Consultar `publication_assignments`.
    
4. Recuperar las revisiones referenciadas.
    

No se toma “la revisión más reciente” de cada asignación.

Una revisión histórica puede existir sin pertenecer al horario vigente.

### 7.12. Vistas previas y huellas

No se crea una tabla de vistas previas persistentes en el MVP.

La vista previa devuelve una huella calculada por el servidor sobre una representación normalizada que incluye:

- Versión del borrador o solicitud.
    
- Publicación base.
    
- Contenido resultante relevante.
    
- Política vigente.
    
- Bloqueos.
    
- Advertencias.
    
- Solicitudes afectadas.
    
- Confirmaciones requeridas.
    

Al confirmar se recalcula dentro de la coordinación transaccional.

Si cambia materialmente, se rechaza la confirmación antigua.

El tiempo actual no se introduce como un valor que cambie la huella en cada lectura. Se reevalúan las condiciones temporales y se incluyen sus resultados relevantes.

La huella final se conserva en el conjunto de cambio, junto con las evidencias correspondientes.

No reemplaza permisos, versiones, restricciones ni validación.

### 7.13. Publicación sin cambios parciales

Orden lógico:

1. Autenticar y determinar organización y actor.
    
2. Adquirir coordinación de escritura.
    
3. Revalidar acceso vigente.
    
4. Comprobar o reservar la clave de idempotencia.
    
5. Comprobar versiones y publicación base.
    
6. Validar el resultado con la política actual.
    
7. Comprobar huella, advertencias, motivo y semana vacía.
    
8. Crear el conjunto de cambio.
    
9. Crear identidades y revisiones necesarias.
    
10. Reutilizar revisiones sin cambios.
    
11. Crear publicación y contenido.
    
12. Invalidar solicitudes afectadas y liberar reservas.
    
13. Actualizar el puntero vigente.
    
14. Completar referencias y cerrar el borrador.
    
15. Registrar reconocimientos, auditoría y outbox.
    
16. Guardar el resultado idempotente.
    
17. Confirmar.
    

Si falla un paso obligatorio, no se publica parcialmente.

Las referencias circulares se resuelven mediante inserciones y actualizaciones ordenadas dentro de la transacción. No se desactivan las FKs durante el uso normal.

## 8. DER: intercambios

```
erDiagram
    APP_USERS ||--o{ SWAP_REQUESTS : solicita_o_recibe
    PUBLICATION_ASSIGNMENTS ||--o{ SWAP_REQUESTS : origen_o_destino
    SWAP_REQUESTS ||--|{ SWAP_EVENTS : registra
    SWAP_REQUESTS ||--o{ SWAP_ASSIGNMENT_RESERVATIONS : reserva
    ASSIGNMENTS ||--o| SWAP_ASSIGNMENT_RESERVATIONS : limita
    SWAP_REQUESTS o|--o| PLANNING_CHANGE_SETS : origina_aprobacion
```

Cada solicitud referencia dos elementos publicados. El diagrama agrupa ambos papeles; el diccionario los separa.

La relación de aprobación es opcional: las solicitudes rechazadas, canceladas, vencidas o invalidadas no tienen un cambio aplicado.

### 8.1. `swap_requests`

Campos:

- Campos comunes empresariales.
    
- `requester_user_id`.
    
- `recipient_user_id`.
    
- `source_publication_id`.
    
- `source_assignment_id`.
    
- `source_revision_id`.
    
- `target_publication_id`.
    
- `target_assignment_id`.
    
- `target_revision_id`.
    
- `status`.
    
- `expires_at`.
    
- `resolved_at`: opcional.
    
- `resolution_code`: opcional.
    
- `resolution_note`: opcional y limitada.
    
- `applied_change_set_id`: opcional.
    

Estados:

- `PENDIENTE_COMPANERO`.
    
- `PENDIENTE_SUPERVISOR`.
    
- `APROBADA`.
    
- `RECHAZADA`.
    
- `CANCELADA`.
    
- `VENCIDA`.
    
- `INVALIDADA`.
    

Restricciones:

- Usuarios distintos.
    
- Asignaciones distintas.
    
- FKs empresariales de participantes.
    
- Cada lado referencia la combinación exacta de publicación, asignación y revisión.
    
- Participantes compatibles con los colaboradores de las revisiones.
    
- `expires_at` igual al inicio más temprano.
    
- Estados pendientes sin `resolved_at` ni cambio aplicado.
    
- Estados terminales con `resolved_at`.
    
- Solo `APROBADA` tiene `applied_change_set_id`.
    
- Cambio aplicado correspondiente a esa misma solicitud.
    

La pertenencia y compatibilidad de participantes se comprueba transaccionalmente.

No se duplican horarios ni empleados que ya constan en las revisiones inmutables.

#### Identidad histórica

Cambiar posteriormente el vínculo de una cuenta no reinterpreta quién participó: se conservan usuarios originales y colaboradores de las revisiones originales.

#### Cambio de una asignación no relacionada

La vigencia se comprueba comparando la revisión esperada con la que utiliza actualmente esa asignación.

Un cambio de publicación que conserve esa revisión no invalida automáticamente la solicitud.

Las demás reglas se revalidan de todos modos.

### 8.2. `swap_assignment_reservations`

Campos:

- `organization_id`.
    
- `assignment_id`.
    
- `swap_request_id`.
    
- `side`: `SOURCE` o `TARGET`.
    
- `created_at`.
    

Claves:

- PK `(organization_id, assignment_id)`.
    
- Único `(organization_id, swap_request_id, side)`.
    

FKs empresariales hacia asignación y solicitud.

Una solicitud pendiente debe tener exactamente dos reservas correspondientes a sus dos asignaciones.

Al terminar, ambas se eliminan en la misma transacción que registra el estado terminal.

La PK impide reservar una asignación desde otra solicitud, sin importar el lado.

La existencia de exactamente dos reservas correctas se garantiza mediante el servicio transaccional y pruebas; no solo mediante las claves.

Una reserva vencida se procesa antes de reutilizarla cuando corresponda. No se espera exclusivamente al proceso periódico ni se elimina sin actualizar la solicitud.

### 8.3. `swap_events`

Historial inmutable.

Campos:

- Identificación empresarial.
    
- `swap_request_id`.
    
- `sequence_number`.
    
- `previous_status`: opcional en creación.
    
- `new_status`.
    
- `actor_type`.
    
- `actor_user_id`: según actor.
    
- `actor_operator_id`: según actor.
    
- `system_actor_code`: según actor.
    
- `reason_code`: opcional.
    
- `reason_note`: opcional.
    
- `occurred_at`.
    
- `correlation_id`.
    

Restricciones:

- Único `(organization_id, swap_request_id, sequence_number)`.
    
- FK empresarial a solicitud.
    
- Actor coherente.
    
- Transición permitida.
    
- Estado actual y evento confirmados conjuntamente.
    
- Sin edición ordinaria.
    

El operador no puede aprobar o rechazar como responsable empresarial. La presencia de su campo no concede ese permiso.

Una actuación administrativa excepcional debe tener un procedimiento específico; no añade transiciones arbitrarias.

### 8.4. Aprobación atómica

Dentro de una transacción:

1. Comprobar acceso, idempotencia y coordinación.
    
2. Comprobar estado y vencimiento.
    
3. Comprobar independencia del aprobador.
    
4. Verificar participantes, reservas y revisiones vigentes.
    
5. Simular el resultado retirando las asignaciones originales.
    
6. Validar turnos y políticas.
    
7. Verificar advertencias reconocidas.
    
8. Crear el conjunto de cambio.
    
9. Crear dos revisiones con nuevos titulares.
    
10. Crear las publicaciones necesarias.
    
11. Actualizar punteros.
    
12. Marcar `APROBADA` y vincular el cambio.
    
13. Registrar evento y reconocimientos.
    
14. Liberar reservas.
    
15. Agregar auditoría, outbox y resultado idempotente.
    
16. Confirmar.
    

En una misma semana se crea una publicación; entre semanas se crean dos, vinculadas al mismo conjunto de cambio.

Los borradores basados en publicaciones anteriores quedan desactualizados por comparación, sin necesitar actualizaciones masivas de un indicador.

## 9. Auditoría, eventos y notificaciones

```
erDiagram
    ORGANIZATIONS ||--o{ AUDIT_ENTRIES : conserva
    ORGANIZATIONS ||--o{ OUTBOX_EVENTS : produce
    OUTBOX_EVENTS ||--o{ NOTIFICATIONS : genera
    APP_USERS ||--o{ NOTIFICATIONS : recibe
    APP_USERS ||--o{ API_OPERATIONS : ejecuta
    PLANNING_CHANGE_SETS o|--o| API_OPERATIONS : resultado_opcional
```

El vínculo opcional de una operación API con un conjunto de cambio no cubre todas las operaciones: crear un empleado, por ejemplo, no crea una decisión de planificación.

### 9.1. Convención de actores

Clases:

- `TENANT_USER`.
    
- `PLATFORM_OPERATOR`.
    
- `SYSTEM`.
    

Cada registro informa exactamente la identidad correspondiente:

- Usuario empresarial.
    
- Operador.
    
- Código del proceso.
    

Los demás campos de actor deben ser nulos.

No se representa un proceso interno con una cuenta humana ficticia.

### 9.2. `audit_entries`

Campos:

- Identificación empresarial.
    
- `actor_type`.
    
- `actor_user_id`: opcional.
    
- `actor_operator_id`: opcional.
    
- `system_actor_code`: opcional.
    
- `action_code`.
    
- `entity_type`.
    
- `entity_id`.
    
- `before_data`: opcional.
    
- `after_data`: opcional.
    
- `reason`: opcional.
    
- `occurred_at`.
    
- `correlation_id`.
    

Restricciones:

- Organización obligatoria para auditoría empresarial.
    
- FK empresarial del usuario.
    
- FK global del operador.
    
- Coherencia de actor.
    
- JSON válido y limitado.
    
- Sin contraseñas, hashes de contraseña, tokens ni secretos.
    

La referencia `entity_type`/`entity_id` es polimórfica. No constituye una FK a todas las tablas.

La aplicación comprueba el recurso y captura el contenido permitido dentro de la transacción.

Los intentos de acceso sin organización conocida se registran en un canal de seguridad separado.

### 9.3. `outbox_events`

Campos:

- Identificación empresarial.
    
- `event_type`.
    
- `aggregate_type`.
    
- `aggregate_id`.
    
- `deduplication_key`.
    
- `payload`.
    
- `status`: `PENDING`, `PROCESSING`, `DONE`, `FAILED`.
    
- `attempt_count`.
    
- `available_at`.
    
- `locked_until`: opcional.
    
- `lock_owner`: opcional.
    
- `processed_at`: opcional.
    
- `last_error_code`: opcional.
    

Restricciones:

- Clave de deduplicación única por organización.
    
- JSON válido y limitado.
    
- Intentos no negativos.
    
- Coherencia de estado y campos de procesamiento.
    

El contenido incluye únicamente lo necesario para producir avisos.

Las referencias dentro del JSON se validan; el formato JSON no garantiza pertenencia empresarial.

El procesamiento utiliza:

- Reclamo atómico.
    
- Reserva temporal.
    
- Recuperación tras interrupción.
    
- Reintentos controlados.
    
- Tratamiento de fallos persistentes.
    

Un evento no queda indefinidamente atrapado en `PROCESSING`.

### 9.4. `notifications`

Campos:

- Identificación empresarial.
    
- `outbox_event_id`.
    
- `recipient_user_id`.
    
- `notification_type`.
    
- `resource_type`.
    
- `resource_id`.
    
- `title`.
    
- `message`.
    
- `read_at`: opcional.
    

Restricciones:

- FKs empresariales al evento y destinatario.
    
- Único `(organization_id, outbox_event_id, recipient_user_id, notification_type)`.
    
- Texto limitado.
    
- Sin secretos ni motivos privados innecesarios.
    

El destinatario consulta solo sus avisos.

Abrir el recurso requiere comprobar permisos actuales.

No se incorporan chat, SMS ni estados de entrega de correo.

## 10. Sesiones, idempotencia e infraestructura

### 10.1. Spring Session JDBC

Se utilizarán las tablas correspondientes a la versión elegida, normalmente:

- `SPRING_SESSION`.
    
- `SPRING_SESSION_ATTRIBUTES`.
    

El esquema y las consultas se tomarán del soporte correspondiente a SQL Server y se incorporarán mediante migraciones controladas. No se añadirá `organization_id` arbitrariamente a tablas administradas por la biblioteca. [Referencia: Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html).

El principal utiliza una identidad inequívoca, como el identificador de `app_users`, no un correo repetible entre organizaciones.

La sesión puede conservar:

- Usuario.
    
- Organización.
    
- Versión de autenticación.
    
- Información técnica necesaria para expiración y seguridad.
    

El estado y los permisos efectivos siguen comprobándose según la arquitectura.

Pueden existir sesiones anónimas para CSRF; no representan usuarios empresariales autenticados.

La limpieza elimina sesiones vencidas. No son historial de negocio.

### 10.2. Historial de migraciones

Flyway conserva su historial técnico.

No se modifica manualmente para ocultar errores o cambios.

La creación de tablas de sesión se coordina con las migraciones; no se habilitan dos mecanismos competidores que intenten crear el mismo esquema.

### 10.3. `api_operations`

Registro técnico para deduplicar comandos y recuperar resultados.

Campos:

- Identificación empresarial.
    
- `actor_user_id`.
    
- `idempotency_key`: UUID recibido.
    
- `request_method`.
    
- `request_path`: representación canónica.
    
- `request_fingerprint`.
    
- `request_schema_version`.
    
- `status`: `IN_PROGRESS` o `COMPLETED`.
    
- `outcome`: opcional durante ejecución; `SUCCEEDED` o `REJECTED` al completar.
    
- `http_status`: opcional durante ejecución.
    
- `response_content_type`: opcional.
    
- `response_body`: opcional, limitado y sin secretos.
    
- `response_location`: opcional.
    
- `planning_change_set_id`: opcional.
    
- `completed_at`: opcional.
    
- `expires_at`.
    

Restricciones:

- Único `(organization_id, actor_user_id, idempotency_key)`.
    
- FK empresarial al actor.
    
- FK empresarial al conjunto de cambio cuando exista.
    
- Un conjunto de cambio se vincula como máximo al resultado de un comando de este registro.
    
- Expiración posterior a creación.
    
- Estado completo con resultado y fecha coherentes.
    
- No almacenar cabeceras de autenticación, cookies ni tokens.
    

#### Huella de petición

Incluye:

- Método.
    
- Ruta y parámetros significativos normalizados.
    
- Cuerpo normalizado.
    
- Versiones esperadas.
    
- Confirmaciones y motivos que formen parte de la intención.
    

No incluye secretos de sesión ni CSRF.

No se guarda por defecto el cuerpo original.

Misma clave y misma huella devuelve el resultado registrado.

Misma clave y huella diferente produce conflicto.

#### Protocolo transaccional

La fila se reserva dentro de la misma transacción que el comando.

`IN_PROGRESS` es un estado interno de esa transacción, no un trabajo duradero confirmado por separado.

Una ejecución confirmada deja `COMPLETED`. Un fallo con reversión no deja un cambio de negocio sin resultado idempotente.

Los rechazos de negocio conocidos pueden confirmarse como `REJECTED` cuando no se realizó el cambio solicitado. Si el rechazo implica materializar un vencimiento o invalidación, ese efecto y su auditoría deben quedar explícitos y ser atómicos.

Errores de autenticación y fallos técnicos no se convierten en respuestas persistidas con secretos o estados parciales.

Si existe una fila `IN_PROGRESS` confirmada y abandonada, se considera una anomalía del protocolo, no una instrucción para repetir el cambio.

#### Operaciones concurrentes

Una segunda petición se coordina con la primera. No ejecuta el comando por no encontrar todavía una fila visible.

La clave única y el protocolo transaccional deben probarse juntos.

Los tiempos de espera o conflictos se devuelven sin afirmar que el primer comando no ocurrió.

#### Consulta de resultados

Se requiere:

- Mismo actor.
    
- Misma organización.
    
- Acceso vigente.
    
- Permiso actual sobre el resultado.
    

La respuesta representa el resultado original. El recurso puede haber cambiado después.

No se utiliza esta tabla como historial empresarial permanente.

#### Retención y limpieza

Ventana inicial propuesta: 24 horas.

La limpieza:

- Se coordina con accesos y reintentos.
    
- No elimina registros antes de completar esa ventana.
    
- No elimina conjuntos de cambio ni publicaciones.
    
- No promete deduplicación después de retirar el registro.
    

Un resultado ausente no prueba que una petición nunca ocurrió.

Fuera de la ventana se revisa el estado de negocio; no se reenvía automáticamente una intención antigua.

#### Exclusiones

No se almacenan aquí respuestas con:

- Contraseñas.
    
- Tokens de recuperación.
    
- Enlaces secretos de alta.
    
- Cookies de sesión.
    

Las operaciones de credenciales conservan sus procedimientos específicos.

## 11. Restricciones: base de datos frente a aplicación

|   |   |
|---|---|
|Invariante|Mecanismo principal|
|Código de organización único|Restricción única|
|Correo único por organización|Restricción única|
|Una cuenta por colaborador vinculado|Índice único filtrado|
|Una sede activa|Índice único filtrado|
|Un borrador abierto|Índice único filtrado|
|Relaciones empresariales|FKs compuestas|
|Intervalo válido|`CHECK`|
|Estados y combinaciones nulas|`CHECK` y nulabilidad|
|Una línea por asignación existente en borrador|Índice único filtrado|
|Publicación dentro de su planificación|FK compuesta|
|Revisión correspondiente a identidad|FK compuesta|
|Una reserva por asignación|PK|
|Dos reservas correctas|Servicio transaccional|
|Último administrador|Servicio transaccional|
|Ausencia de solapamientos publicados|Validación transaccional|
|Descanso entre periodos|Motor de reglas y coordinación|
|Independencia del aprobador|Autorización y validación|
|Semana vacía confirmada|Validación transaccional|
|Advertencias exactas reconocidas|Recálculo y comparación|
|Motivo obligatorio según contexto|Validación transaccional|
|Clave idempotente única por actor|Restricción única|
|Cambio y resultado idempotente juntos|Misma transacción|
|Auditoría y outbox junto al cambio|Misma transacción|
|Avisos sin duplicación visible|Clave única y procesamiento idempotente|

SQL Server permite índices únicos filtrados para limitar la unicidad a filas que cumplen una condición, como vínculos no nulos. [Referencia: Microsoft, unicidad filtrada](https://learn.microsoft.com/en-us/sql/relational-databases/tables/create-unique-constraints?view=sql-server-ver17).

No se utiliza un `CHECK` ordinario como sustituto de reglas que requieren consultar múltiples filas o tablas.

### 11.1. Claves candidatas de planificación

Además de las claves empresariales:

|   |   |
|---|---|
|Tabla|Combinación que debe poder referenciarse|
|`schedules`|Organización y planificación|
|`assignments`|Organización, planificación e identidad|
|`assignment_revisions`|Organización, planificación, identidad y revisión|
|`schedule_drafts`|Organización, planificación y borrador|
|`schedule_publications`|Organización, planificación y publicación|
|`publication_assignments`|Organización, publicación, identidad y revisión|
|`planning_change_sets`|Organización, cambio y política|

Las claves candidatas deben declararse antes de las FKs que las utilizan.

Las columnas repetidas para integridad no pueden editarse independientemente de sus relaciones.

### 11.2. Coordinación común

Las rutas que pueden alterar invariantes deben utilizar el mismo protocolo empresarial:

- Publicación.
    
- Intercambio.
    
- Cambio de política.
    
- Indisponibilidad.
    
- Cambio de puesto.
    
- Desactivación o cambio de vínculo.
    
- Suspensión y reactivación relevantes.
    
- Cambio permitido de zona.
    
- Limpieza de idempotencia cuando interfiera con reintentos.
    

El bloqueo definido en arquitectura complementa las versiones optimistas; no las reemplaza.

### 11.3. Inmutabilidad

Se aplica mediante servicios restringidos, permisos y pruebas.

“Inmutable” no significa que un administrador técnico sea físicamente incapaz de alterar la base. Significa que la aplicación ordinaria no actualiza ni elimina el historial y que cualquier intervención privilegiada requiere procedimiento y evidencia.

## 12. Índices iniciales propuestos

|   |   |
|---|---|
|Tabla|Acceso prioritario|
|`organizations`|Código normalizado|
|`app_users`|Organización y correo; vínculo no nulo|
|`employees`|Organización, estado y puesto|
|`employee_unavailabilities`|Organización, colaborador e inicio|
|`planning_policy_versions`|Organización y número|
|`schedules`|Organización, sede y semana|
|`schedule_drafts`|Borrador abierto por planificación|
|`draft_assignments`|Borrador y colaborador|
|`assignment_revisions`|Asignación y revisión; colaborador e inicio|
|`publication_assignments`|Publicación e identidad|
|`planning_change_sets`|Organización y fecha; referencias de origen|
|`planning_warning_acknowledgements`|Cambio; colaborador y periodo|
|`swap_requests`|Organización, estado y vencimiento; participantes|
|`swap_assignment_reservations`|Asignación; solicitud|
|`swap_events`|Solicitud y secuencia|
|`audit_entries`|Organización y fecha|
|`outbox_events`|Estado y disponibilidad|
|`notifications`|Destinatario y fecha|
|`api_operations`|Organización, actor y clave; expiración|
|`password_reset_tokens`|Hash y vencimiento|

Son candidatos. Se comprobarán con consultas y planes reales.

Consultar revisiones por empleado no convierte todas sus revisiones históricas en turnos vigentes: debe seleccionarse la publicación pertinente.

La elección de índices agrupados y orden de identificadores queda para el diseño físico; no se asume que cualquier PK UUID sea automáticamente una buena organización del almacenamiento.

## 13. Normalización y duplicaciones deliberadas

### 13.1. Separaciones mantenidas

- Usuario y colaborador.
    
- Puesto y colaborador.
    
- Plantilla y asignación.
    
- Línea de borrador e identidad publicada.
    
- Identidad y revisión.
    
- Publicación y contenido.
    
- Cambio confirmado y advertencias.
    
- Solicitud y eventos.
    
- Outbox y aviso.
    
- Resultado idempotente e historial duradero.
    

No se almacena un total mutable de horas como fuente principal.

### 13.2. Duplicaciones justificadas

- `organization_id` para integridad.
    
- Identificadores de planificación para impedir mezclas de semanas.
    
- Nombres históricos en revisiones.
    
- Valores numéricos de advertencias como evidencia del reconocimiento.
    
- Estado actual de solicitud junto con historial de transiciones.
    
- Metadatos de creación de política coherentes con su activación.
    
- Política en publicación, vinculada a la del conjunto de cambio.
    

Estas duplicaciones tienen reglas de consistencia explícitas.

### 13.3. Fuentes únicas

- Vínculo de cuenta: `app_users.employee_id`.
    
- Publicación vigente: `schedules.current_publication_id`.
    
- Política vigente: `organizations.current_policy_version_id`.
    
- Horario publicado: contenido de `publication_assignments`.
    
- Motivo y actor de una publicación: conjunto de cambio.
    
- Contenido de un turno histórico: revisión referenciada.
    

No se mantienen copias editables competidoras.

### 13.4. Reportes y huella de fuentes

Los reportes se calculan sobre una lectura coherente de publicaciones y revisiones.

`sourceFingerprint` se deriva de las fuentes relevantes y parámetros normalizados. No requiere una tabla de reportes ni una columna global mutable.

Al exportar se verifica nuevamente.

El mecanismo de lectura consistente y comparación deberá implementarse y probarse contra SQL Server; leer tablas por separado sin coordinación no garantiza un mismo estado.

## 14. Ejemplos de evolución

### 14.1. Primera publicación

- Planificación S.
    
- Borrador D1 con dos líneas nuevas.
    
- Cambio C1.
    
- Publicación P1.
    
- Asignación A1 → revisión A1-R1.
    
- Asignación A2 → revisión A2-R1.
    
- `current_publication_id = P1`.
    

Las identidades A1 y A2 nacen al publicar. Las líneas de D1 conservan la correspondencia al cerrarse.

### 14.2. Revisión de un turno

Se modifica A1:

- Cambio C2 con motivo.
    
- Publicación P2.
    
- A1 → A1-R2.
    
- A2 → A2-R1.
    

P1 sigue intacta.

Una solicitud sobre A1 queda invalidada. Una sobre A2 puede conservarse, sujeta a revalidación.

### 14.3. Intercambio aprobado

Se intercambian titulares de A1 y A2:

- Solicitud R1.
    
- Cambio C3 vinculado a R1.
    
- Publicación P3.
    
- A1 → A1-R3.
    
- A2 → A2-R2.
    
- Reservas eliminadas.
    
- Evento y auditoría registrados.
    

Si aparecen advertencias, sus reconocimientos apuntan a C3.

### 14.4. Borrador previo

Un borrador basado en P2 queda desactualizado cuando la vigente pasa a P3.

No necesita una actualización del campo `status` para detectar esa condición.

### 14.5. Semana vacía

Para otra semana sin turnos iniciados:

- Cambio C4 con motivo.
    
- `empty_schedule_confirmed = true`.
    
- Publicación P4.
    
- Cero elementos en `publication_assignments`.
    
- Puntero vigente informado.
    

No es lo mismo que no haber publicado.

### 14.6. Respuesta de red perdida

La publicación P4 y su resultado idempotente se confirmaron, pero el navegador no recibió respuesta.

La misma petición con la misma clave recupera el resultado. No crea P5.

Eliminar posteriormente el registro técnico por retención no elimina C4 ni P4.

## 15. Conservación, limpieza y recuperación

### 15.1. Historia operativa

Conservar conforme a política:

- Publicaciones y contenido.
    
- Revisiones referenciadas.
    
- Conjuntos de cambio.
    
- Advertencias reconocidas.
    
- Solicitudes y eventos.
    
- Auditoría.
    

### 15.2. Datos técnicos o temporales

Limpiar de forma controlada:

- Sesiones vencidas.
    
- Tokens consumidos o vencidos.
    
- Operaciones idempotentes fuera de ventana.
    
- Reservas de solicitudes terminales.
    
- Outbox y avisos según dependencias.
    
- Borradores descartados según retención.
    

No se elimina un outbox todavía referenciado por avisos conservados sin resolver primero esa relación mediante una política explícita.

### 15.3. Cierre empresarial

No se ejecuta una cascada indiscriminada desde `organizations`.

El procedimiento contempla:

- Suspensión.
    
- Exportación acordada.
    
- Retención.
    
- Anonimización o eliminación.
    
- Instantáneas.
    
- Advertencias.
    
- Copias de seguridad.
    
- Evidencias administrativas.
    

Inmutabilidad funcional no significa retención ilimitada.

### 15.4. Restauración

Una restauración puede recuperar:

- Sesiones antiguas.
    
- Enlaces de recuperación.
    
- Cuentas previamente revocadas.
    
- Reservas y solicitudes antiguas.
    
- Avisos pendientes.
    
- Registros idempotentes anteriores.
    

Antes de reabrir se aplica el procedimiento del documento 12.

La ausencia de una operación posterior al respaldo no autoriza a reproducirla automáticamente. Puede haber tenido efectos ya conocidos por usuarios.

## 16. Pruebas de integridad obligatorias

### 16.1. Aislamiento

- Usuario vinculado a empleado ajeno: rechazo.
    
- Revisión con empleado ajeno: rechazo.
    
- Publicación con política ajena: rechazo.
    
- Aviso con destinatario ajeno: rechazo.
    
- Puntero a publicación de otra planificación: rechazo.
    
- Advertencia con colaborador ajeno: rechazo.
    
- Operación idempotente consultada por otro actor: acceso rechazado.
    

### 16.2. Unicidad

- Dos cuentas para un colaborador: rechazo.
    
- Varias cuentas sin colaborador: permitido.
    
- Dos sedes activas: rechazo.
    
- Dos borradores abiertos: rechazo.
    
- Varias líneas nuevas sin asignación publicada: permitido.
    
- Duplicar una asignación existente dentro del borrador: rechazo.
    
- Reservar la misma asignación desde lados distintos: rechazo.
    
- Reutilizar clave idempotente del mismo actor con otro contenido: conflicto.
    

### 16.3. Historial

- Editar borrador no altera publicaciones.
    
- Cambiar plantilla no altera revisiones.
    
- Renombrar empleado no cambia instantáneas.
    
- Retirar turno no elimina su historia.
    
- Nueva publicación mantiene su versión anterior.
    
- Motivo de publicación se recupera desde la decisión correcta.
    
- Advertencia conserva valores aceptados aunque cambie la política.
    

### 16.4. Publicación y políticas

- Guardar conflicto operativo es posible.
    
- Publicarlo sigue bloqueado.
    
- Semana vacía sin confirmación: rechazo.
    
- Semana vacía con turno iniciado que se retiraría: rechazo.
    
- Activación incompatible con descansos futuros: rechazo.
    
- Cambio de advertencias invalida confirmación anterior.
    
- Política y conjunto de cambio no pueden quedar desalineados.
    
- Cambiar zona no traslada silenciosamente asignaciones.
    

### 16.5. Intercambios

- Referencias originales conservadas.
    
- Cambio ajeno no invalida innecesariamente.
    
- Cambio del turno solicitado sí invalida.
    
- Aprobación propia rechazada.
    
- Rechazo final por participante rechazado.
    
- Aprobación crea ambas revisiones o ninguna.
    
- Publicaciones entre semanas comparten una decisión válida.
    
- Estado terminal libera dos reservas.
    
- Repetir aprobación no vuelve a permutar turnos.
    

### 16.6. Operación técnica

- Cambio, auditoría, outbox y resultado idempotente se confirman juntos.
    
- Fallo anterior al commit no deja datos parciales.
    
- Pérdida de respuesta posterior al commit permite recuperar resultado.
    
- Reintento de outbox no duplica avisos.
    
- Token no puede consumirse dos veces.
    
- Migraciones crean todas las claves candidatas y FKs.
    
- Retención idempotente no elimina historia.
    
- Restauración conserva relaciones y exige revisión de seguridad.
    

## 17. Orden orientativo de implementación

1. Roles e identidades administrativas.
    
2. Organizaciones, sedes y puestos.
    
3. Colaboradores y usuarios.
    
4. Recuperación y sesiones.
    
5. Políticas y plantillas.
    
6. Auditoría, outbox e idempotencia.
    
7. Planificaciones y borradores.
    
8. Identidades y revisiones.
    
9. Conjuntos de cambio y reconocimientos.
    
10. Publicaciones y contenido.
    
11. Solicitudes, eventos y reservas.
    
12. FKs que dependen de tablas creadas en pasos posteriores.
    
13. Avisos.
    
14. Índices y pruebas integradas.
    

Las migraciones se organizarán por incrementos.

Cuando existan referencias circulares, se crean las tablas y después las FKs dentro de la preparación del esquema, antes de habilitar el flujo afectado.

No se deshabilitan restricciones en producción para permitir operaciones que el diseño no resolvió.

## 18. Entidades excluidas

No se crean:

- Marcaciones.
    
- Justificaciones de asistencia.
    
- Liquidaciones.
    
- Nóminas.
    
- Pagos.
    
- Facturas automáticas.
    
- Cupones.
    
- Suscripciones autoservicio.
    
- Dispositivos biométricos.
    
- Chat.
    
- Motor arbitrario de reglas.
    
- Modelos de inteligencia artificial.
    
- Historial ilimitado de peticiones HTTP.
    
- Almacén genérico de secretos dentro de tablas de negocio.
    

Las tres entidades nuevas soportan reglas ya definidas; no añaden módulos comerciales.

## 19. Pendientes de aprobación y concreción

- Aprobar esta revisión frente a la nota 04.
    
- Confirmar normalización de códigos y correos.
    
- Definir longitudes y tamaños máximos.
    
- Precisar esquema de `context_data`.
    
- Definir normalización y versiones de huellas.
    
- Completar políticas de retención.
    
- Seleccionar versión compatible de Spring Session.
    
- Validar consultas de reportes y su consistencia.
    
- Revisar tiempos de espera y protocolo de idempotencia.
    
- Convertir claves candidatas en DDL.
    
- Comprobar índices y crecimiento.
    
- Ensayar restauración.
    

Estos pendientes no significan que deban construirse nuevas funciones. Son detalles de implementación y verificación del alcance existente.

## 20. Sincronización con otros documentos

|   |   |
|---|---|
|Documento|Actualización necesaria|
|04|Mantener referencia a sus reglas consolidadas|
|05|Criterios de persistencia e integridad|
|06|Incorporar registro idempotente y decisiones persistentes|
|08 y 09|Mostrar motivos, advertencias y semana vacía|
|10|Diferenciar línea de borrador y asignación; contratos de reconocimientos e idempotencia|
|11|Tareas de las tres entidades nuevas y pruebas|
|12|Fallos transaccionales, retención y restauración|

### Ajustes concretos de API

- `draftItemId` identifica una línea.
    
- `assignmentId` puede ser nulo antes de publicar.
    
- Autor y motivo de publicación se obtienen desde el conjunto de cambio.
    
- Aprobar con advertencias requiere reconocimientos concretos.
    
- Publicar vacío requiere confirmación y motivo.
    
- Recuperar una operación no equivale a consultar el estado actual del recurso.
    

Los nombres de propiedades definitivos se fijarán en OpenAPI.

## 21. Criterio de cierre

El modelo estará aprobado como base cuando:

- Cada requisito de persistencia esté representado.
    
- Se distingan borradores, datos vigentes e historial.
    
- Las relaciones impidan asociaciones empresariales cruzadas.
    
- Exista una sola fuente del horario vigente.
    
- Motivos y advertencias queden vinculados a decisiones concretas.
    
- Los intercambios conserven referencias y atomicidad.
    
- La idempotencia no dependa de memoria ni sustituya el historial.
    
- Las sesiones y secretos tengan tratamiento separado.
    
- La conservación contemple instantáneas y dependencias.
    
- El DDL sea implementable sin relaciones ambiguas.
    
- Contratos y pruebas coincidan con el modelo.
    

**Situación actual:** diseño lógico consolidado. Las migraciones, los diagramas en Obsidian, las consultas y las garantías de concurrencia todavía deben verificarse durante la implementación.