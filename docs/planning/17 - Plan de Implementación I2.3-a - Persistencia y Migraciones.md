# 17 — Plan de Implementación I2.3-a: Persistencia y Migraciones

> [!IMPORTANT]
> **Documento de diseño de referencia:**  
> Este plan implementa la fase de persistencia relacional especificada en [docs/planning/16 - Diseño Técnico I2.3 - Política Operativa Inicial.md](file:///d:/Proyectos/TurnoShift/docs/planning/16%20-%20Dise%C3%B1o%20T%C3%A9cnico%20I2.3%20-%20Pol%C3%ADtica%20Operativa%20Inicial.md).

---

## 1. Objetivo, Límites y Alcance

### 1.1. Objetivo
Instalar las estructuras físicas de base de datos para el versionado inmutable y append-only de políticas operativas de planificación, resolver la referencia circular histórica de `organizations.current_policy_version_id` mediante una clave foránea compuesta empresarial que garantice aislamiento multi-tenant estricto, e incorporar la suite de pruebas de integración de persistencia y actualización progresiva.

### 1.2. Límites Estrictos de I2.3-a
- **Dentro de alcance:**
  - Creación de la migración `V15__planning_policy_versions.sql`.
  - Creación de la migración `V16__organizations_policy_fk.sql`.
  - Creación de la suite de pruebas de integración `PolicyPersistenceIT.java`.
  - Actualización exclusiva de las suites de prueba históricas afectadas por el avance de versiones (`SchemaUpgradeV3ToLatestIT.java` y `SpringBootStartupIT.java`).
- **Fuera de alcance:**
  - Código de dominio Java (`PolicyVersion`, `PolicyThresholds`, etc.).
  - Adaptador de repositorio JDBC (`JdbcPolicyRepository`).
  - Casos de uso de aplicación (`GetCurrentPolicyUseCase`, etc.).
  - Controladores HTTP, DTOs y serialización de comandos.
  - Modificación del contrato OpenAPI (`docs/api/openapi.yaml`, diferido a I2.3-d).
  - Frontend o interfaz de usuario.
  - No se modifica ninguna migración previa (`V1` a `V14`).
  - No se modifica `SchemaUpgradeV11ToV12IT.java` (su target histórico está fijado en V12).

---

## 2. Inventario Exacto de Archivos

### 2.1. Archivos a Crear
1. `backend/src/main/resources/db/migration/V15__planning_policy_versions.sql`: DDL de la tabla `planning_policy_versions` y sus restricciones base.
2. `backend/src/main/resources/db/migration/V16__organizations_policy_fk.sql`: preflight fail-closed e instalación de la FK compuesta `fk_organizations_current_policy`.
3. `backend/src/test/java/com/turnoshift/persistence/PolicyPersistenceIT.java`: suite de pruebas de integración de persistencia sobre SQL Server real (Testcontainers).

### 2.2. Archivos a Modificar
1. `backend/src/test/java/com/turnoshift/persistence/SchemaUpgradeV3ToLatestIT.java`:
   - Actualizar la aserción de versiones aplicadas en `historialContieneV1aV11()` para esperar `V1` a `V16` (añadiendo `"15"` y `"16"` a `containsExactly`).
2. `backend/src/test/java/com/turnoshift/health/SpringBootStartupIT.java`:
   - Actualizar la aserción de versiones en `flywayAutoConfigAplicoMigraciones()` para esperar `V1` a `V16` (añadiendo `"15"` y `"16"`).
   - Añadir `"planning_policy_versions"` a la lista de tablas verificadas en `tablasDeProduccionExisten()`.

---

## 3. Tareas TDD Secuenciales

- [ ] **Tarea 1: Prueba roja inicial de esquema en `PolicyPersistenceIT`**
  - Crear el esqueleto de `PolicyPersistenceIT.java` con una prueba inicial que consulte en `INFORMATION_SCHEMA.TABLES` y en `sys.foreign_keys`, afirmando positivamente que la tabla `planning_policy_versions` y la restricción `fk_organizations_current_policy` existen.
  - Ejecutar la prueba y comprobar que falla (rojo) debido a la ausencia física real de ambas estructuras antes de crear las migraciones.
  - Tras implementar `V15` y `V16`, esta misma prueba pasará a verde sin requerir cambios en sus aserciones.
- [ ] **Tarea 2: Crear migración `V15__planning_policy_versions.sql`**
  - Implementar la definición DDL de `planning_policy_versions` según la especificación técnica §4.
  - Verificar que la tabla y sus restricciones se crean correctamente.
- [ ] **Tarea 3: Crear migración `V16__organizations_policy_fk.sql`**
  - Implementar la comprobación fail-closed de punteros legados no nulos, el retiro de la guarda transitoria `chk_organizations_policy_version_null` de V3 y la adición de la FK compuesta según la especificación técnica §5.
  - Verificar que la FK compuesta queda activa y que la guarda temporal queda eliminada.
- [ ] **Tarea 4: Implementar casos de prueba completos en `PolicyPersistenceIT`**
  - Desarrollar las pruebas de inserción nominal, aislamiento multi-tenant, autoría, unicidad de versión, límites de rangos, motivo y upgrade progresivo V14→V16.
- [ ] **Tarea 5: Actualizar suites históricas afectadas**
  - Actualizar expectativas de versión latest en `SchemaUpgradeV3ToLatestIT.java` y `SpringBootStartupIT.java`.
- [ ] **Tarea 6: Verificación focalizada final**
  - Ejecutar exclusivamente la suite focalizada:
    `.\mvnw.cmd test "-Dtest=PolicyPersistenceIT,SchemaUpgradeV3ToLatestIT,SpringBootStartupIT" -B`

---

## 4. Especificación SQL Exacta para `V15__planning_policy_versions.sql`

```sql
-- =============================================================================
-- V15: planning_policy_versions
-- =============================================================================
-- Almacén inmutable y append-only de versiones de políticas operativas.
-- Define descanso mínimo obligatorio y umbral de aviso semanal para el MVP.
-- =============================================================================

CREATE TABLE planning_policy_versions (
    -- Identificación técnica y empresarial
    id                          UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),
    organization_id             UNIQUEIDENTIFIER    NOT NULL,
    version_number              INT                 NOT NULL,

    -- Parámetros operativos iniciales
    minimum_rest_minutes        INT                 NOT NULL,
    weekly_warning_minutes      INT                 NOT NULL,

    -- Vigencia y auditoría
    effective_at                DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    created_by_user_id          UNIQUEIDENTIFIER    NOT NULL,
    change_reason               NVARCHAR(500)       NOT NULL,
    created_at                  DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    -- Clave primaria simple
    CONSTRAINT pk_planning_policy_versions
        PRIMARY KEY (id),

    -- Clave candidata empresarial: requerida para FKs compuestas desde organizations
    CONSTRAINT uq_policy_versions_org_id
        UNIQUE (organization_id, id),

    -- Unicidad de secuencia de versión por empresa (segunda barrera física)
    CONSTRAINT uq_policy_versions_org_version
        UNIQUE (organization_id, version_number),

    -- FK simple a organizations (sin borrado en cascada)
    CONSTRAINT fk_policy_versions_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- FK compuesta a app_users: garantiza que el autor pertenece a la misma organización
    CONSTRAINT fk_policy_versions_author
        FOREIGN KEY (organization_id, created_by_user_id)
        REFERENCES app_users (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- Número de versión estrictamente positivo
    CONSTRAINT chk_policy_version_number_positive
        CHECK (version_number > 0),

    -- Límites operativos validados por CHECK: 1 a 1440 min (1 min a 24 horas)
    CONSTRAINT chk_policy_minimum_rest_range
        CHECK (minimum_rest_minutes >= 1 AND minimum_rest_minutes <= 1440),

    -- Límites operativos validados por CHECK: 1 a 10080 min (1 min a 7 días)
    CONSTRAINT chk_policy_weekly_warning_range
        CHECK (weekly_warning_minutes >= 1 AND weekly_warning_minutes <= 10080),

    -- Protección mínima contra cadena vacía o compuesta solo por espacios ASCII
    CONSTRAINT chk_policy_reason_not_empty
        CHECK (LEN(LTRIM(RTRIM(change_reason))) > 0)
);
```

---

## 5. Especificación SQL Exacta para `V16__organizations_policy_fk.sql`

```sql
-- =============================================================================
-- V16: organizations_policy_fk
-- =============================================================================
-- Resuelve la referencia circular diferida en DEC-08-DEFER-FK-POLICY.
-- 1. Preflight fail-closed de integridad de punteros legados no nulos.
-- 2. Desmantela la restricción temporal chk_organizations_policy_version_null (V3).
-- 3. Instala la FK compuesta:
--      organizations(id, current_policy_version_id)
--      -> planning_policy_versions(organization_id, id)
-- =============================================================================

-- 1. Preflight fail-closed: si existe algún puntero legado corrupto o cruzado,
--    la migración debe abortar de inmediato sin modificar ni eliminar datos.
IF EXISTS (
    SELECT 1
    FROM organizations o
    WHERE o.current_policy_version_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM planning_policy_versions p
          WHERE p.organization_id = o.id
            AND p.id = o.current_policy_version_id
      )
)
BEGIN
    THROW 50000, 'Migración V16 abortada: existen organizaciones con current_policy_version_id que no corresponden a una versión válida de la misma organización.', 1;
END;

-- 2. Desmantelar la restricción temporal que obligaba a current_policy_version_id a ser NULL (instalada en V3)
ALTER TABLE organizations
    DROP CONSTRAINT chk_organizations_policy_version_null;

-- 3. Instalación de la clave foránea compuesta
ALTER TABLE organizations
ADD CONSTRAINT fk_organizations_current_policy
    FOREIGN KEY (id, current_policy_version_id)
    REFERENCES planning_policy_versions (organization_id, id)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION;
```

---

## 6. Diseño Completo de `PolicyPersistenceIT`

Ubicación: `backend/src/test/java/com/turnoshift/persistence/PolicyPersistenceIT.java`  
Anotaciones: `@SpringBootTest`, `@Testcontainers`, `@Transactional`, `@Rollback`.

### 6.1. Orden de Inserción Respetando Claves Foráneas
Para los datos de prueba, se debe respetar el orden estricto de dependencias relacionales:
1. `organizations`
2. `sites` (FK compuesta a `organizations`)
3. `positions` (FK compuesta a `organizations`)
4. Catálogo `roles` (ya provisto por V4: `ADMINISTRADOR`, `SUPERVISOR`, `COLABORADOR`)
5. `app_users` (FKs a `organizations` y `roles`)
6. `planning_policy_versions` (FKs a `organizations` y `app_users`)
7. Actualización de `organizations.current_policy_version_id` (FK compuesta a `planning_policy_versions`)

### 6.2. Casos de Prueba Requeridos

1. **`aplicacionLimpiaDeMigraciones`:**
   - Verifica que `flyway_schema_history` registra V1 a V16 aplicadas exitosamente en orden de `installed_rank`.
2. **`existenciaDeEstructurasRelacionales`:**
   - Comprueba existencia de la tabla `planning_policy_versions` en `INFORMATION_SCHEMA.TABLES`.
   - Comprueba existencia de las restricciones `pk_planning_policy_versions`, `uq_policy_versions_org_id`, `uq_policy_versions_org_version`, `fk_policy_versions_organization`, `fk_policy_versions_author`, `fk_organizations_current_policy`.
   - Comprueba que la guarda transitoria `chk_organizations_policy_version_null` de V3 fue eliminada de `sys.check_constraints` tras la migración exitosa.
3. **`insercionValidaYActualizacionDePoliticaVigente`:**
   - Inserta una política con `version_number = 1`, `minimum_rest_minutes = 660`, `weekly_warning_minutes = 2400`, `change_reason = 'Configuración inicial'`.
   - Actualiza `organizations.current_policy_version_id` con el `id` generado.
   - Comprueba que la consulta con JOIN `organizations` y `planning_policy_versions` retorna los datos exactos.
4. **`rechazoFkCompuestaMultiTenant_PoliticaDeOtraOrganizacion`:**
   - Crea Org A y Org B.
   - Crea una versión de política perteneciente a Org B.
   - Intenta actualizar `organizations.current_policy_version_id` de Org A con el `id` de la política de Org B.
   - Verifica que se lanza `DataIntegrityViolationException` asociada a `fk_organizations_current_policy`.
5. **`rechazoAutorDeOtraOrganizacion`:**
   - Crea Org A y Org B con un usuario en Org B.
   - Intenta insertar una política en Org A con `created_by_user_id` perteneciente a Org B.
   - Verifica que se lanza `DataIntegrityViolationException` asociada a `fk_policy_versions_author`.
6. **`rechazoUnicidadDeVersionDuplicada`:**
   - Inserta en Org A una versión con `version_number = 1`.
   - Intenta insertar en Org A una segunda fila con `version_number = 1`.
   - Verifica que se lanza `DataIntegrityViolationException` asociada a `uq_policy_versions_org_version`.
7. **`rechazoLimitesFueraDeRango_MinimumRest`:**
   - Verifica rechazo para `minimum_rest_minutes = 0`, `-1` y `1441` por `chk_policy_minimum_rest_range`.
8. **`rechazoLimitesFueraDeRango_WeeklyWarning`:**
   - Verifica rechazo para `weekly_warning_minutes = 0`, `-1` y `10081` por `chk_policy_weekly_warning_range`.
9. **`rechazoMotivoVacio`:**
   - Verifica rechazo para `change_reason = ''` y `change_reason = '   '` por `chk_policy_reason_not_empty`.
10. **`upgradeProgresivoV14aV16_ConservaDatosYNulls` (Contenedor aislado):**
    - Arranca un contenedor SQL Server efímero aislado.
    - Aplica migraciones con Flyway hasta target 14.
    - Inserta organizaciones, sedes, puestos y usuarios de prueba con `current_policy_version_id = NULL`.
    - Aplica migraciones hasta target 16.
    - Comprueba que todos los datos insertados en V14 sobreviven intactos y que `current_policy_version_id` permanece en `NULL`.
    - Comprueba que la guarda transitoria `chk_organizations_policy_version_null` fue eliminada y que `fk_organizations_current_policy` existe.
    - Inserta una política y asocia el puntero con éxito en el esquema actualizado.
11. **`preflightFailClosedV16_DetectaPunteroInvalido` (Contenedor aislado):**
    - En base aislada migrada hasta V15, inserta una organización con un `current_policy_version_id` ficticio que no existe en `planning_policy_versions` (deshabilitando temporalmente el check de V3 para simular una anomalía o estado legado excepcional).
    - Ejecuta la migración V16.
    - Comprueba que Flyway falla la migración con el error `50000` definido en el preflight.
    - Comprueba que la clave foránea `fk_organizations_current_policy` NO fue creada (`COUNT = 0` en `sys.foreign_keys`).
    - Comprueba que `V16` NO quedó registrada como migración exitosa en `flyway_schema_history` (`COUNT = 0` con `version = '16'` y `success = 1`).

---

## 7. Comandos Futuros de Verificación (Solo Referencia)

> [!CAUTION]
> **No ejecutar durante la fase de diseño y planificación.** Estos comandos quedan establecidos para la fase de implementación de I2.3-a:

```powershell
# Ejecución focalizada individual de la nueva suite durante desarrollo
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
cmd /c ".\mvnw.cmd test -Dtest=PolicyPersistenceIT -B"

# Verificación final focalizada de cierre de subtarea (único comando oficial)
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
cmd /c ".\mvnw.cmd test -Dtest=PolicyPersistenceIT,SchemaUpgradeV3ToLatestIT,SpringBootStartupIT -B"
```

*Regla:* No se ejecutan `clean`, `verify`, `failsafe` ni suites completas del proyecto. La validación global se reserva para el cierre formal del incremento I2.

---

## 8. Riesgos Específicos y Controles

| Riesgo Identificado | Impacto | Control y Mitigación Incorporados |
| :--- | :--- | :--- |
| **Inconsistencia de checksums en Flyway** | Fallo crítico en arranque | Prohibición absoluta de modificar archivos `V1` a `V14`. Las adiciones son estrictamente nuevas migraciones `V15` y `V16`. |
| **Punteros huérfanos o cruzados en `organizations`** | Corrupción de integridad multi-tenant | El bloque preflight de `V16` aborta con `THROW 50000` si detecta cualquier puntero no nulo sin contraparte válida en la misma organización. |
| **Rotura de organizaciones en onboarding (`current_policy_version_id = NULL`)** | Regresión en incorporación asistida | Validado que SQL Server evalúa claves foráneas compuestas con componente nulo como `UNKNOWN` (satisfechas), probado explícitamente en el test progresivo. |
| **Aserciones rojas artificiales** | Falsos positivos en TDD | La prueba roja inicial se basa en la ausencia física real de la tabla y de la constraint en el catálogo del sistema (`INFORMATION_SCHEMA` y `sys.foreign_keys`), no en `fail()`. |

---

## 9. Auto-Revisión del Plan

- **Cobertura del documento 16:** Se incorporan exactamente todos los requisitos de persistencia, restricciones CHECK (1..1440, 1..10080), motivo (`NVARCHAR(500)`), convención temporal (`DATETIMEOFFSET(3)` con `SYSUTCDATETIME()`) y resolución por FK compuesta.
- **Consistencia de nombres:** `planning_policy_versions`, `uq_policy_versions_org_id`, `uq_policy_versions_org_version`, `fk_policy_versions_organization`, `fk_policy_versions_author`, `fk_organizations_current_policy`.
- **Aislamiento de pruebas:** No se introducen modificaciones en `SchemaUpgradeV11ToV12IT.java` debido a que su target histórico en V12 permanece correcto e inalterado.
- **Ausencia de TODOs:** El plan está 100% especificado y listo para ser ejecutado paso a paso por Antigravity.
