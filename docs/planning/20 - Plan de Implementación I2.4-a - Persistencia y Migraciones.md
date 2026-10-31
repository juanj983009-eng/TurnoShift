# Implementación I2.4-a: Persistencia y Migración de Indisponibilidades

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Diseñar e implementar la infraestructura de persistencia relacional multi-tenant para las indisponibilidades de personal de TurnoShift, incluyendo la migración aditiva Flyway `V17__employee_unavailabilities.sql`, sus restricciones de integridad empresarial compuestas, auditoría, índices filtrados y la suite exhaustiva de pruebas de esquema con Testcontainers.

**Architecture:** Persistencia relacional pura en Microsoft SQL Server 2022 sobre la base de una migración Flyway estrictamente aditiva (`V17`). La tabla `employee_unavailabilities` vincula al colaborador y a los usuarios de auditoría mediante claves foráneas compuestas `(organization_id, id)` que blindan el aislamiento multi-tenant a nivel de motor de base de datos. El intervalo de indisponibilidad se almacena como una única fuente de verdad canónica en UTC semiabierto `[starts_at, ends_at)`. La validación de ciclo de vida se refuerza mediante `CHECK constraints` físicos y concurrencia optimista (`lock_version`).

**Tech Stack:** Java 21 (Temurin), Spring Boot 3.4.x, Spring Data JDBC / `JdbcTemplate`, Flyway 10.x, Microsoft SQL Server 2022 (Linux container), Testcontainers, JUnit 5, AssertJ.

**Spec:** [docs/planning/19 - Diseño Técnico I2.4 - Indisponibilidades.md](file:///d:/Proyectos/TurnoShift/docs/planning/19%20-%20Dise%C3%B1o%20T%C3%A9cnico%20I2.4%20-%20Indisponibilidades.md)

## Global Constraints

- **No regresión:** No modificar tablas existentes ni datos legados en V1–V16.
- **Aislamiento Multi-Tenant Estricto:** Toda relación relacional debe garantizar que la organización coincida mediante claves foráneas compuestas `(organization_id, target_id)`.
- **Integridad Referencial sin Cascada:** Todas las claves foráneas deben configurarse con `ON DELETE NO ACTION ON UPDATE NO ACTION` (regla de oro del proyecto contra borrados accidentales en cascada).
- **Semántica Temporal:** Instantes normalizados en UTC con tipo `DATETIMEOFFSET(3)`, intervalo semiabierto $[starts\_at, ends\_at)$ con regla `starts_at < ends_at`.
- **TDD Riguroso:** Red-Green-Refactor con Testcontainers MSSQL Server 2022. La prueba debe fallar por ausencia de migración/esquema y pasar una vez aplicado V17.
- **Comando Único de Verificación:** Prohibido ejecutar `verify`, `clean`, `failsafe` o suites globales. La verificación se realiza exclusivamente mediante el comando único focalizado que ejecuta `UnavailabilityPersistenceIT`, `SchemaUpgradeV3ToLatestIT`, `SpringBootStartupIT` y `PolicyPersistenceIT`.

---

## 1. Alcance y Límites de I2.4-a

### 1.1. Dentro del Alcance de I2.4-a
1. **Migración Flyway `V17__employee_unavailabilities.sql`:**
   - Creación física de la tabla `employee_unavailabilities`.
   - Clave primaria simple `id` con valor predeterminado `NEWID()`.
   - Clave candidata empresarial `uq_unavailabilities_org_id (organization_id, id)`.
   - Clave foránea simple `fk_unavailabilities_organization` a `organizations(id)`.
   - Claves foráneas empresariales compuestas:
     - `fk_unavailabilities_employee`: `(organization_id, employee_id) -> employees(organization_id, id)`.
     - `fk_unavailabilities_created_by`: `(organization_id, created_by_user_id) -> app_users(organization_id, id)`.
     - `fk_unavailabilities_cancelled_by`: `(organization_id, cancelled_by_user_id) -> app_users(organization_id, id)`.
   - Restricciones `CHECK`:
     - `chk_unavailabilities_interval`: `starts_at < ends_at`.
     - `chk_unavailabilities_lock_version_non_negative`: `lock_version >= 0`.
     - `chk_unavailabilities_note_not_blank`: `restricted_note IS NULL OR LEN(LTRIM(RTRIM(restricted_note))) > 0`.
     - `chk_unavailabilities_reason_code`: catálogo cerrado `('VACACIONES', 'PERMISO_PERSONAL', 'MEDICO', 'FORMACION', 'OTRO')` o `NULL`.
     - `chk_unavailabilities_cancellation_coherence`: coherencia atómica de auditoría de cancelación `(cancelled_at IS NULL AND cancelled_by_user_id IS NULL) OR (cancelled_at IS NOT NULL AND cancelled_by_user_id IS NOT NULL)`.
   - Índices filtrados:
     - `ix_unavailabilities_employee_active`: `(organization_id, employee_id, starts_at, ends_at) WHERE cancelled_at IS NULL`.
     - `ix_unavailabilities_org_interval_active`: `(organization_id, starts_at, ends_at) INCLUDE (employee_id, reason_code) WHERE cancelled_at IS NULL`.
2. **Suite TDD de Integración `UnavailabilityPersistenceIT`:**
   - 11 casos de prueba exhaustivos con Testcontainers SQL Server 2022.
3. **Actualización de Aserciones Históricas:**
   - Actualización de los tests globales de historial y arranque para reconocer V17.

### 1.2. Fuera del Alcance de I2.4-a (Subincrementos Posteriores)
- Entidades Java de dominio, agregados y value objects (`I2.4-b`).
- Repositorios Spring Data JDBC / `JdbcTemplate` y adaptadores (`I2.4-b`).
- Casos de uso de registro, modificación, consulta y cancelación (`I2.4-b`).
- Controlador HTTP REST, DTOs, filtros, CSRF e idempotencia HTTP (`I2.4-c`).
- Contrato OpenAPI y validación Redocly (`I2.4-d`).
- Motor de planificación de turnos y validación de choques en horarios (`I3`).

---

## 2. Definición Canónica de la Representación Temporal

### 2.1. Fuente de Verdad Única $[starts\_at, ends\_at)$ en UTC
- La base de datos relacional almacena instantes absolutos normalizados a UTC con tipo `DATETIMEOFFSET(3)`.
- El intervalo temporal representa formalmente $[starts\_at, ends\_at)$ (inicio inclusivo, fin exclusivo).
- Esta semántica es requerida por **RN-017**: si un turno o una indisponibilidad contigua inicia exactamente en `ends_at`, no existe colisión matemática.

### 2.2. Prohibición de Almacenamiento Dual Incompatible
- No se introducen columnas redundantes en la base de datos (por ejemplo, `start_date`, `end_date`, `starts_at_local`, ni componentes locales de calendario).
- El almacenamiento simultáneo de fechas locales y marcas UTC en una misma fila genera duplicación de estado, riesgo de desincronización y ambigüedad ante cambios de huso horario o transiciones horarias (DST).

### 2.3. Expansión Canónica Previa de la Modalidad `allDay`
- La modalidad `allDay: true` (con `startDate` inclusiva y `endDate` exclusiva en hora local) es estrictamente un formato de entrada en la capa de transporte API.
- La capa de aplicación (caso de uso de negocio en I2.4-b) consulta la zona horaria del tenant (`organizations.timezone_id`) y calcula:
  $$\text{startsAt} = \text{LocalDate.parse}(\text{startDate}).\text{atStartOfDay}(\text{zoneId}).\text{toInstant()}$$
  $$\text{endsAt} = \text{LocalDate.parse}(\text{endDate}).\text{atStartOfDay}(\text{zoneId}).\text{toInstant()}$$
- Por lo tanto, hacia la capa de persistencia relacional únicamente transitan los instantes absolutos UTC resultantes.

### 2.4. Evaluación de la Columna `is_all_day`: Decisión Técnica y Justificación
- **Evaluación Funcional:**
  1. El motor de planificación (I3) y los algoritmos de detección de colisiones operan exclusivamente sobre la intersección temporal de intervalos $[s, e)$. El hecho de que una indisponibilidad haya sido ingresada como "día completo" o "por horas" es indistinto para la asignación y validación de turnos.
  2. Guardar un flag booleano `is_all_day = 1` en la fila relacional crearía un acoplamiento riesgoso con el huso horario del momento. Si la organización llegara a modificar su `timezone_id` en el futuro, no sería seguro asumir si el intervalo UTC debe recalculares (rompiendo inmutabilidad y auditoría de planificación ya ejecutada) o mantenerse intacto.
  3. Para efectos de renderizado en UI (etiqueta "Día Completo"), el frontend o la capa de presentación pueden contrastar si el intervalo UTC coincide con las medianoches locales según el timezone del tenant.
- **Decisión Final de Persistencia:** **NO se incluye la columna `is_all_day`** en la tabla `employee_unavailabilities`. El esquema se mantiene estrictamente minimalista, ortogonal y con una sola fuente de verdad matemática.

---

## 3. Matriz de Coherencia: Diseño Técnico 19 vs. Esquema Actual de BD

| Elemento de Diseño 19 | Referencia Actual en Esquema (V1–V16) | Estado de Coherencia |
| :--- | :--- | :--- |
| Tabla `organizations` (PK `id`) | `V1__organizations.sql` (`pk_organizations PRIMARY KEY (id)`) | **100% Coherente** |
| Clave candidata `organizations(id)` | Clave primaria simple de `organizations` | **100% Coherente** |
| Tabla `employees` (PK `id`) | `V13__employees.sql` (`pk_employees PRIMARY KEY (id)`) | **100% Coherente** |
| Clave candidata `employees(organization_id, id)` | `V13__employees.sql` (`uq_employees_org_id UNIQUE (organization_id, id)`) | **100% Coherente** |
| Tabla `app_users` (PK `id`) | `V5__app_users.sql` (`pk_app_users PRIMARY KEY (id)`) | **100% Coherente** |
| Clave candidata `app_users(organization_id, id)` | `V5__app_users.sql` (`uq_app_users_org_id UNIQUE (organization_id, id)`) | **100% Coherente** |
| Tipos de clave (`UNIQUEIDENTIFIER`) | Idénticos en todas las entidades corporativas (`UUID` en Java) | **100% Coherente** |
| Marcas de tiempo de auditoría | `DATETIMEOFFSET(3)` con default `SYSUTCDATETIME()` en V13/V15 | **100% Coherente** |
| Manejo de concurrencia | `lock_version BIGINT NOT NULL DEFAULT 0` con `CHECK (lock_version >= 0)` | **100% Coherente** |

> **Conclusión de Verificación:** Existen **cero (0) discrepancias** entre el diseño técnico 19 y el esquema de base de datos actual. Todas las restricciones compuestas `(organization_id, id)` requeridas ya están debidamente instaladas en las tablas referenciadas.

---

## 4. Estructura de Archivos a Modificar y Crear

```
backend/
├── src/
│   ├── main/
│   │   └── resources/
│   │       └── db/
│   │           └── migration/
│   │               └── V17__employee_unavailabilities.sql        [CREAR]
│   └── test/
│       └── java/
│           └── com/
│               └── turnoshift/
│                   ├── persistence/
│                   │   ├── UnavailabilityPersistenceIT.java      [CREAR]
│                   │   ├── SchemaUpgradeV3ToLatestIT.java        [MODIFICAR]
│                   │   └── PolicyPersistenceIT.java              [MODIFICAR]
│                   └── health/
│                       └── SpringBootStartupIT.java              [MODIFICAR]
```

---

## 5. Tareas Detalladas de Implementación

### Task 1: [Test] Fase Roja - Creación de la Suite de Integración `UnavailabilityPersistenceIT`

**Files:**
- Create: `backend/src/test/java/com/turnoshift/persistence/UnavailabilityPersistenceIT.java`

**Interfaces:**
- Consumes: Testcontainers `MSSQLServerContainer`, Spring Boot test context, `JdbcTemplate`.
- Produces: Suite de validación de persistencia con 11 tests exhaustivos que fallarán inicialmente (Fase Roja) por la ausencia de la tabla `employee_unavailabilities`.

- [ ] **Step 1: Escribir la prueba de integración completa `UnavailabilityPersistenceIT`**

Crear el archivo `backend/src/test/java/com/turnoshift/persistence/UnavailabilityPersistenceIT.java` con las siguientes pruebas:

```java
package com.turnoshift.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integración de persistencia y migraciones para I2.4-a:
 * Verifica la migración V17__employee_unavailabilities.sql, integridad multi-tenant con FKs
 * compuestas a employees y app_users, restricciones CHECK, índices filtrados y ruta de upgrade V16->V17.
 */
@SpringBootTest
@Testcontainers
@Transactional
@Rollback
@DisplayName("IT: Persistencia de indisponibilidades e integridad multi-tenant (V17)")
class UnavailabilityPersistenceIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                    .acceptLicense();

    @DynamicPropertySource
    static void configurarDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    // =========================================================================
    // Helpers para armado de grafos relacionales
    // =========================================================================

    private String insertarOrganizacion(String code, String displayName) {
        String orgId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                    max_active_employees, max_active_sites)
                VALUES (?, ?, ?, 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, code, displayName);
        return orgId;
    }

    private String insertarSede(String orgId, String code, String name) {
        String siteId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO sites (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, ?, 1)
                """, siteId, orgId, code, name);
        return siteId;
    }

    private String insertarPuesto(String orgId, String code, String name) {
        String positionId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO positions (id, organization_id, code, name, is_active)
                VALUES (?, ?, ?, ?, 1)
                """, positionId, orgId, code, name);
        return positionId;
    }

    private String insertarEmpleado(String orgId, String code, String displayName, String posId, String siteId) {
        String empId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO employees (id, organization_id, code, display_name, position_id, site_id, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """, empId, orgId, code, displayName, posId, siteId);
        return empId;
    }

    private String insertarUsuario(String orgId, String email, String displayName) {
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                VALUES (?, ?, ?, ?, ?, 'SUPERVISOR', NULL)
                """, userId, orgId, email, email.toLowerCase(), displayName);
        return userId;
    }

    private String insertarIndisponibilidad(String orgId, String empId, Instant startsAt, Instant endsAt,
                                            String reasonCode, String note, String createdByUserId) {
        String unavId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO employee_unavailabilities (
                    id, organization_id, employee_id, starts_at, ends_at,
                    reason_code, restricted_note, created_by_user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, unavId, orgId, empId, startsAt.toString(), endsAt.toString(),
                reasonCode, note, createdByUserId);
        return unavId;
    }

    // =========================================================================
    // Casos de prueba
    // =========================================================================

    @Test
    @DisplayName("1. Flyway aplica V1 a V17 en orden desde una base vacía")
    void aplicacionLimpiaDeMigraciones() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(versiones).contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17");
    }

    @Test
    @DisplayName("2. La tabla employee_unavailabilities y sus constraints existen con nombres exactos")
    void existenciaDeEstructurasRelacionalesYConstraints() {
        Integer tableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'employee_unavailabilities' AND TABLE_TYPE = 'BASE TABLE'",
                Integer.class);
        assertThat(tableCount).isEqualTo(1);

        for (String constraintName : List.of(
                "pk_employee_unavailabilities",
                "uq_unavailabilities_org_id",
                "fk_unavailabilities_organization",
                "fk_unavailabilities_employee",
                "fk_unavailabilities_created_by",
                "fk_unavailabilities_cancelled_by",
                "chk_unavailabilities_interval",
                "chk_unavailabilities_lock_version_non_negative",
                "chk_unavailabilities_note_not_blank",
                "chk_unavailabilities_reason_code",
                "chk_unavailabilities_cancellation_coherence"
        )) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys.objects
                    WHERE name = ?
                    """, Integer.class, constraintName);
            assertThat(count).as("La restricción " + constraintName + " debe existir físicamente").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("3. Inserción válida y recuperación completa de campos, defaults y marcas de tiempo")
    void insercionValidaYRecuperacion() {
        String orgId = insertarOrganizacion("ORG-UNAV-01", "Org Unav 01");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Puesto 1");
        String empId = insertarEmpleado(orgId, "EMP-01", "Carlos Gomez", posId, siteId);
        String userId = insertarUsuario(orgId, "supervisor@unav01.com", "Supervisor Unav 01");

        Instant startsAt = Instant.parse("2026-11-01T08:00:00Z");
        Instant endsAt = Instant.parse("2026-11-01T17:00:00Z");
        String unavId = insertarIndisponibilidad(orgId, empId, startsAt, endsAt, "MEDICO", "Cita odontológica", userId);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT id, organization_id, employee_id, reason_code, restricted_note,
                       created_by_user_id, cancelled_at, cancelled_by_user_id, lock_version
                FROM employee_unavailabilities
                WHERE id = ?
                """, unavId);

        assertThat(row.get("id").toString()).isEqualToIgnoringCase(unavId);
        assertThat(row.get("organization_id").toString()).isEqualToIgnoringCase(orgId);
        assertThat(row.get("employee_id").toString()).isEqualToIgnoringCase(empId);
        assertThat(row.get("reason_code")).isEqualTo("MEDICO");
        assertThat(row.get("restricted_note")).isEqualTo("Cita odontológica");
        assertThat(row.get("created_by_user_id").toString()).isEqualToIgnoringCase(userId);
        assertThat(row.get("cancelled_at")).isNull();
        assertThat(row.get("cancelled_by_user_id")).isNull();
        assertThat(row.get("lock_version")).isEqualTo(0L);
    }

    @Test
    @DisplayName("4. Aislamiento multi-tenant: consultas filtradas no retornan datos de otras organizaciones")
    void aislamientoMultiTenant() {
        String orgAId = insertarOrganizacion("ORG-TEN-A", "Org A");
        String siteAId = insertarSede(orgAId, "SED-A", "Sede A");
        String posAId = insertarPuesto(orgAId, "PST-A", "Puesto A");
        String empAId = insertarEmpleado(orgAId, "EMP-A", "Empleado A", posAId, siteAId);
        String userAId = insertarUsuario(orgAId, "user@orga.com", "User A");

        String orgBId = insertarOrganizacion("ORG-TEN-B", "Org B");
        String siteBId = insertarSede(orgBId, "SED-B", "Sede B");
        String posBId = insertarPuesto(orgBId, "PST-B", "Puesto B");
        String empBId = insertarEmpleado(orgBId, "EMP-B", "Empleado B", posBId, siteBId);
        String userBId = insertarUsuario(orgBId, "user@orgb.com", "User B");

        Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(8, ChronoUnit.HOURS);

        insertarIndisponibilidad(orgAId, empAId, startsAt, endsAt, "VACACIONES", "Vacaciones Org A", userAId);
        insertarIndisponibilidad(orgBId, empBId, startsAt, endsAt, "VACACIONES", "Vacaciones Org B", userBId);

        List<Map<String, Object>> filasOrgA = jdbc.queryForList(
                "SELECT id FROM employee_unavailabilities WHERE organization_id = ?", orgAId);
        assertThat(filasOrgA).hasSize(1);

        List<Map<String, Object>> filasOrgB = jdbc.queryForList(
                "SELECT id FROM employee_unavailabilities WHERE organization_id = ?", orgBId);
        assertThat(filasOrgB).hasSize(1);
    }

    @Test
    @DisplayName("5. fk_unavailabilities_employee rechaza colaborador perteneciente a otra organización")
    void rechazoFkCruzada_EmpleadoDeOtraOrganizacion() {
        String orgAId = insertarOrganizacion("ORG-FK-A", "Org A");
        String userAId = insertarUsuario(orgAId, "admin@fka.com", "Admin A");

        String orgBId = insertarOrganizacion("ORG-FK-B", "Org B");
        String siteBId = insertarSede(orgBId, "SED-B", "Sede B");
        String posBId = insertarPuesto(orgBId, "PST-B", "Puesto B");
        String empBId = insertarEmpleado(orgBId, "EMP-B", "Empleado B", posBId, siteBId);

        Instant startsAt = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(4, ChronoUnit.HOURS);

        assertThatThrownBy(() ->
                insertarIndisponibilidad(orgAId, empBId, startsAt, endsAt, "OTRO", "Intento cruzado", userAId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_unavailabilities_employee");
    }

    @Test
    @DisplayName("6. fk_unavailabilities_created_by rechaza autor de creación de otra organización")
    void rechazoFkCruzada_AutorDeCreacionDeOtraOrganizacion() {
        String orgAId = insertarOrganizacion("ORG-AUT-A", "Org A");
        String siteAId = insertarSede(orgAId, "SED-A", "Sede A");
        String posAId = insertarPuesto(orgAId, "PST-A", "Puesto A");
        String empAId = insertarEmpleado(orgAId, "EMP-A", "Empleado A", posAId, siteAId);

        String orgBId = insertarOrganizacion("ORG-AUT-B", "Org B");
        String userBId = insertarUsuario(orgBId, "user@autb.com", "User B");

        Instant startsAt = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(4, ChronoUnit.HOURS);

        assertThatThrownBy(() ->
                insertarIndisponibilidad(orgAId, empAId, startsAt, endsAt, "OTRO", "Autor cruzado", userBId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_unavailabilities_created_by");
    }

    @Test
    @DisplayName("7. fk_unavailabilities_cancelled_by rechaza autor de cancelación de otra organización")
    void rechazoFkCruzada_AutorDeCancelacionDeOtraOrganizacion() {
        String orgAId = insertarOrganizacion("ORG-CNC-A", "Org A");
        String siteAId = insertarSede(orgAId, "SED-A", "Sede A");
        String posAId = insertarPuesto(orgAId, "PST-A", "Puesto A");
        String empAId = insertarEmpleado(orgAId, "EMP-A", "Empleado A", posAId, siteAId);
        String userAId = insertarUsuario(orgAId, "admin@cnca.com", "Admin A");

        String orgBId = insertarOrganizacion("ORG-CNC-B", "Org B");
        String userBId = insertarUsuario(orgBId, "admin@cncb.com", "Admin B");

        Instant startsAt = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(8, ChronoUnit.HOURS);
        String unavId = insertarIndisponibilidad(orgAId, empAId, startsAt, endsAt, "FORMACION", "Curso", userAId);

        assertThatThrownBy(() ->
                jdbc.update("""
                        UPDATE employee_unavailabilities
                        SET cancelled_at = SYSUTCDATETIME(), cancelled_by_user_id = ?
                        WHERE id = ?
                        """, userBId, unavId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_unavailabilities_cancelled_by");
    }

    @Test
    @DisplayName("8. chk_unavailabilities_interval rechaza starts_at >= ends_at")
    void rechazoIntervaloInvalido() {
        String orgId = insertarOrganizacion("ORG-INT-01", "Org Int 01");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Puesto 1");
        String empId = insertarEmpleado(orgId, "EMP-01", "Empleado 1", posId, siteId);
        String userId = insertarUsuario(orgId, "user@int01.com", "User 1");

        Instant t1 = Instant.parse("2026-11-10T12:00:00Z");

        // starts_at == ends_at
        assertThatThrownBy(() ->
                insertarIndisponibilidad(orgId, empId, t1, t1, "OTRO", "Intervalo cero", userId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_unavailabilities_interval");

        // starts_at > ends_at
        assertThatThrownBy(() ->
                insertarIndisponibilidad(orgId, empId, t1, t1.minusSeconds(3600), "OTRO", "Intervalo invertido", userId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_unavailabilities_interval");
    }

    @Test
    @DisplayName("9. chk_unavailabilities_reason_code rechaza códigos no contemplados en el catálogo cerrado")
    void rechazoCatalogoInvalido() {
        String orgId = insertarOrganizacion("ORG-CAT-01", "Org Cat 01");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Puesto 1");
        String empId = insertarEmpleado(orgId, "EMP-01", "Empleado 1", posId, siteId);
        String userId = insertarUsuario(orgId, "user@cat01.com", "User 1");

        Instant startsAt = Instant.now().plus(4, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(4, ChronoUnit.HOURS);

        assertThatThrownBy(() ->
                insertarIndisponibilidad(orgId, empId, startsAt, endsAt, "INVENTADO", "Código ilegal", userId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_unavailabilities_reason_code");
    }

    @Test
    @DisplayName("10. chk_unavailabilities_cancellation_coherence rechaza estados de cancelación parciales")
    void rechazoCoherenciaCancelacionParcial() {
        String orgId = insertarOrganizacion("ORG-COH-01", "Org Coh 01");
        String siteId = insertarSede(orgId, "SED-01", "Sede 1");
        String posId = insertarPuesto(orgId, "PST-01", "Puesto 1");
        String empId = insertarEmpleado(orgId, "EMP-01", "Empleado 1", posId, siteId);
        String userId = insertarUsuario(orgId, "user@coh01.com", "User 1");

        Instant startsAt = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant endsAt = startsAt.plus(8, ChronoUnit.HOURS);
        String unavId = insertarIndisponibilidad(orgId, empId, startsAt, endsAt, "VACACIONES", "Vacaciones", userId);

        // Intento 1: cancelled_at presente, pero cancelled_by_user_id nulo
        assertThatThrownBy(() ->
                jdbc.update("UPDATE employee_unavailabilities SET cancelled_at = SYSUTCDATETIME() WHERE id = ?", unavId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_unavailabilities_cancellation_coherence");

        // Intento 2: cancelled_by_user_id presente, pero cancelled_at nulo
        assertThatThrownBy(() ->
                jdbc.update("UPDATE employee_unavailabilities SET cancelled_by_user_id = ? WHERE id = ?", userId, unavId)
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_unavailabilities_cancellation_coherence");
    }

    @Test
    @DisplayName("11. Los índices filtrados de indisponibilidades activas existen físicamente con filtro cancelled_at IS NULL")
    void verificacionIndicesFiltrados() {
        List<Map<String, Object>> indices = jdbc.queryForList("""
                SELECT i.name AS index_name, i.has_filter, i.filter_definition
                FROM sys.indexes i
                JOIN sys.objects o ON i.object_id = o.object_id
                WHERE o.name = 'employee_unavailabilities'
                  AND i.name IN ('ix_unavailabilities_employee_active', 'ix_unavailabilities_org_interval_active')
                """);

        assertThat(indices).hasSize(2);
        for (Map<String, Object> idx : indices) {
            assertThat(idx.get("has_filter")).isEqualTo(true);
            assertThat(idx.get("filter_definition").toString().toUpperCase()).contains("CANCELLED_AT IS NULL");
        }
    }

    @Test
    @DisplayName("12. Upgrade progresivo V16→V17 conserva datos existentes y habilita la nueva tabla")
    void upgradeProgresivoV16aV17_ConservaDatos() {
        try (MSSQLServerContainer isolatedServer =
                     new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU16-ubuntu-22.04")
                             .acceptLicense()) {
            isolatedServer.start();

            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            ds.setUrl(isolatedServer.getJdbcUrl());
            ds.setUsername(isolatedServer.getUsername());
            ds.setPassword(isolatedServer.getPassword());

            JdbcTemplate isolatedJdbc = new JdbcTemplate(ds);

            // 1. Migrar hasta Flyway target 16 (estado previo a indisponibilidades)
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("16")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            String orgId = "11111111-1111-1111-1111-111111111111";
            String siteId = "22222222-2222-2222-2222-222222222222";
            String posId = "33333333-3333-3333-3333-333333333333";
            String empId = "44444444-4444-4444-4444-444444444444";
            String userId = "55555555-5555-5555-5555-555555555555";

            // 2. Insertar grafo relacional previo en V16
            isolatedJdbc.update("""
                    INSERT INTO organizations (id, code, display_name, service_status, timezone_id,
                        max_active_employees, max_active_sites)
                    VALUES (?, 'ORG-UPG-16', 'Org Upgrade V16', 'ACTIVA', 'America/Bogota', 15, 1)
                    """, orgId);

            isolatedJdbc.update("""
                    INSERT INTO sites (id, organization_id, code, name, is_active)
                    VALUES (?, ?, 'HQ-16', 'Sede V16', 1)
                    """, siteId, orgId);

            isolatedJdbc.update("""
                    INSERT INTO positions (id, organization_id, code, name, is_active)
                    VALUES (?, ?, 'POS-16', 'Puesto V16', 1)
                    """, posId, orgId);

            isolatedJdbc.update("""
                    INSERT INTO employees (id, organization_id, code, display_name, position_id, site_id, is_active)
                    VALUES (?, ?, 'EMP-16', 'Colaborador V16', ?, ?, 1)
                    """, empId, orgId, posId, siteId);

            isolatedJdbc.update("""
                    INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, role_code, employee_id)
                    VALUES (?, ?, 'v16user@turnoshift.com', 'v16user@turnoshift.com', 'Usuario V16', 'ADMINISTRADOR', ?)
                    """, userId, orgId, empId);

            // 3. Migrar aplicando V17
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target("17")
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            // 4. Verificar que los datos preexistentes se conservan intactos
            Map<String, Object> empRow = isolatedJdbc.queryForMap(
                    "SELECT id, code, display_name FROM employees WHERE id = ?", empId);
            assertThat(empRow.get("code")).isEqualTo("EMP-16");

            // 5. Verificar que la tabla employee_unavailabilities existe y permite registrar una fila vinculada
            Integer tableCount = isolatedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'employee_unavailabilities'",
                    Integer.class);
            assertThat(tableCount).isEqualTo(1);

            String unavId = UUID.randomUUID().toString();
            isolatedJdbc.update("""
                    INSERT INTO employee_unavailabilities (
                        id, organization_id, employee_id, starts_at, ends_at,
                        reason_code, restricted_note, created_by_user_id
                    )
                    VALUES (?, ?, ?, '2026-12-01T00:00:00Z', '2026-12-05T00:00:00Z', 'VACACIONES', 'Post upgrade', ?)
                    """, unavId, orgId, empId, userId);

            String storedUnavId = isolatedJdbc.queryForObject(
                    "SELECT id FROM employee_unavailabilities WHERE id = ?", String.class, unavId);
            assertThat(storedUnavId).isEqualToIgnoringCase(unavId);
        }
    }
}
```

- [ ] **Step 2: Verificar la Fase Roja ejecutando la prueba focalizada**

Ejecutar en consola:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityPersistenceIT" -B
```
Resultado esperado: **FALLO** (la tabla `employee_unavailabilities` no existe aún, o Flyway no encuentra la migración V17).

---

### Task 2: [Migration] Fase Verde - Creación de la Migración `V17__employee_unavailabilities.sql`

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__employee_unavailabilities.sql`

**Interfaces:**
- Consumes: `organizations(id)`, `employees(organization_id, id)`, `app_users(organization_id, id)`.
- Produces: Tabla `employee_unavailabilities`, índices filtrados y restricciones relacionales compuestas.

- [ ] **Step 1: Crear el script DDL de migración Flyway V17**

Crear el archivo `backend/src/main/resources/db/migration/V17__employee_unavailabilities.sql` con el siguiente contenido exacto:

```sql
-- =============================================================================
-- V17: employee_unavailabilities
-- =============================================================================
-- Tabla multi-tenant para indisponibilidades temporales de personal (MVP universitario).
-- Soporta tanto indisponibilidades por horas (parciales) como por días completos
-- convertidos a instantes UTC [starts_at, ends_at) según la zona horaria del tenant.
--
-- Integridad relacional multi-tenant garantizada mediante claves foráneas compuestas:
--   - (organization_id, employee_id)         -> employees(organization_id, id)
--   - (organization_id, created_by_user_id)   -> app_users(organization_id, id)
--   - (organization_id, cancelled_by_user_id) -> app_users(organization_id, id)
-- Todas configuradas con ON DELETE NO ACTION ON UPDATE NO ACTION.
--
-- Concurrencia optimista mediante lock_version BIGINT.
-- Auditoría de cancelación lógica atómica (cancelled_at y cancelled_by_user_id
-- deben ser ambos nulos o ambos no nulos).
-- =============================================================================

CREATE TABLE employee_unavailabilities (
    -- Identificación técnica y empresarial (§4.3)
    organization_id         UNIQUEIDENTIFIER    NOT NULL,
    id                      UNIQUEIDENTIFIER    NOT NULL    DEFAULT NEWID(),

    -- Colaborador afectado (FK compuesta a employees)
    employee_id             UNIQUEIDENTIFIER    NOT NULL,

    -- Intervalo temporal semiabierto [starts_at, ends_at) en UTC
    starts_at               DATETIMEOFFSET(3)   NOT NULL,
    ends_at                 DATETIMEOFFSET(3)   NOT NULL,

    -- Motivo estandarizado opcional y nota privada confidencial (RN-014)
    reason_code             VARCHAR(32)         NULL,
    restricted_note         NVARCHAR(500)       NULL,

    -- Auditoría de cancelación lógica
    cancelled_at            DATETIMEOFFSET(3)   NULL,
    cancelled_by_user_id    UNIQUEIDENTIFIER    NULL,

    -- Auditoría de creación y actualización
    created_by_user_id      UNIQUEIDENTIFIER    NOT NULL,
    created_at              DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIMEOFFSET(3)   NOT NULL    DEFAULT SYSUTCDATETIME(),

    -- Control de concurrencia optimista
    lock_version            BIGINT              NOT NULL    DEFAULT 0,

    -- Clave primaria simple
    CONSTRAINT pk_employee_unavailabilities
        PRIMARY KEY (id),

    -- Clave candidata empresarial: habilita integridad multi-tenant en relaciones compuestas
    CONSTRAINT uq_unavailabilities_org_id
        UNIQUE (organization_id, id),

    -- Clave foránea simple a organizations (sin borrado en cascada)
    CONSTRAINT fk_unavailabilities_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- Clave foránea compuesta a employees: garantiza pertenencia a la misma organización
    CONSTRAINT fk_unavailabilities_employee
        FOREIGN KEY (organization_id, employee_id)
        REFERENCES employees (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- Clave foránea compuesta a usuario autor de creación
    CONSTRAINT fk_unavailabilities_created_by
        FOREIGN KEY (organization_id, created_by_user_id)
        REFERENCES app_users (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- Clave foránea compuesta a usuario autor de cancelación
    CONSTRAINT fk_unavailabilities_cancelled_by
        FOREIGN KEY (organization_id, cancelled_by_user_id)
        REFERENCES app_users (organization_id, id)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION,

    -- Restricción cronológica: inicio estrictamente menor al fin
    CONSTRAINT chk_unavailabilities_interval
        CHECK (starts_at < ends_at),

    -- Restricción de lock_version no negativo
    CONSTRAINT chk_unavailabilities_lock_version_non_negative
        CHECK (lock_version >= 0),

    -- Restricción de nota privada: si no es nula, no puede ser una cadena en blanco
    CONSTRAINT chk_unavailabilities_note_not_blank
        CHECK (restricted_note IS NULL OR LEN(LTRIM(RTRIM(restricted_note))) > 0),

    -- Restricción de motivo: catálogo controlado de motivos iniciales
    CONSTRAINT chk_unavailabilities_reason_code
        CHECK (reason_code IS NULL OR reason_code IN ('VACACIONES', 'PERMISO_PERSONAL', 'MEDICO', 'FORMACION', 'OTRO')),

    -- Restricción de coherencia en cancelación: ambos presentes o ambos nulos
    CONSTRAINT chk_unavailabilities_cancellation_coherence
        CHECK (
            (cancelled_at IS NULL AND cancelled_by_user_id IS NULL)
            OR
            (cancelled_at IS NOT NULL AND cancelled_by_user_id IS NOT NULL)
        )
);

-- =============================================================================
-- Índices Filtrados para Consultas de Indisponibilidades Activas
-- =============================================================================

-- Índice filtrado para resolver indisponibilidades vigentes de un colaborador
CREATE INDEX ix_unavailabilities_employee_active
    ON employee_unavailabilities (organization_id, employee_id, starts_at, ends_at)
    WHERE cancelled_at IS NULL;

-- Índice filtrado para carga masiva por rango de tiempo y organización (motor I3)
CREATE INDEX ix_unavailabilities_org_interval_active
    ON employee_unavailabilities (organization_id, starts_at, ends_at)
    INCLUDE (employee_id, reason_code)
    WHERE cancelled_at IS NULL;
```

- [ ] **Step 2: Ejecutar la prueba focalizada para confirmar la Fase Verde**

Ejecutar en consola:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityPersistenceIT" -B
```
Resultado esperado: **APROBADO (BUILD SUCCESS, 12 pruebas pasadas)**.

---

### Task 3: [Refactor] Actualización de Aserciones Históricas de Migración

**Files:**
- Modify: `backend/src/test/java/com/turnoshift/persistence/SchemaUpgradeV3ToLatestIT.java:130-138`
- Modify: `backend/src/test/java/com/turnoshift/health/SpringBootStartupIT.java:126-143`
- Modify: `backend/src/test/java/com/turnoshift/persistence/PolicyPersistenceIT.java:101-107`

**Interfaces:**
- Consumes: Historial de Flyway hasta versión `17`.
- Produces: Coherencia global de los tests de ciclo de vida del contenedor y catálogo de tablas.

- [ ] **Step 1: Actualizar `SchemaUpgradeV3ToLatestIT.java`**

En `backend/src/test/java/com/turnoshift/persistence/SchemaUpgradeV3ToLatestIT.java`:
- Actualizar el nombre del test en la línea 130: `"Historial contiene V1 a V17 tras actualización progresiva V3→latest"`.
- Actualizar la aserción en la línea 137:
  ```java
  assertThat(versiones)
          .as("La actualización progresiva V3→latest debe registrar V1 a V17")
          .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17");
  ```

- [ ] **Step 2: Actualizar `SpringBootStartupIT.java`**

En `backend/src/test/java/com/turnoshift/health/SpringBootStartupIT.java`:
- Actualizar la aserción en la línea 127 para incluir `"17"`:
  ```java
  assertThat(versiones)
          .as("Flyway auto-config debe haber aplicado V1 a V17 al arrancar Spring Boot")
          .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17");
  ```
- Actualizar la lista de tablas de producción verificadas en la línea 137 para incluir `"employee_unavailabilities"`:
  ```java
  for (String tabla : List.of("organizations", "sites", "roles", "app_users", "password_reset_tokens", "platform_operators", "api_operations", "positions", "employees", "planning_policy_versions", "employee_unavailabilities")) {
  ```

- [ ] **Step 3: Actualizar `PolicyPersistenceIT.java`**

En `backend/src/test/java/com/turnoshift/persistence/PolicyPersistenceIT.java`:
- Actualizar la aserción en la línea 106 para incluir `"17"`:
  ```java
  assertThat(versiones).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17");
  ```

---

### Task 4: [Verification] Verificación Focalizada Final de I2.4-a

**Files:** Ninguno (ejecución de pruebas).

- [ ] **Step 1: Ejecutar el comando único de verificación focalizada obligatoria**

La verificación de `SchemaUpgradeV3ToLatestIT`, `SpringBootStartupIT` y `PolicyPersistenceIT` no es opcional, dado que el plan contempla su actualización para reconocer la migración V17. Por ello, se sustituyen las verificaciones separadas por un único comando final focalizado obligatorio:

```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityPersistenceIT,SchemaUpgradeV3ToLatestIT,SpringBootStartupIT,PolicyPersistenceIT" -B
```

Resultado esperado: **BUILD SUCCESS** con todas las pruebas aprobadas, garantizando que este comando cubre de forma exhaustiva y focalizada:
- **esquema nuevo V17:** creación de `employee_unavailabilities`, claves foráneas compuestas, checks e índices filtrados (`UnavailabilityPersistenceIT`);
- **upgrade V16→V17:** preservación de datos e inserción en la nueva tabla tras migración progresiva (`UnavailabilityPersistenceIT`);
- **historial Flyway actualizado:** registro progresivo exacto de versiones V1→V17 (`SchemaUpgradeV3ToLatestIT`);
- **arranque Spring Boot con esquema latest:** verificación de auto-configuración y catálogo de tablas de producción (`SpringBootStartupIT`);
- **regresión de persistencia de políticas:** verificación de no afectación en versiones de políticas y claves foráneas previas (`PolicyPersistenceIT`).

---

## 6. Comando Único de Verificación Focalizado para I2.4-a

Para verificar de forma estricta, atómica y completa el entregable de I2.4-a, el comando único final focalizado y obligatorio es:

```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityPersistenceIT,SchemaUpgradeV3ToLatestIT,SpringBootStartupIT,PolicyPersistenceIT" -B
```

Este comando cubre de forma exhaustiva:
- **esquema nuevo V17;**
- **upgrade V16→V17;**
- **historial Flyway actualizado;**
- **arranque Spring Boot con esquema latest;**
- **regresión de persistencia de políticas.**

No se deben ejecutar `mvn clean`, `mvn verify`, plugins `failsafe` ni suites globales de test en esta fase.
