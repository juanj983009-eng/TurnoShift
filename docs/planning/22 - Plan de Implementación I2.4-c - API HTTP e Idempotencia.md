# Plan de Implementación I2.4-c — API HTTP, Zona Horaria e Idempotencia de Indisponibilidades

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar la capa de exposición web HTTP REST para la gestión de indisponibilidades de personal (HU-10), proveyendo 6 endpoints bajo `/api/v1`, control de acceso RBAC estricto (Administrador y Supervisor), conversión determinista de zona horaria de organización con manejo explícito de horario de verano (DST), protección de idempotencia mono-transaccional mediante `BusinessIdempotentOperationExecutor`, control de concurrencia optimista y mapeo de errores Problem Details conforme a RFC 9457.

**Architecture:** Capa web REST construida sobre Spring Web MVC y Spring Security. Los controladores resuelven la identidad del tenant y el actor exclusivamente desde la sesión autenticada (`AuthenticatedIdentity`). La entrada temporal admite dos formatos mutuamente excluyentes (parcial UTC o día completo local con `endDate` exclusiva expandido vía `organizations.timezone_id`). Las mutaciones son orquestadas mediante comandos tipados de `BusinessIdempotentCommand<EmployeeUnavailabilityResponse>` garantizando replay seguro y detección de reuso de clave sobre la tabla `api_operations`. Los errores de negocio se traducen directamente a Problem Details sin persistirse como operaciones idempotentes.

**Tech Stack:** Java 21, Spring Boot 4.0.0, Spring Web MVC, Spring Security 7.x (Session JDBC + CSRF), Jackson (tools.jackson), Testcontainers 2.0.2 (MSSQL Server 2022 CU16), JUnit Jupiter 6.0.1, AssertJ, Mockito.

**Spec:** [docs/planning/19 - Diseño Técnico I2.4 - Indisponibilidades.md](file:///d:/Proyectos/TurnoShift/docs/planning/19%20-%20Dise%C3%B1o%20T%C3%A9cnico%20I2.4%20-%20Indisponibilidades.md)

---

## Global Constraints

- **Entorno JDK:** Todas las compilaciones y ejecuciones de prueba utilizan obligatoriamente:
  `$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"`
- **Stack Tecnológico:**
  - Java 21
  - Spring Boot 4.0.0
  - Spring Security 7.x
  - JUnit Jupiter 6.0.1
  - Testcontainers 2.0.2
  - Prohibido referenciar Spring Boot 3.x, Spring Security 6.x o JUnit 5.
- **Control de Versiones y Git:**
  - **No se realizarán commits ni push** (`git add`, `git commit`, `git push` terminantemente prohibidos).
  - Cada tarea finaliza con la verificación exitosa de sus pruebas automáticas, sin interacción con Git.
- **Aislamiento Multi-tenant:** El `organizationId` y el `actorUserId` se derivan **estrictamente** del `SecurityContextHolder` (`AuthenticatedIdentity`). Jamás se aceptan en el cuerpo JSON ni se confía en parámetros de ruta sin validar pertenencia contra el tenant autenticado.
- **RBAC Estricto:**
  - `ROLE_ADMINISTRADOR` y `ROLE_SUPERVISOR`: Permitidos (200 OK / 201 Created).
  - `ROLE_COLABORADOR`: Rechazado con HTTP 403 Forbidden (`ACCESS_DENIED`).
  - `ROLE_PLATFORM_OPERATOR`: Rechazado con HTTP 403 Forbidden (`ACCESS_DENIED`).
  - Peticiones anónimas / sin sesión: Rechazadas con HTTP 401 Unauthorized (`SESSION_REQUIRED`).
- **CSRF:** Obligatorio en todas las mutaciones (`POST` y `PUT`) mediante la cabecera `X-CSRF-TOKEN`.
- **Formatos Temporales Mutuamente Excluyentes (RN-015, RN-016):**
  - *Formato A (Parcial):* `startsAt` + `endsAt` (UTC ISO-8601). `startsAt < endsAt`. Campos locales deben ser nulos.
  - *Formato B (Día Completo):* `allDay: true` + `startDate` (inclusiva `YYYY-MM-DD`) + `endDate` (exclusiva `YYYY-MM-DD`). `startDate < endDate`. Campos UTC deben ser nulos.
  - Expansión de día completo usando exclusivamente `ZoneId.of(organizations.timezone_id)` mediante `LocalDate.atStartOfDay(zoneId).toInstant()`.
  - Cualquier payload mixto o incompleto se rechaza con HTTP 400 Bad Request (`VALIDATION_FAILED`).
  - El dominio y repositorio reciben única y exclusivamente `Instant`s normalizados a UTC.
- **Idempotencia (V11 `api_operations`):**
  - Obligatoria en `POST` de creación, `PUT` de modificación y `POST` de cancelación vía `Idempotency-Key` (UUID).
  - Prohibida en consultas `GET` (no procesan ni exigen la cabecera).
  - Reutilización directa de `BusinessIdempotencyResult<T>` y `BusinessIdempotentCommand<EmployeeUnavailabilityResponse>`.
  - No se crea `UnavailabilityExecutionResult` ni clases selladas de resultados propias.
  - Las mutaciones exitosas devuelven y persisten `EmployeeUnavailabilityResponse`.
  - Replay transparente con cabecera `Idempotency-Replayed: true`.
  - Reutilización de clave con distinto payload responde HTTP 409 Conflict (`IDEMPOTENCY_KEY_REUSED`).
  - TTL de 24 horas (`IDEMPOTENCY_KEY_EXPIRED`).
  - Los errores de negocio se manejan como Problem Details existentes (no se persisten como registros de éxito en `api_operations`).
  - Prohibido duplicar infraestructura de idempotencia: reutilizar `BusinessIdempotentOperationExecutor` y `BusinessIdempotentCommand`.
- **Control de Concurrencia Optimista:** `expectedLockVersion` es estrictamente obligatorio (número >= 0) en `PUT` y `POST /cancel`. Desalineación responde HTTP 409 Conflict (`CONCURRENT_MODIFICATION`).
- **Reglas de Negocio Específicas:**
  - **RN-013 en actualización:**
    - Se rechaza si la indisponibilidad original tiene `startsAt <= now` (`PAST_INTERVAL_MODIFICATION_PROHIBITED`, 400 Bad Request).
    - Se rechaza si el nuevo intervalo propuesto tiene `newStartsAt <= now` (`PAST_INTERVAL_MODIFICATION_PROHIBITED`, 400 Bad Request).
    - Con `TimeInterval` positivo (`startsAt < endsAt`), no se requiere una regla redundante separada sobre `newEndsAt`.
  - **RN-013 en cancelación:** Se rechaza si `startsAt <= now` (`PAST_INTERVAL_MODIFICATION_PROHIBITED`, 400 Bad Request).
  - **Cancelación previa:** Modificación o cancelación de una indisponibilidad ya cancelada responde HTTP 409 Conflict (`UNAVAILABILITY_ALREADY_CANCELLED`).
  - **No encontrado:** Colaborador no encontrado o inactivo responde HTTP 404 Not Found (`EMPLOYEE_NOT_FOUND`). Indisponibilidad no encontrada responde HTTP 404 Not Found (`UNAVAILABILITY_NOT_FOUND`).
- **RFC 9457 Problem Details:** Todas las respuestas de error utilizan `Content-Type: application/problem+json;charset=UTF-8`, `Cache-Control: no-store` y contienen `type`, `title`, `status`, `detail`, `instance`, `code`, `requestId` y `errors`. Jamás se exponen trazas, consultas SQL ni excepciones de base de datos (`503 SERVICE_UNAVAILABLE`).
- **Cero Placeholders:** Todo el código en este plan es definitivo y ejecutable sin `TODO`, `TBD` ni omisiones.

---

## Mapa de Archivos a Crear y Modificar

| Tipo | Archivo | Responsabilidad |
| :--- | :--- | :--- |
| **Create** | `backend/src/main/java/com/turnoshift/unavailability/domain/OrganizationTimezonePort.java` | Puerto de dominio para resolver la zona horaria de una organización. |
| **Create** | `backend/src/main/java/com/turnoshift/unavailability/infrastructure/adapter/OrganizationTimezoneAdapter.java` | Adaptador JDBC que lee `organizations.timezone_id` y devuelve `ZoneId`. |
| **Create** | `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityTemporalParser.java` | Validador y parser de los dos formatos mutuamente excluyentes con DST. |
| **Create** | `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/CreateUnavailabilityRequest.java` | DTO de entrada para creación de indisponibilidad. |
| **Create** | `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/UpdateUnavailabilityRequest.java` | DTO de entrada para actualización con `expectedLockVersion`. |
| **Create** | `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/CancelUnavailabilityRequest.java` | DTO de entrada para cancelación con `expectedLockVersion`. |
| **Create** | `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/EmployeeUnavailabilityResponse.java` | DTO de salida con atributos completos de la indisponibilidad. |
| **Create** | `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/UnavailabilityListResponse.java` | DTO envoltorio de lista (`{ "items": [ ... ] }`). |
| **Create** | `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityIdempotentCommand.java` | Implementación de `BusinessIdempotentCommand<EmployeeUnavailabilityResponse>`. |
| **Create** | `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityController.java` | Controlador REST con los 6 endpoints y manejadores Problem Details. |
| **Create** | `backend/src/main/java/com/turnoshift/unavailability/infrastructure/config/UnavailabilityConfiguration.java` | Configuración Spring `@Configuration` para inyección de casos de uso y adaptadores. |
| **Modify** | `backend/src/main/java/com/turnoshift/security/SecurityConfig.java` | Reglas de autorización en `SecurityFilterChain` para rutas de indisponibilidades. |
| **Create** | `backend/src/test/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityTemporalParserTest.java` | Pruebas unitarias de parsing, DST, validación mutua y cálculo UTC. |
| **Create** | `backend/src/test/java/com/turnoshift/unavailability/infrastructure/adapter/OrganizationTimezoneAdapterTest.java` | Pruebas unitarias del adaptador de zona horaria. |
| **Create** | `backend/src/test/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityIdempotentCommandTest.java` | Pruebas unitarias de huella canónica y serialización/deserialización de respuesta. |
| **Create** | `backend/src/test/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityControllerTest.java` | Pruebas MockMvc standalone de endpoints, validaciones, RN-013, Problem Details y replay. |
| **Create** | `backend/src/test/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityControllerIT.java` | Pruebas de integración Spring Boot + Testcontainers: roles, CSRF, multi-tenant y replay real. |

---

## Tareas de Implementación

### Tarea 1: Puerto y Adaptador de Zona Horaria de Organización

**Files:**
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/OrganizationTimezonePort.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/adapter/OrganizationTimezoneAdapter.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/infrastructure/adapter/OrganizationTimezoneAdapterTest.java`

**Interfaces:**
- Produces: `OrganizationTimezonePort.resolveZoneId(UUID organizationId): ZoneId`
- Consumes: `organizations.timezone_id` en base de datos SQL Server o vía repositorio de organización.

- [ ] **Paso 1: Escribir la prueba unitaria que falla**

```java
package com.turnoshift.unavailability.infrastructure.adapter;

import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.organization.domain.OrganizationView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: OrganizationTimezoneAdapter — Resolución de Zona Horaria")
class OrganizationTimezoneAdapterTest {

    @Mock
    private OrganizationConfigurationRepository repository;

    @Test
    @DisplayName("Resuelve ZoneId correctamente a partir de la configuración de la organización")
    void resuelveZoneIdValido() {
        UUID orgId = UUID.randomUUID();
        when(repository.findOrganizationById(orgId)).thenReturn(Optional.of(
                new OrganizationView(orgId, "Empresa Test", "America/Bogota", 0L)
        ));

        OrganizationTimezoneAdapter adapter = new OrganizationTimezoneAdapter(repository);
        ZoneId zoneId = adapter.resolveZoneId(orgId);

        assertThat(zoneId).isEqualTo(ZoneId.of("America/Bogota"));
    }

    @Test
    @DisplayName("Lanza excepción si la organización no existe")
    void lanzaExcepcionSiOrganizacionNoExiste() {
        UUID orgId = UUID.randomUUID();
        when(repository.findOrganizationById(orgId)).thenReturn(Optional.empty());

        OrganizationTimezoneAdapter adapter = new OrganizationTimezoneAdapter(repository);

        assertThatThrownBy(() -> adapter.resolveZoneId(orgId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Organización no encontrada");
    }

    @Test
    @DisplayName("Lanza excepción si el timezone_id configurado es inválido")
    void lanzaExcepcionSiTimezoneInvalido() {
        UUID orgId = UUID.randomUUID();
        when(repository.findOrganizationById(orgId)).thenReturn(Optional.of(
                new OrganizationView(orgId, "Empresa Test", "Zona/Invalida_123", 0L)
        ));

        OrganizationTimezoneAdapter adapter = new OrganizationTimezoneAdapter(repository);

        assertThatThrownBy(() -> adapter.resolveZoneId(orgId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Zona horaria no válida");
    }
}
```

- [ ] **Paso 2: Ejecutar la prueba para verificar que falla**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=OrganizationTimezoneAdapterTest" -B
```
Resultado esperado: Error de compilación (`OrganizationTimezonePort` y `OrganizationTimezoneAdapter` no encontradas).

- [ ] **Paso 3: Implementar el puerto y el adaptador**

Crear `backend/src/main/java/com/turnoshift/unavailability/domain/OrganizationTimezonePort.java`:
```java
package com.turnoshift.unavailability.domain;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Puerto de dominio para resolver la zona horaria oficial configurada de una organización.
 */
public interface OrganizationTimezonePort {

    /**
     * Resuelve el {@link ZoneId} de la organización especificada.
     *
     * @param organizationId Identificador único de la organización.
     * @return El {@link ZoneId} resuelto.
     * @throws IllegalStateException Si la organización no existe o la zona no es válida.
     */
    ZoneId resolveZoneId(UUID organizationId);
}
```

Crear `backend/src/main/java/com/turnoshift/unavailability/infrastructure/adapter/OrganizationTimezoneAdapter.java`:
```java
package com.turnoshift.unavailability.infrastructure.adapter;

import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.organization.domain.OrganizationView;
import com.turnoshift.unavailability.domain.OrganizationTimezonePort;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador que resuelve el huso horario oficial de una organización delegando
 * en el repositorio de configuración de organizaciones.
 */
public class OrganizationTimezoneAdapter implements OrganizationTimezonePort {

    private final OrganizationConfigurationRepository organizationConfigurationRepository;

    public OrganizationTimezoneAdapter(OrganizationConfigurationRepository organizationConfigurationRepository) {
        this.organizationConfigurationRepository = Objects.requireNonNull(
                organizationConfigurationRepository, "organizationConfigurationRepository no puede ser nulo");
    }

    @Override
    public ZoneId resolveZoneId(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");

        OrganizationView org = organizationConfigurationRepository.findOrganizationById(organizationId)
                .orElseThrow(() -> new IllegalStateException("Organización no encontrada: " + organizationId));

        try {
            return ZoneId.of(org.timezoneId());
        } catch (DateTimeException e) {
            throw new IllegalStateException("Zona horaria no válida en la organización: " + org.timezoneId(), e);
        }
    }
}
```

- [ ] **Paso 4: Ejecutar la prueba para verificar que pasa**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=OrganizationTimezoneAdapterTest" -B
```
Resultado esperado: PASS (3 pruebas exitosas).

---

### Tarea 2: Parser y Validador Temporal con Soporte DST (Formatos Mutuamente Excluyentes)

**Files:**
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityTemporalParser.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityTemporalParserTest.java`

**Interfaces:**
- Produces: `UnavailabilityTemporalParser.parse(String startsAt, String endsAt, Boolean allDay, String startDate, String endDate, ZoneId zoneId): ParsedInterval`
- Requisitos de validación:
  - Formato parcial: `startsAt` y `endsAt` válidos, `allDay == null || allDay == false`, `startDate == null`, `endDate == null`. `startsAt.isBefore(endsAt)`.
  - Formato día completo: `allDay == Boolean.TRUE`, `startDate` y `endDate` formato `YYYY-MM-DD`, `startsAt == null`, `endsAt == null`. `startDate.isBefore(endDate)`. Expansión UTC con `LocalDate.atStartOfDay(zoneId).toInstant()`.
  - Duración máxima: menor o igual a 365 días (`ChronoUnit.DAYS.between(start, end) <= 365`).
  - Cualquier violación lanza `InvalidUnavailabilityDataException` con mensajes descriptivos.

- [ ] **Paso 1: Escribir la prueba unitaria que falla**

```java
package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit: UnavailabilityTemporalParser — Validación y Expansión Temporal")
class UnavailabilityTemporalParserTest {

    private final ZoneId bogotaZone = ZoneId.of("America/Bogota"); // UTC-5 fijo
    private final ZoneId madridZone = ZoneId.of("Europe/Madrid"); // DST: UTC+1 invierno, UTC+2 verano
    private final ZoneId newYorkZone = ZoneId.of("America/New_York"); // DST: UTC-5 invierno, UTC-4 verano

    @Test
    @DisplayName("Formato parcial UTC válido: parsea instantes exactos")
    void formatoParcialValido() {
        var parsed = UnavailabilityTemporalParser.parse(
                "2026-10-15T14:00:00Z",
                "2026-10-15T18:00:00Z",
                null,
                null,
                null,
                bogotaZone
        );

        assertThat(parsed.startsAt()).isEqualTo(Instant.parse("2026-10-15T14:00:00Z"));
        assertThat(parsed.endsAt()).isEqualTo(Instant.parse("2026-10-15T18:00:00Z"));
    }

    @Test
    @DisplayName("Formato día completo válido: expande fechas locales con endDate exclusiva en America/Bogota (UTC-5)")
    void formatoDiaCompletoBogota() {
        var parsed = UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-10-15",
                "2026-10-17",
                bogotaZone
        );

        // 2026-10-15 00:00:00 en America/Bogota (-05:00) es 05:00:00 UTC
        assertThat(parsed.startsAt()).isEqualTo(Instant.parse("2026-10-15T05:00:00Z"));
        // 2026-10-17 00:00:00 en America/Bogota (-05:00) es 05:00:00 UTC (48 horas exactas)
        assertThat(parsed.endsAt()).isEqualTo(Instant.parse("2026-10-17T05:00:00Z"));
    }

    @Test
    @DisplayName("Día completo cruzando cambio de horario de verano en Madrid (DST spring forward, 23h)")
    void formatoDiaCompletoMadridDstSpring() {
        // En 2026, el cambio de hora en Europa es el domingo 29 de marzo (02:00 -> 03:00)
        var parsed = UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-03-29",
                "2026-03-30",
                madridZone
        );

        // 29 de marzo a medianoche en Madrid es UTC+1 -> 2026-03-28T23:00:00Z
        assertThat(parsed.startsAt()).isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
        // 30 de marzo a medianoche en Madrid ya es UTC+2 -> 2026-03-29T22:00:00Z (23 horas de diferencia)
        assertThat(parsed.endsAt()).isEqualTo(Instant.parse("2026-03-29T22:00:00Z"));
        assertThat(java.time.Duration.between(parsed.startsAt(), parsed.endsAt()).toHours()).isEqualTo(23);
    }

    @Test
    @DisplayName("Día completo cruzando cambio de horario de invierno en New York (DST fall back, 25h)")
    void formatoDiaCompletoNewYorkDstFall() {
        // En 2026, el cambio en EE.UU. ocurre el domingo 1 de noviembre (02:00 -> 01:00)
        var parsed = UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-11-01",
                "2026-11-02",
                newYorkZone
        );

        // 1 de noviembre a medianoche en NY es UTC-4 -> 2026-11-01T04:00:00Z
        assertThat(parsed.startsAt()).isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
        // 2 de noviembre a medianoche en NY ya es UTC-5 -> 2026-11-02T05:00:00Z (25 horas)
        assertThat(parsed.endsAt()).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"));
        assertThat(java.time.Duration.between(parsed.startsAt(), parsed.endsAt()).toHours()).isEqualTo(25);
    }

    @Test
    @DisplayName("Payload mixto: allDay true con startsAt presente es rechazado")
    void payloadMixtoRechazado() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                "2026-10-15T14:00:00Z",
                null,
                true,
                "2026-10-15",
                "2026-10-16",
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("No se permite mezclar el formato de rango parcial con el formato de día completo");
    }

    @Test
    @DisplayName("Payload incompleto: allDay true sin endDate es rechazado")
    void payloadIncompletoAllDaySinEndDate() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-10-15",
                null,
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("El formato de día completo requiere 'startDate' y 'endDate'");
    }

    @Test
    @DisplayName("Payload incompleto: formato parcial sin endsAt es rechazado")
    void payloadIncompletoParcialSinEndsAt() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                "2026-10-15T14:00:00Z",
                null,
                false,
                null,
                null,
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("El formato de rango parcial requiere 'startsAt' y 'endsAt'");
    }

    @Test
    @DisplayName("Cronología invertida en parcial: startsAt posterior a endsAt es rechazado")
    void cronologiaInvertidaParcial() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                "2026-10-15T18:00:00Z",
                "2026-10-15T14:00:00Z",
                false,
                null,
                null,
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("startsAt debe ser anterior a endsAt");
    }

    @Test
    @DisplayName("Cronología invertida en día completo: startDate igual a endDate es rechazado (endDate es exclusiva)")
    void cronologiaInvertidaDiaCompleto() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-10-15",
                "2026-10-15",
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("startDate debe ser anterior a endDate");
    }

    @Test
    @DisplayName("Rango mayor a 365 días es rechazado")
    void rangoMayorA365DiasRechazado() {
        assertThatThrownBy(() -> UnavailabilityTemporalParser.parse(
                null,
                null,
                true,
                "2026-01-01",
                "2027-01-05",
                bogotaZone
        )).isInstanceOf(InvalidUnavailabilityDataException.class)
          .hasMessageContaining("El rango de indisponibilidad no puede exceder 365 días");
    }
}
```

- [ ] **Paso 2: Ejecutar la prueba para verificar que falla**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityTemporalParserTest" -B
```
Resultado esperado: Error de compilación (`UnavailabilityTemporalParser` no existe).

- [ ] **Paso 3: Implementar `UnavailabilityTemporalParser`**

Crear `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityTemporalParser.java`:
```java
package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Validador y analizador de formatos temporales para la entrada de indisponibilidades.
 *
 * <p>Asegura la exclusión mutua estricta entre el formato parcial (instantes UTC)
 * y el formato de día completo (fechas locales de calendario expandidas a UTC mediante la zona horaria del tenant).
 */
public final class UnavailabilityTemporalParser {

    private static final long MAX_ALLOWED_DAYS = 365;

    private UnavailabilityTemporalParser() {
        // Utilidad estática
    }

    public record ParsedInterval(Instant startsAt, Instant endsAt) {
        public ParsedInterval {
            Objects.requireNonNull(startsAt, "startsAt no puede ser nulo");
            Objects.requireNonNull(endsAt, "endsAt no puede ser nulo");
            if (!startsAt.isBefore(endsAt)) {
                throw new InvalidUnavailabilityDataException("startsAt debe ser anterior a endsAt");
            }
        }
    }

    public static ParsedInterval parse(
            String startsAtRaw,
            String endsAtRaw,
            Boolean allDay,
            String startDateRaw,
            String endDateRaw,
            ZoneId zoneId
    ) {
        Objects.requireNonNull(zoneId, "zoneId no puede ser nulo");

        boolean isAllDay = Boolean.TRUE.equals(allDay);
        boolean hasUtcFields = (startsAtRaw != null && !startsAtRaw.isBlank())
                || (endsAtRaw != null && !endsAtRaw.isBlank());
        boolean hasLocalFields = (startDateRaw != null && !startDateRaw.isBlank())
                || (endDateRaw != null && !endDateRaw.isBlank());

        // Regla 1: Exclusión mutua de ambos formatos
        if (isAllDay && hasUtcFields) {
            throw new InvalidUnavailabilityDataException(
                    "No se permite mezclar el formato de rango parcial con el formato de día completo ('allDay': true con 'startsAt' o 'endsAt').");
        }
        if (!isAllDay && hasLocalFields) {
            throw new InvalidUnavailabilityDataException(
                    "Los campos 'startDate' y 'endDate' solo se permiten cuando 'allDay' es true.");
        }

        Instant startsAt;
        Instant endsAt;

        if (isAllDay) {
            if (startDateRaw == null || startDateRaw.isBlank() || endDateRaw == null || endDateRaw.isBlank()) {
                throw new InvalidUnavailabilityDataException(
                        "El formato de día completo requiere 'startDate' y 'endDate' con formato YYYY-MM-DD.");
            }

            LocalDate startDate;
            LocalDate endDate;
            try {
                startDate = LocalDate.parse(startDateRaw.trim());
                endDate = LocalDate.parse(endDateRaw.trim());
            } catch (DateTimeParseException e) {
                throw new InvalidUnavailabilityDataException(
                        "Las fechas 'startDate' y 'endDate' deben tener formato ISO-8601 (YYYY-MM-DD).", e);
            }

            if (!startDate.isBefore(endDate)) {
                throw new InvalidUnavailabilityDataException(
                        "startDate debe ser anterior a endDate (la fecha fin es exclusiva).");
            }

            try {
                startsAt = startDate.atStartOfDay(zoneId).toInstant();
                endsAt = endDate.atStartOfDay(zoneId).toInstant();
            } catch (DateTimeException e) {
                throw new InvalidUnavailabilityDataException(
                        "Error expandiendo fechas locales a UTC en la zona horaria: " + zoneId.getId(), e);
            }

        } else {
            if (startsAtRaw == null || startsAtRaw.isBlank() || endsAtRaw == null || endsAtRaw.isBlank()) {
                throw new InvalidUnavailabilityDataException(
                        "El formato de rango parcial requiere 'startsAt' y 'endsAt' en formato UTC ISO-8601.");
            }

            try {
                startsAt = Instant.parse(startsAtRaw.trim());
                endsAt = Instant.parse(endsAtRaw.trim());
            } catch (DateTimeParseException e) {
                throw new InvalidUnavailabilityDataException(
                        "Los campos 'startsAt' y 'endsAt' deben tener formato ISO-8601 UTC válido (ej. 2026-10-15T14:00:00Z).", e);
            }

            if (!startsAt.isBefore(endsAt)) {
                throw new InvalidUnavailabilityDataException(
                        "startsAt debe ser anterior a endsAt.");
            }
        }

        long daysBetween = ChronoUnit.DAYS.between(startsAt, endsAt);
        if (daysBetween > MAX_ALLOWED_DAYS) {
            throw new InvalidUnavailabilityDataException(
                    "El rango de indisponibilidad no puede exceder 365 días (días solicitados: " + daysBetween + ").");
        }

        return new ParsedInterval(startsAt, endsAt);
    }
}
```

- [ ] **Paso 4: Ejecutar la prueba para verificar que pasa**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityTemporalParserTest" -B
```
Resultado esperado: PASS (10 pruebas exitosas).

---

### Tarea 3: DTOs de Entrada y Salida

**Files:**
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/CreateUnavailabilityRequest.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/UpdateUnavailabilityRequest.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/CancelUnavailabilityRequest.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/EmployeeUnavailabilityResponse.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/UnavailabilityListResponse.java`

**Interfaces:**
- `CreateUnavailabilityRequest`: acepta campos de Formato A y B, motivo opcional y nota privada.
- `UpdateUnavailabilityRequest`: incluye además `expectedLockVersion` (obligatorio).
- `CancelUnavailabilityRequest`: incluye `expectedLockVersion` (obligatorio).
- `EmployeeUnavailabilityResponse`: proyección inmutable con mapeo desde la entidad de dominio `EmployeeUnavailability`.
- `UnavailabilityListResponse`: envoltura con clave `items`.

- [ ] **Paso 1: Implementar los DTOs de petición y respuesta**

Crear `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/CreateUnavailabilityRequest.java`:
```java
package com.turnoshift.unavailability.infrastructure.web.dto;

public record CreateUnavailabilityRequest(
        String startsAt,
        String endsAt,
        Boolean allDay,
        String startDate,
        String endDate,
        String reasonCode,
        String restrictedNote
) {}
```

Crear `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/UpdateUnavailabilityRequest.java`:
```java
package com.turnoshift.unavailability.infrastructure.web.dto;

public record UpdateUnavailabilityRequest(
        String startsAt,
        String endsAt,
        Boolean allDay,
        String startDate,
        String endDate,
        String reasonCode,
        String restrictedNote,
        Long expectedLockVersion
) {}
```

Crear `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/CancelUnavailabilityRequest.java`:
```java
package com.turnoshift.unavailability.infrastructure.web.dto;

public record CancelUnavailabilityRequest(
        Long expectedLockVersion
) {}
```

Crear `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/EmployeeUnavailabilityResponse.java`:
```java
package com.turnoshift.unavailability.infrastructure.web.dto;

import com.turnoshift.unavailability.domain.EmployeeUnavailability;

import java.time.Instant;
import java.util.UUID;

public record EmployeeUnavailabilityResponse(
        UUID id,
        UUID employeeId,
        Instant startsAt,
        Instant endsAt,
        String reasonCode,
        String restrictedNote,
        boolean cancelled,
        Instant cancelledAt,
        UUID cancelledByUserId,
        UUID createdByUserId,
        long lockVersion,
        Instant createdAt,
        Instant updatedAt
) {
    public static EmployeeUnavailabilityResponse from(EmployeeUnavailability u) {
        return new EmployeeUnavailabilityResponse(
                u.id(),
                u.employeeId(),
                u.interval().startsAt(),
                u.interval().endsAt(),
                u.reasonCode().map(Enum::name).orElse(null),
                u.restrictedNote() != null ? u.restrictedNote().value() : null,
                u.isCancelled(),
                u.cancellationInfo() != null ? u.cancellationInfo().cancelledAt() : null,
                u.cancellationInfo() != null ? u.cancellationInfo().cancelledByUserId() : null,
                u.createdByUserId(),
                u.lockVersion(),
                u.createdAt(),
                u.updatedAt()
        );
    }
}
```

Crear `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/dto/UnavailabilityListResponse.java`:
```java
package com.turnoshift.unavailability.infrastructure.web.dto;

import java.util.List;

public record UnavailabilityListResponse(
        List<EmployeeUnavailabilityResponse> items
) {}
```

- [ ] **Paso 2: Ejecutar compilación de verificación**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test-compile -B
```
Resultado esperado: BUILD SUCCESS.

---

### Tarea 4: Comando de Idempotencia Tipado (`UnavailabilityIdempotentCommand`)

**Files:**
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityIdempotentCommand.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityIdempotentCommandTest.java`

**Interfaces:**
- `UnavailabilityIdempotentCommand`: implementa directamente `BusinessIdempotentCommand<EmployeeUnavailabilityResponse>`, serializa a JSON y deserializa respuestas `EmployeeUnavailabilityResponse` persistidas en `api_operations`. Provee metadatos de método HTTP, ruta y payload canónico. No persiste resultados de error (los errores de negocio se manejan directamente como Problem Details vía `@ExceptionHandler` y fallos transaccionales estándar).

- [ ] **Paso 1: Escribir la prueba unitaria que falla**

```java
package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.unavailability.infrastructure.web.dto.EmployeeUnavailabilityResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit: UnavailabilityIdempotentCommand — Comportamiento de Idempotencia Tipada")
class UnavailabilityIdempotentCommandTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID idemKey = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();
    private final UUID unavId = UUID.randomUUID();

    @Test
    @DisplayName("Comando Create: estado 201 en éxito y serialización/deserialización correcta")
    void createSuccessSerialization() {
        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavId, empId, Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"),
                "MEDICO", "Cita médica", false, null, null, userId, 0L, Instant.now(), Instant.now()
        );

        UnavailabilityIdempotentCommand cmd = new UnavailabilityIdempotentCommand(
                orgId, userId, idemKey, "POST", "/api/v1/employees/" + empId + "/unavailabilities",
                "canonicalPayload", true, objectMapper
        );

        assertThat(cmd.httpStatus(resp)).isEqualTo(201);
        assertThat(cmd.outcome(resp)).isEqualTo("SUCCEEDED");
        assertThat(cmd.responseContentType(resp)).isEqualTo("application/json");

        String serialized = cmd.sanitizeResponseBody(resp);
        EmployeeUnavailabilityResponse deserialized = cmd.deserializeResponseBody(serialized);

        assertThat(deserialized.id()).isEqualTo(unavId);
        assertThat(deserialized.reasonCode()).isEqualTo("MEDICO");
        assertThat(deserialized.restrictedNote()).isEqualTo("Cita médica");
    }

    @Test
    @DisplayName("Comando Update: estado 200 en éxito")
    void updateSuccessHttpStatus() {
        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavId, empId, Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"),
                "VACACIONES", null, false, null, null, userId, 1L, Instant.now(), Instant.now()
        );

        UnavailabilityIdempotentCommand cmd = new UnavailabilityIdempotentCommand(
                orgId, userId, idemKey, "PUT", "/api/v1/unavailabilities/" + unavId,
                "canonicalPayload", false, objectMapper
        );

        assertThat(cmd.httpStatus(resp)).isEqualTo(200);
        assertThat(cmd.outcome(resp)).isEqualTo("SUCCEEDED");
        assertThat(cmd.method()).isEqualTo("PUT");
        assertThat(cmd.path()).isEqualTo("/api/v1/unavailabilities/" + unavId);
    }
}
```

- [ ] **Paso 2: Ejecutar la prueba para verificar que falla**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityIdempotentCommandTest" -B
```
Resultado esperado: Error de compilación (`UnavailabilityIdempotentCommand` no encontrada).

- [ ] **Paso 3: Implementar `UnavailabilityIdempotentCommand`**

Crear `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityIdempotentCommand.java`:
```java
package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.security.idempotency.BusinessIdempotentCommand;
import com.turnoshift.unavailability.infrastructure.web.dto.EmployeeUnavailabilityResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.UUID;

/**
 * Comando idempotente empresarial para mutaciones sobre indisponibilidades.
 * Gestiona operaciones sobre la tabla api_operations con respuestas tipadas en EmployeeUnavailabilityResponse.
 */
public class UnavailabilityIdempotentCommand implements BusinessIdempotentCommand<EmployeeUnavailabilityResponse> {

    private final UUID organizationId;
    private final UUID actorUserId;
    private final UUID idempotencyKey;
    private final String method;
    private final String path;
    private final String canonicalPayload;
    private final boolean isCreate;
    private final ObjectMapper objectMapper;

    public UnavailabilityIdempotentCommand(
            UUID organizationId,
            UUID actorUserId,
            UUID idempotencyKey,
            String method,
            String path,
            String canonicalPayload,
            boolean isCreate,
            ObjectMapper objectMapper
    ) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId no puede ser nulo");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey no puede ser nulo");
        this.method = Objects.requireNonNull(method, "method no puede ser nulo");
        this.path = Objects.requireNonNull(path, "path no puede ser nulo");
        this.canonicalPayload = Objects.requireNonNull(canonicalPayload, "canonicalPayload no puede ser nulo");
        this.isCreate = isCreate;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    @Override
    public UUID organizationId() {
        return organizationId;
    }

    @Override
    public UUID actorUserId() {
        return actorUserId;
    }

    @Override
    public UUID idempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String schemaVersion() {
        return "1.0";
    }

    @Override
    public String canonicalPayload() {
        return canonicalPayload;
    }

    @Override
    public int httpStatus(EmployeeUnavailabilityResponse response) {
        return isCreate ? 201 : 200;
    }

    @Override
    public String outcome(EmployeeUnavailabilityResponse response) {
        return "SUCCEEDED";
    }

    @Override
    public String responseContentType() {
        return "application/json";
    }

    @Override
    public String responseContentType(EmployeeUnavailabilityResponse response) {
        return "application/json";
    }

    @Override
    public String sanitizeResponseBody(EmployeeUnavailabilityResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Error sanitizando respuesta de indisponibilidad", e);
        }
    }

    @Override
    public EmployeeUnavailabilityResponse deserializeResponseBody(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, EmployeeUnavailabilityResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializando cuerpo persistido de indisponibilidad", e);
        }
    }
}
```

- [ ] **Paso 4: Ejecutar la prueba para verificar que pasa**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityIdempotentCommandTest" -B
```
Resultado esperado: PASS (2 pruebas exitosas).

---

### Tarea 5: Configuración de Seguridad en Spring Security

**Files:**
- Modify: `backend/src/main/java/com/turnoshift/security/SecurityConfig.java:160-175`

**Interfaces:**
- Autoriza rutas de indisponibilidades:
  - `GET /api/v1/employees/*/unavailabilities`, `GET /api/v1/unavailabilities`, `GET /api/v1/unavailabilities/*` para `ADMINISTRADOR` y `SUPERVISOR`.
  - `POST /api/v1/employees/*/unavailabilities`, `POST /api/v1/unavailabilities/*/cancel` para `ADMINISTRADOR` y `SUPERVISOR`.
  - `PUT /api/v1/unavailabilities/*` para `ADMINISTRADOR` y `SUPERVISOR`.
- IMPORTANTE: Estas reglas deben colocarse **antes** de `.requestMatchers(HttpMethod.POST, "/api/v1/employees", "/api/v1/employees/**").hasAuthority("ROLE_ADMINISTRADOR")` para que el `ROLE_SUPERVISOR` no sea bloqueado por el comodín de empleados.

- [ ] **Paso 1: Modificar `SecurityConfig.java`**

En `backend/src/main/java/com/turnoshift/security/SecurityConfig.java`, insertar inmediatamente antes de la sección `// Rutas de colaboradores / empleados (HU-02)`:

```java
                // Rutas de indisponibilidades de personal (HU-10 / I2.4)
                // Se ubican antes del wildcard de empleados para que SUPERVISOR tenga acceso a POST /employees/*/unavailabilities
                .requestMatchers(HttpMethod.GET,   "/api/v1/employees/*/unavailabilities",
                                                   "/api/v1/unavailabilities",
                                                   "/api/v1/unavailabilities/*").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR")
                .requestMatchers(HttpMethod.POST,  "/api/v1/employees/*/unavailabilities",
                                                   "/api/v1/unavailabilities/*/cancel").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR")
                .requestMatchers(HttpMethod.PUT,   "/api/v1/unavailabilities/*").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR")
                .requestMatchers("/api/v1/unavailabilities/**").hasAnyAuthority("ROLE_ADMINISTRADOR", "ROLE_SUPERVISOR")
```

- [ ] **Paso 2: Verificar compilación**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test-compile -B
```
Resultado esperado: BUILD SUCCESS.

---

### Tarea 6: Inyección de Dependencias Spring (`UnavailabilityConfiguration`)

**Files:**
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/config/UnavailabilityConfiguration.java`

**Interfaces:**
- Declara como beans de Spring:
  - `TeamEmployeeValidationAdapter` (implementando `EmployeeValidationPort`).
  - `OrganizationTimezoneAdapter` (implementando `OrganizationTimezonePort`).
  - `CreateUnavailabilityUseCase`
  - `GetUnavailabilityUseCase`
  - `ListEmployeeUnavailabilitiesUseCase`
  - `ListOrganizationUnavailabilitiesUseCase`
  - `UpdateUnavailabilityUseCase`
  - `CancelUnavailabilityUseCase`
  - `UnavailabilityConflictQueryService`

- [ ] **Paso 1: Crear `UnavailabilityConfiguration.java`**

```java
package com.turnoshift.unavailability.infrastructure.config;

import com.turnoshift.organization.domain.OrganizationConfigurationRepository;
import com.turnoshift.team.domain.EmployeeRepository;
import com.turnoshift.unavailability.application.CancelUnavailabilityUseCase;
import com.turnoshift.unavailability.application.CreateUnavailabilityUseCase;
import com.turnoshift.unavailability.application.GetUnavailabilityUseCase;
import com.turnoshift.unavailability.application.ListEmployeeUnavailabilitiesUseCase;
import com.turnoshift.unavailability.application.ListOrganizationUnavailabilitiesUseCase;
import com.turnoshift.unavailability.application.UnavailabilityConflictQueryService;
import com.turnoshift.unavailability.application.UpdateUnavailabilityUseCase;
import com.turnoshift.unavailability.domain.EmployeeUnavailabilityRepository;
import com.turnoshift.unavailability.domain.EmployeeValidationPort;
import com.turnoshift.unavailability.domain.OrganizationTimezonePort;
import com.turnoshift.unavailability.infrastructure.adapter.OrganizationTimezoneAdapter;
import com.turnoshift.unavailability.infrastructure.adapter.TeamEmployeeValidationAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Configuración Spring para los casos de uso y adaptadores del módulo de indisponibilidades.
 * Permite que las clases de dominio y aplicación se mantengan libres de dependencias de Spring.
 */
@Configuration
public class UnavailabilityConfiguration {

    @Bean
    public EmployeeValidationPort unavailabilityEmployeeValidationPort(EmployeeRepository employeeRepository) {
        return new TeamEmployeeValidationAdapter(employeeRepository);
    }

    @Bean
    public OrganizationTimezonePort organizationTimezonePort(OrganizationConfigurationRepository organizationConfigurationRepository) {
        return new OrganizationTimezoneAdapter(organizationConfigurationRepository);
    }

    @Bean
    public CreateUnavailabilityUseCase createUnavailabilityUseCase(
            EmployeeUnavailabilityRepository repository,
            EmployeeValidationPort employeeValidationPort,
            Clock clock
    ) {
        return new CreateUnavailabilityUseCase(repository, employeeValidationPort, clock);
    }

    @Bean
    public GetUnavailabilityUseCase getUnavailabilityUseCase(EmployeeUnavailabilityRepository repository) {
        return new GetUnavailabilityUseCase(repository);
    }

    @Bean
    public ListEmployeeUnavailabilitiesUseCase listEmployeeUnavailabilitiesUseCase(
            EmployeeUnavailabilityRepository repository,
            EmployeeValidationPort employeeValidationPort
    ) {
        return new ListEmployeeUnavailabilitiesUseCase(repository, employeeValidationPort);
    }

    @Bean
    public ListOrganizationUnavailabilitiesUseCase listOrganizationUnavailabilitiesUseCase(
            EmployeeUnavailabilityRepository repository
    ) {
        return new ListOrganizationUnavailabilitiesUseCase(repository);
    }

    @Bean
    public UpdateUnavailabilityUseCase updateUnavailabilityUseCase(
            EmployeeUnavailabilityRepository repository,
            Clock clock
    ) {
        return new UpdateUnavailabilityUseCase(repository, clock);
    }

    @Bean
    public CancelUnavailabilityUseCase cancelUnavailabilityUseCase(
            EmployeeUnavailabilityRepository repository,
            Clock clock
    ) {
        return new CancelUnavailabilityUseCase(repository, clock);
    }

    @Bean
    public UnavailabilityConflictQueryService unavailabilityConflictQueryService(
            EmployeeUnavailabilityRepository repository
    ) {
        return new UnavailabilityConflictQueryService(repository);
    }
}
```

- [ ] **Paso 2: Verificar compilación**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test-compile -B
```
Resultado esperado: BUILD SUCCESS.

---

### Tarea 7: Controlador REST `UnavailabilityController` — Endpoints de Lectura

**Files:**
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityController.java` (endpoints de lectura, estructura base y manejadores de excepción Problem Details)
- Test: `backend/src/test/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityControllerTest.java` (sección lectura)

**Interfaces:**
- Endpoints implementados en esta tarea:
  - `GET /api/v1/employees/{employeeId}/unavailabilities`
  - `GET /api/v1/unavailabilities`
  - `GET /api/v1/unavailabilities/{unavailabilityId}`
- Manejadores de excepción RFC 9457:
  - 401 `SESSION_REQUIRED`
  - 404 `EMPLOYEE_NOT_FOUND`
  - 404 `UNAVAILABILITY_NOT_FOUND`
  - 400 `VALIDATION_FAILED`
  - 503 `SERVICE_UNAVAILABLE`

- [ ] **Paso 1: Escribir las pruebas MockMvc para los endpoints de lectura**

Crear `backend/src/test/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityControllerTest.java`:
```java
package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.security.idempotency.BusinessIdempotentOperationExecutor;
import com.turnoshift.unavailability.application.*;
import com.turnoshift.unavailability.domain.*;
import com.turnoshift.unavailability.domain.exceptions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit: UnavailabilityController — API HTTP de Indisponibilidades")
class UnavailabilityControllerTest {

    @Mock private CreateUnavailabilityUseCase createUnavailabilityUseCase;
    @Mock private GetUnavailabilityUseCase getUnavailabilityUseCase;
    @Mock private ListEmployeeUnavailabilitiesUseCase listEmployeeUnavailabilitiesUseCase;
    @Mock private ListOrganizationUnavailabilitiesUseCase listOrganizationUnavailabilitiesUseCase;
    @Mock private UpdateUnavailabilityUseCase updateUnavailabilityUseCase;
    @Mock private CancelUnavailabilityUseCase cancelUnavailabilityUseCase;
    @Mock private OrganizationTimezonePort organizationTimezonePort;
    @Mock private BusinessIdempotentOperationExecutor executor;

    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID orgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID empId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID unavId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final Instant now = Instant.parse("2026-09-25T12:00:00Z");

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        UnavailabilityController controller = new UnavailabilityController(
                createUnavailabilityUseCase,
                getUnavailabilityUseCase,
                listEmployeeUnavailabilitiesUseCase,
                listOrganizationUnavailabilitiesUseCase,
                updateUnavailabilityUseCase,
                cancelUnavailabilityUseCase,
                organizationTimezonePort,
                executor,
                objectMapper
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        authenticateAs("ADMINISTRADOR");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                userId, orgId, "admin@test.example", "Admin Test", role, null, 1L, "Org Test", "America/Bogota"
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                identity, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("Sin sesión autenticada: devuelve 401 SESSION_REQUIRED")
    void sinSesion_devuelve401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/unavailabilities").param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-10T00:00:00Z"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("GET /employees/{id}/unavailabilities: deriva tenant y devuelve lista 200")
    void getByEmployee_exito() throws Exception {
        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                unavId, orgId, empId,
                TimeInterval.of(Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-16T00:00:00Z")),
                UnavailabilityReason.VACACIONES,
                RestrictedNote.of("Vacaciones"),
                null, 0L, now, now, userId
        );
        when(listEmployeeUnavailabilitiesUseCase.execute(orgId, empId, false)).thenReturn(List.of(unav));

        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items[0].id").value(unavId.toString()))
                .andExpect(jsonPath("$.items[0].employeeId").value(empId.toString()))
                .andExpect(jsonPath("$.items[0].reasonCode").value("VACACIONES"))
                .andExpect(jsonPath("$.items[0].restrictedNote").value("Vacaciones"))
                .andExpect(jsonPath("$.items[0].cancelled").value(false));

        verify(listEmployeeUnavailabilitiesUseCase).execute(orgId, empId, false);
    }

    @Test
    @DisplayName("GET /employees/{id}/unavailabilities: colaborador no encontrado responde 404 EMPLOYEE_NOT_FOUND")
    void getByEmployee_notFound() throws Exception {
        when(listEmployeeUnavailabilitiesUseCase.execute(orgId, empId, false))
                .thenThrow(new EmployeeNotFoundException("Colaborador no encontrado"));

        mockMvc.perform(get("/api/v1/employees/" + empId + "/unavailabilities"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /unavailabilities: rango global válido responde 200")
    void getOrgUnavailabilities_exito() throws Exception {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-31T00:00:00Z");

        when(listOrganizationUnavailabilitiesUseCase.execute(orgId, from, to, false)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/unavailabilities")
                        .param("from", "2026-10-01T00:00:00Z")
                        .param("to", "2026-10-31T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("GET /unavailabilities: falta parámetro 'to' responde 400 VALIDATION_FAILED")
    void getOrgUnavailabilities_faltaTo() throws Exception {
        mockMvc.perform(get("/api/v1/unavailabilities").param("from", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /unavailabilities/{id}: encontrada responde 200")
    void getById_exito() throws Exception {
        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                unavId, orgId, empId,
                TimeInterval.of(Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-16T00:00:00Z")),
                UnavailabilityReason.MEDICO, null, null, 0L, now, now, userId
        );
        when(getUnavailabilityUseCase.execute(orgId, unavId)).thenReturn(unav);

        mockMvc.perform(get("/api/v1/unavailabilities/" + unavId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(unavId.toString()))
                .andExpect(jsonPath("$.reasonCode").value("MEDICO"));
    }

    @Test
    @DisplayName("GET /unavailabilities/{id}: no encontrada responde 404 UNAVAILABILITY_NOT_FOUND")
    void getById_noEncontrada() throws Exception {
        when(getUnavailabilityUseCase.execute(orgId, unavId))
                .thenThrow(new UnavailabilityNotFoundException("Indisponibilidad no encontrada"));

        mockMvc.perform(get("/api/v1/unavailabilities/" + unavId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAVAILABILITY_NOT_FOUND"));
    }
}
```

- [ ] **Paso 2: Ejecutar la prueba para verificar que falla**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityControllerTest" -B
```
Resultado esperado: Error de compilación (`UnavailabilityController` no existe).

- [ ] **Paso 3: Implementar la base de `UnavailabilityController` con endpoints de lectura**

Crear `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityController.java`:
```java
package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.identity.domain.AuthenticatedIdentity;
import com.turnoshift.security.SecurityProblemHandlers.ProblemDetailDto;
import com.turnoshift.security.idempotency.BusinessFingerprintCalculator;
import com.turnoshift.security.idempotency.BusinessIdempotencyDataAccessException;
import com.turnoshift.security.idempotency.BusinessIdempotencyResult;
import com.turnoshift.security.idempotency.BusinessIdempotentOperationExecutor;
import com.turnoshift.unavailability.application.*;
import com.turnoshift.unavailability.domain.*;
import com.turnoshift.unavailability.domain.exceptions.*;
import com.turnoshift.unavailability.infrastructure.persistence.UnavailabilityDataAccessException;
import com.turnoshift.unavailability.infrastructure.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
public class UnavailabilityController {

    private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
            MediaType.parseMediaType("application/problem+json;charset=UTF-8");

    private final CreateUnavailabilityUseCase createUnavailabilityUseCase;
    private final GetUnavailabilityUseCase getUnavailabilityUseCase;
    private final ListEmployeeUnavailabilitiesUseCase listEmployeeUnavailabilitiesUseCase;
    private final ListOrganizationUnavailabilitiesUseCase listOrganizationUnavailabilitiesUseCase;
    private final UpdateUnavailabilityUseCase updateUnavailabilityUseCase;
    private final CancelUnavailabilityUseCase cancelUnavailabilityUseCase;
    private final OrganizationTimezonePort organizationTimezonePort;
    private final BusinessIdempotentOperationExecutor executor;
    private final ObjectMapper objectMapper;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public UnavailabilityController(
            CreateUnavailabilityUseCase createUnavailabilityUseCase,
            GetUnavailabilityUseCase getUnavailabilityUseCase,
            ListEmployeeUnavailabilitiesUseCase listEmployeeUnavailabilitiesUseCase,
            ListOrganizationUnavailabilitiesUseCase listOrganizationUnavailabilitiesUseCase,
            UpdateUnavailabilityUseCase updateUnavailabilityUseCase,
            CancelUnavailabilityUseCase cancelUnavailabilityUseCase,
            OrganizationTimezonePort organizationTimezonePort,
            BusinessIdempotentOperationExecutor executor,
            ObjectMapper objectMapper
    ) {
        this.createUnavailabilityUseCase = Objects.requireNonNull(createUnavailabilityUseCase, "createUnavailabilityUseCase no puede ser nulo");
        this.getUnavailabilityUseCase = Objects.requireNonNull(getUnavailabilityUseCase, "getUnavailabilityUseCase no puede ser nulo");
        this.listEmployeeUnavailabilitiesUseCase = Objects.requireNonNull(listEmployeeUnavailabilitiesUseCase, "listEmployeeUnavailabilitiesUseCase no puede ser nulo");
        this.listOrganizationUnavailabilitiesUseCase = Objects.requireNonNull(listOrganizationUnavailabilitiesUseCase, "listOrganizationUnavailabilitiesUseCase no puede ser nulo");
        this.updateUnavailabilityUseCase = Objects.requireNonNull(updateUnavailabilityUseCase, "updateUnavailabilityUseCase no puede ser nulo");
        this.cancelUnavailabilityUseCase = Objects.requireNonNull(cancelUnavailabilityUseCase, "cancelUnavailabilityUseCase no puede ser nulo");
        this.organizationTimezonePort = Objects.requireNonNull(organizationTimezonePort, "organizationTimezonePort no puede ser nulo");
        this.executor = Objects.requireNonNull(executor, "executor no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    // =========================================================================
    // 1. GET /api/v1/employees/{employeeId}/unavailabilities
    // =========================================================================

    @GetMapping("/api/v1/employees/{employeeId}/unavailabilities")
    public ResponseEntity<?> getEmployeeUnavailabilities(
            @PathVariable("employeeId") String employeeIdParam,
            @RequestParam(name = "from", required = false) String fromParam,
            @RequestParam(name = "to", required = false) String toParam,
            @RequestParam(name = "includeCancelled", required = false, defaultValue = "false") boolean includeCancelled,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID employeeId = parseUuid(employeeIdParam);
        if (employeeId == null) {
            return validationFailedResponse(httpRequest, "El identificador del colaborador debe ser un UUID válido.");
        }

        try {
            List<EmployeeUnavailability> list;
            boolean hasFrom = fromParam != null && !fromParam.isBlank();
            boolean hasTo = toParam != null && !toParam.isBlank();

            if (hasFrom != hasTo) {
                return validationFailedResponse(httpRequest, "Los parámetros 'from' y 'to' deben proporcionarse simultáneamente si se filtra por rango.");
            }

            if (hasFrom) {
                Instant from = parseInstant(fromParam);
                Instant to = parseInstant(toParam);
                if (from == null || to == null) {
                    return validationFailedResponse(httpRequest, "Los parámetros 'from' y 'to' deben ser instantes ISO-8601 UTC válidos.");
                }
                if (!from.isBefore(to)) {
                    return validationFailedResponse(httpRequest, "El parámetro 'from' debe ser estrictamente anterior a 'to'.");
                }
                list = listEmployeeUnavailabilitiesUseCase.execute(identity.organizationId(), employeeId, from, to, includeCancelled);
            } else {
                list = listEmployeeUnavailabilitiesUseCase.execute(identity.organizationId(), employeeId, includeCancelled);
            }

            List<EmployeeUnavailabilityResponse> items = list.stream()
                    .map(EmployeeUnavailabilityResponse::from)
                    .toList();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UnavailabilityListResponse(items));

        } catch (EmployeeNotFoundException e) {
            return notFoundResponse(httpRequest, "El colaborador solicitado no existe o no pertenece a la organización.", "EMPLOYEE_NOT_FOUND");
        }
    }

    // =========================================================================
    // 2. GET /api/v1/unavailabilities
    // =========================================================================

    @GetMapping("/api/v1/unavailabilities")
    public ResponseEntity<?> getOrganizationUnavailabilities(
            @RequestParam(name = "from") String fromParam,
            @RequestParam(name = "to") String toParam,
            @RequestParam(name = "includeCancelled", required = false, defaultValue = "false") boolean includeCancelled,
            @RequestParam(name = "employeeId", required = false) String employeeIdParam,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        if (fromParam == null || fromParam.isBlank() || toParam == null || toParam.isBlank()) {
            return validationFailedResponse(httpRequest, "Los parámetros 'from' y 'to' son obligatorios para la consulta global.");
        }

        Instant from = parseInstant(fromParam);
        Instant to = parseInstant(toParam);
        if (from == null || to == null) {
            return validationFailedResponse(httpRequest, "Los parámetros 'from' y 'to' deben ser instantes ISO-8601 UTC válidos.");
        }
        if (!from.isBefore(to)) {
            return validationFailedResponse(httpRequest, "El parámetro 'from' debe ser estrictamente anterior a 'to'.");
        }

        long days = ChronoUnit.DAYS.between(from, to);
        if (days > 92) {
            return validationFailedResponse(httpRequest, "El rango de consulta organizacional no puede exceder 92 días.");
        }

        UUID filterEmployeeId = null;
        if (employeeIdParam != null && !employeeIdParam.isBlank()) {
            filterEmployeeId = parseUuid(employeeIdParam);
            if (filterEmployeeId == null) {
                return validationFailedResponse(httpRequest, "El parámetro 'employeeId' debe ser un UUID válido.");
            }
        }

        try {
            List<EmployeeUnavailability> list;
            if (filterEmployeeId != null) {
                list = listEmployeeUnavailabilitiesUseCase.execute(identity.organizationId(), filterEmployeeId, from, to, includeCancelled);
            } else {
                list = listOrganizationUnavailabilitiesUseCase.execute(identity.organizationId(), from, to, includeCancelled);
            }

            List<EmployeeUnavailabilityResponse> items = list.stream()
                    .map(EmployeeUnavailabilityResponse::from)
                    .toList();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UnavailabilityListResponse(items));

        } catch (EmployeeNotFoundException e) {
            return notFoundResponse(httpRequest, "El colaborador solicitado no existe o no pertenece a la organización.", "EMPLOYEE_NOT_FOUND");
        }
    }

    // =========================================================================
    // 3. GET /api/v1/unavailabilities/{unavailabilityId}
    // =========================================================================

    @GetMapping("/api/v1/unavailabilities/{unavailabilityId}")
    public ResponseEntity<?> getUnavailability(
            @PathVariable("unavailabilityId") String unavailabilityIdParam,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID unavailabilityId = parseUuid(unavailabilityIdParam);
        if (unavailabilityId == null) {
            return validationFailedResponse(httpRequest, "El identificador de la indisponibilidad debe ser un UUID válido.");
        }

        try {
            EmployeeUnavailability unav = getUnavailabilityUseCase.execute(identity.organizationId(), unavailabilityId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(EmployeeUnavailabilityResponse.from(unav));
        } catch (UnavailabilityNotFoundException e) {
            return notFoundResponse(httpRequest, "La indisponibilidad solicitada no existe o no pertenece a la organización.", "UNAVAILABILITY_NOT_FOUND");
        }
    }

    // =========================================================================
    // Manejo de excepciones y Problem Details (RFC 9457)
    // =========================================================================

    @ExceptionHandler(PastIntervalModificationProhibitedException.class)
    public ResponseEntity<ProblemDetailDto> handlePastInterval(HttpServletRequest request, PastIntervalModificationProhibitedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/past-interval-modification-prohibited",
                        "Modificación de intervalo pasado o iniciado prohibida",
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage() != null ? ex.getMessage() : "No se puede registrar, modificar ni cancelar una indisponibilidad en un periodo que ya ha iniciado o finalizado.",
                        request.getRequestURI(),
                        "PAST_INTERVAL_MODIFICATION_PROHIBITED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    @ExceptionHandler(UnavailabilityAlreadyCancelledException.class)
    public ResponseEntity<ProblemDetailDto> handleAlreadyCancelled(HttpServletRequest request, UnavailabilityAlreadyCancelledException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/unavailability-already-cancelled",
                        "Indisponibilidad ya cancelada",
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage() != null ? ex.getMessage() : "La indisponibilidad especificada ya se encuentra cancelada.",
                        request.getRequestURI(),
                        "UNAVAILABILITY_ALREADY_CANCELLED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    @ExceptionHandler(UnavailabilityConcurrentModificationException.class)
    public ResponseEntity<ProblemDetailDto> handleConcurrentModification(HttpServletRequest request, UnavailabilityConcurrentModificationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/concurrent-modification",
                        "Conflicto de concurrencia",
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage() != null ? ex.getMessage() : "El recurso fue modificado por otra operación concurrente o la versión esperada no coincide.",
                        request.getRequestURI(),
                        "CONCURRENT_MODIFICATION",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    @ExceptionHandler({UnavailabilityDataAccessException.class, BusinessIdempotencyDataAccessException.class})
    public ResponseEntity<ProblemDetailDto> handleDataAccessException(HttpServletRequest request, Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/service-unavailable",
                        "Servicio no disponible",
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "El servicio no está disponible temporalmente. Intente más tarde.",
                        request.getRequestURI(),
                        "SERVICE_UNAVAILABLE",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    @ExceptionHandler(InvalidUnavailabilityDataException.class)
    public ResponseEntity<ProblemDetailDto> handleInvalidUnavailabilityDataException(HttpServletRequest request, InvalidUnavailabilityDataException ex) {
        return validationFailedResponse(request, ex.getMessage());
    }

    // =========================================================================
    // Helpers internos
    // =========================================================================

    private Optional<AuthenticatedIdentity> getAuthenticatedIdentity() {
        Authentication auth = securityContextHolderStrategy.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedIdentity identity) {
            return Optional.of(identity);
        }
        return Optional.empty();
    }

    private ResponseEntity<ProblemDetailDto> sessionRequiredResponse(HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/session-required",
                        "Sesión requerida",
                        HttpStatus.UNAUTHORIZED.value(),
                        "No existe una sesión autenticada válida.",
                        httpRequest.getRequestURI(),
                        "SESSION_REQUIRED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private ResponseEntity<ProblemDetailDto> validationFailedResponse(HttpServletRequest httpRequest, String detail) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        "/problems/validation-failed",
                        "Validación fallida",
                        HttpStatus.BAD_REQUEST.value(),
                        detail,
                        httpRequest.getRequestURI(),
                        "VALIDATION_FAILED",
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private ResponseEntity<ProblemDetailDto> notFoundResponse(HttpServletRequest httpRequest, String detail, String code) {
        String problemType = "EMPLOYEE_NOT_FOUND".equals(code) ? "/problems/employee-not-found" : "/problems/unavailability-not-found";
        String problemTitle = "EMPLOYEE_NOT_FOUND".equals(code) ? "Colaborador no encontrado" : "Indisponibilidad no encontrada";
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ProblemDetailDto(
                        problemType,
                        problemTitle,
                        HttpStatus.NOT_FOUND.value(),
                        detail,
                        httpRequest.getRequestURI(),
                        code,
                        UUID.randomUUID().toString(),
                        List.of()
                ));
    }

    private UUID parseUuid(String param) {
        if (param == null || param.isBlank()) return null;
        try {
            return UUID.fromString(param.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant parseInstant(String param) {
        if (param == null || param.isBlank()) return null;
        try {
            return Instant.parse(param.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
```

- [ ] **Paso 4: Ejecutar la prueba para verificar que pasa**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityControllerTest" -B
```
Resultado esperado: PASS (6 pruebas exitosas).

---

### Tarea 8: Controlador REST `UnavailabilityController` — Mutaciones e Idempotencia Directa

**Files:**
- Modify: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityController.java` (agregar mutaciones, orquestación directa con `BusinessIdempotencyResult<EmployeeUnavailabilityResponse>` y RN-013)
- Test: `backend/src/test/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityControllerTest.java` (agregar pruebas de mutación, replay, conflictos de versión, RN-013 en actualización con `newStartsAt` pasado y reuso de clave)

**Interfaces:**
- Endpoints de mutación:
  - `POST /api/v1/employees/{employeeId}/unavailabilities`
  - `PUT /api/v1/unavailabilities/{unavailabilityId}`
  - `POST /api/v1/unavailabilities/{unavailabilityId}/cancel`
- Reutiliza directamente `BusinessIdempotencyResult<EmployeeUnavailabilityResponse>` sin clases selladas intermedias.
- Respuestas exitosas persisten y retransmiten `EmployeeUnavailabilityResponse` (con cabecera `Idempotency-Replayed: true` en replay).
- RN-013 en actualización:
  - Rechaza si la indisponibilidad original tiene `startsAt <= now`.
  - Rechaza si el nuevo intervalo propuesto tiene `newStartsAt <= now`.
  - Con `TimeInterval` positivo, no hace falta regla redundante sobre `newEndsAt`.
- RN-013 en cancelación:
  - Rechaza si `startsAt <= now`.

- [ ] **Paso 1: Agregar pruebas MockMvc para mutaciones y RN-013 en `UnavailabilityControllerTest.java`**

En `backend/src/test/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityControllerTest.java`, añadir los siguientes métodos de prueba:

```java
    // =========================================================================
    // Mutaciones: POST create, PUT update, POST cancel y RN-013
    // =========================================================================

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: creación exitosa devuelve 201 y Location")
    void create_exito() throws Exception {
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavId, empId, Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"),
                "MEDICO", "Cita médica", false, null, null, userId, 0L, now, now
        );

        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(resp));

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z",
                  "reasonCode": "MEDICO",
                  "restrictedNote": "Cita médica"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/unavailabilities/" + unavId))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(unavId.toString()))
                .andExpect(jsonPath("$.reasonCode").value("MEDICO"));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: sin Idempotency-Key devuelve 400 VALIDATION_FAILED")
    void create_sinIdempotencyKey() throws Exception {
        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/employees/" + empId + "/unavailabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: replay idempotente devuelve 201 e Idempotency-Replayed: true")
    void create_replayIdempotente() throws Exception {
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavId, empId, Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"),
                null, null, false, null, null, userId, 0L, now, now
        );

        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new BusinessIdempotencyResult.Replay<>(resp));

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(unavId.toString()));
    }

    @Test
    @DisplayName("POST /employees/{id}/unavailabilities: clave reutilizada con distinto payload devuelve 409 IDEMPOTENCY_KEY_REUSED")
    void create_keyReusedDevuelve409() throws Exception {
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new BusinessIdempotencyResult.KeyReused<>());

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/employees/" + empId + "/unavailabilities")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("PUT /unavailabilities/{id}: falta expectedLockVersion devuelve 400 VALIDATION_FAILED")
    void update_faltaLockVersion() throws Exception {
        UUID idemKey = UUID.randomUUID();
        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/unavailabilities/" + unavId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("RN-013 Update: rechaza actualizar indisponibilidad original ya iniciada (startsAt <= now)")
    void update_indisponibilidadOriginalIniciada_responde400() throws Exception {
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    Supplier<?> effect = inv.getArgument(1);
                    return effect.get(); // ejecutará el efecto que lanza la excepción
                });

        when(updateUnavailabilityUseCase.execute(
                org.mockito.ArgumentMatchers.eq(orgId),
                org.mockito.ArgumentMatchers.eq(unavId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0L)
        )).thenThrow(new PastIntervalModificationProhibitedException("No se puede modificar una indisponibilidad que ya inició o finalizó"));

        String payload = """
                {
                  "startsAt": "2026-10-15T14:00:00Z",
                  "endsAt": "2026-10-15T18:00:00Z",
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/unavailabilities/" + unavId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PAST_INTERVAL_MODIFICATION_PROHIBITED"));
    }

    @Test
    @DisplayName("RN-013 Update: rechaza actualizar indisponibilidad futura si el nuevo startsAt ya inició o es pasado (newStartsAt <= now)")
    void update_indisponibilidadFuturaConNuevoStartsAtIniciadoOPasado_responde400() throws Exception {
        UUID idemKey = UUID.randomUUID();
        when(organizationTimezonePort.resolveZoneId(orgId)).thenReturn(ZoneId.of("America/Bogota"));

        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    Supplier<?> effect = inv.getArgument(1);
                    return effect.get();
                });

        when(updateUnavailabilityUseCase.execute(
                org.mockito.ArgumentMatchers.eq(orgId),
                org.mockito.ArgumentMatchers.eq(unavId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0L)
        )).thenThrow(new PastIntervalModificationProhibitedException("El nuevo intervalo no puede haber iniciado ya ni estar en el pasado"));

        String payload = """
                {
                  "startsAt": "2026-09-20T10:00:00Z",
                  "endsAt": "2026-09-20T14:00:00Z",
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/unavailabilities/" + unavId)
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PAST_INTERVAL_MODIFICATION_PROHIBITED"));
    }

    @Test
    @DisplayName("POST /unavailabilities/{id}/cancel: cancelación exitosa responde 200")
    void cancel_exito() throws Exception {
        UUID idemKey = UUID.randomUUID();

        EmployeeUnavailabilityResponse resp = new EmployeeUnavailabilityResponse(
                unavId, empId, Instant.parse("2026-10-15T14:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"),
                null, null, true, now, userId, userId, 1L, now, now
        );

        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new BusinessIdempotencyResult.Proceed<>(resp));

        String payload = """
                {
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/unavailabilities/" + unavId + "/cancel")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true))
                .andExpect(jsonPath("$.lockVersion").value(1L));
    }

    @Test
    @DisplayName("RN-013 Cancel: rechaza cancelar indisponibilidad ya iniciada (startsAt <= now)")
    void cancel_indisponibilidadIniciada_responde400() throws Exception {
        UUID idemKey = UUID.randomUUID();

        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    Supplier<?> effect = inv.getArgument(1);
                    return effect.get();
                });

        when(cancelUnavailabilityUseCase.execute(orgId, unavId, userId, 0L))
                .thenThrow(new PastIntervalModificationProhibitedException("No se puede cancelar una indisponibilidad que ya inició o finalizó"));

        String payload = """
                {
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/unavailabilities/" + unavId + "/cancel")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PAST_INTERVAL_MODIFICATION_PROHIBITED"));
    }

    @Test
    @DisplayName("POST /unavailabilities/{id}/cancel: conflicto de concurrencia responde 409 CONCURRENT_MODIFICATION")
    void cancel_concurrencyConflict() throws Exception {
        UUID idemKey = UUID.randomUUID();

        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    Supplier<?> effect = inv.getArgument(1);
                    return effect.get();
                });

        when(cancelUnavailabilityUseCase.execute(orgId, unavId, userId, 0L))
                .thenThrow(new UnavailabilityConcurrentModificationException("Conflicto de versión"));

        String payload = """
                {
                  "expectedLockVersion": 0
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/unavailabilities/" + unavId + "/cancel")
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }
```

- [ ] **Paso 2: Implementar los métodos de mutación y renderizado en `UnavailabilityController.java`**

Añadir en `UnavailabilityController.java`:

```java
    // =========================================================================
    // 4. POST /api/v1/employees/{employeeId}/unavailabilities
    // =========================================================================

    @PostMapping("/api/v1/employees/{employeeId}/unavailabilities")
    public ResponseEntity<?> createUnavailability(
            @PathVariable("employeeId") String employeeIdParam,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID employeeId = parseUuid(employeeIdParam);
        if (employeeId == null) {
            return validationFailedResponse(httpRequest, "El identificador del colaborador debe ser un UUID válido.");
        }

        UUID idempotencyKey = parseUuid(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la petición es obligatorio.");
        }

        CreateUnavailabilityRequest req;
        try {
            req = objectMapper.readValue(rawBody, CreateUnavailabilityRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        ZoneId zoneId = organizationTimezonePort.resolveZoneId(identity.organizationId());
        UnavailabilityTemporalParser.ParsedInterval interval;
        try {
            interval = UnavailabilityTemporalParser.parse(
                    req.startsAt(), req.endsAt(), req.allDay(), req.startDate(), req.endDate(), zoneId
            );
        } catch (InvalidUnavailabilityDataException e) {
            return validationFailedResponse(httpRequest, e.getMessage());
        }

        UnavailabilityReason reason = parseReasonCode(req.reasonCode());
        if (req.reasonCode() != null && !req.reasonCode().isBlank() && reason == null) {
            return validationFailedResponse(httpRequest, "El motivo '" + req.reasonCode() + "' no pertenece al catálogo permitido.");
        }

        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, employeeId.toString());
        BusinessFingerprintCalculator.appendField(sb, interval.startsAt().toString());
        BusinessFingerprintCalculator.appendField(sb, interval.endsAt().toString());
        BusinessFingerprintCalculator.appendField(sb, reason != null ? reason.name() : "");
        BusinessFingerprintCalculator.appendField(sb, req.restrictedNote() != null ? req.restrictedNote() : "");
        String canonicalPayload = sb.toString();

        UnavailabilityIdempotentCommand command = new UnavailabilityIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "POST",
                "/api/v1/employees/" + employeeId + "/unavailabilities",
                canonicalPayload,
                true,
                objectMapper
        );

        Supplier<EmployeeUnavailabilityResponse> effect = () -> {
            EmployeeUnavailability created = createUnavailabilityUseCase.execute(
                    identity.organizationId(),
                    employeeId,
                    interval.startsAt(),
                    interval.endsAt(),
                    reason,
                    req.restrictedNote(),
                    identity.userId()
            );
            return EmployeeUnavailabilityResponse.from(created);
        };

        BusinessIdempotencyResult<EmployeeUnavailabilityResponse> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, true, httpRequest);
    }

    // =========================================================================
    // 5. PUT /api/v1/unavailabilities/{unavailabilityId}
    // =========================================================================

    @PutMapping("/api/v1/unavailabilities/{unavailabilityId}")
    public ResponseEntity<?> updateUnavailability(
            @PathVariable("unavailabilityId") String unavailabilityIdParam,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID unavailabilityId = parseUuid(unavailabilityIdParam);
        if (unavailabilityId == null) {
            return validationFailedResponse(httpRequest, "El identificador de la indisponibilidad debe ser un UUID válido.");
        }

        UUID idempotencyKey = parseUuid(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la petición es obligatorio.");
        }

        UpdateUnavailabilityRequest req;
        try {
            req = objectMapper.readValue(rawBody, UpdateUnavailabilityRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        if (req.expectedLockVersion() == null || req.expectedLockVersion() < 0) {
            return validationFailedResponse(httpRequest, "El campo 'expectedLockVersion' es obligatorio y debe ser un entero mayor o igual a 0.");
        }

        ZoneId zoneId = organizationTimezonePort.resolveZoneId(identity.organizationId());
        UnavailabilityTemporalParser.ParsedInterval interval;
        try {
            interval = UnavailabilityTemporalParser.parse(
                    req.startsAt(), req.endsAt(), req.allDay(), req.startDate(), req.endDate(), zoneId
            );
        } catch (InvalidUnavailabilityDataException e) {
            return validationFailedResponse(httpRequest, e.getMessage());
        }

        UnavailabilityReason reason = parseReasonCode(req.reasonCode());
        if (req.reasonCode() != null && !req.reasonCode().isBlank() && reason == null) {
            return validationFailedResponse(httpRequest, "El motivo '" + req.reasonCode() + "' no pertenece al catálogo permitido.");
        }

        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, unavailabilityId.toString());
        BusinessFingerprintCalculator.appendField(sb, interval.startsAt().toString());
        BusinessFingerprintCalculator.appendField(sb, interval.endsAt().toString());
        BusinessFingerprintCalculator.appendField(sb, reason != null ? reason.name() : "");
        BusinessFingerprintCalculator.appendField(sb, req.restrictedNote() != null ? req.restrictedNote() : "");
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(req.expectedLockVersion()));
        String canonicalPayload = sb.toString();

        UnavailabilityIdempotentCommand command = new UnavailabilityIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "PUT",
                "/api/v1/unavailabilities/" + unavailabilityId,
                canonicalPayload,
                false,
                objectMapper
        );

        Supplier<EmployeeUnavailabilityResponse> effect = () -> {
            EmployeeUnavailability updated = updateUnavailabilityUseCase.execute(
                    identity.organizationId(),
                    unavailabilityId,
                    interval.startsAt(),
                    interval.endsAt(),
                    reason,
                    req.restrictedNote(),
                    req.expectedLockVersion()
            );
            return EmployeeUnavailabilityResponse.from(updated);
        };

        BusinessIdempotencyResult<EmployeeUnavailabilityResponse> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, false, httpRequest);
    }

    // =========================================================================
    // 6. POST /api/v1/unavailabilities/{unavailabilityId}/cancel
    // =========================================================================

    @PostMapping("/api/v1/unavailabilities/{unavailabilityId}/cancel")
    public ResponseEntity<?> cancelUnavailability(
            @PathVariable("unavailabilityId") String unavailabilityIdParam,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthenticatedIdentity> identityOpt = getAuthenticatedIdentity();
        if (identityOpt.isEmpty()) {
            return sessionRequiredResponse(httpRequest);
        }
        AuthenticatedIdentity identity = identityOpt.get();

        UUID unavailabilityId = parseUuid(unavailabilityIdParam);
        if (unavailabilityId == null) {
            return validationFailedResponse(httpRequest, "El identificador de la indisponibilidad debe ser un UUID válido.");
        }

        UUID idempotencyKey = parseUuid(idempotencyKeyHeader);
        if (idempotencyKey == null) {
            return validationFailedResponse(httpRequest, "La cabecera Idempotency-Key es obligatoria y debe ser un UUID válido.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            return validationFailedResponse(httpRequest, "El cuerpo de la petición es obligatorio.");
        }

        CancelUnavailabilityRequest req;
        try {
            req = objectMapper.readValue(rawBody, CancelUnavailabilityRequest.class);
        } catch (JacksonException e) {
            return validationFailedResponse(httpRequest, "Estructura JSON inválida.");
        }

        if (req.expectedLockVersion() == null || req.expectedLockVersion() < 0) {
            return validationFailedResponse(httpRequest, "El campo 'expectedLockVersion' es obligatorio y debe ser un entero mayor o igual a 0.");
        }

        StringBuilder sb = new StringBuilder();
        BusinessFingerprintCalculator.appendField(sb, unavailabilityId.toString());
        BusinessFingerprintCalculator.appendField(sb, String.valueOf(req.expectedLockVersion()));
        String canonicalPayload = sb.toString();

        UnavailabilityIdempotentCommand command = new UnavailabilityIdempotentCommand(
                identity.organizationId(),
                identity.userId(),
                idempotencyKey,
                "POST",
                "/api/v1/unavailabilities/" + unavailabilityId + "/cancel",
                canonicalPayload,
                false,
                objectMapper
        );

        Supplier<EmployeeUnavailabilityResponse> effect = () -> {
            EmployeeUnavailability cancelled = cancelUnavailabilityUseCase.execute(
                    identity.organizationId(),
                    unavailabilityId,
                    identity.userId(),
                    req.expectedLockVersion()
            );
            return EmployeeUnavailabilityResponse.from(cancelled);
        };

        BusinessIdempotencyResult<EmployeeUnavailabilityResponse> result = executor.execute(command, effect);
        return renderIdempotencyResult(result, false, httpRequest);
    }

    // =========================================================================
    // Métodos auxiliares de renderizado de idempotencia
    // =========================================================================

    private ResponseEntity<?> renderIdempotencyResult(
            BusinessIdempotencyResult<EmployeeUnavailabilityResponse> result,
            boolean isCreate,
            HttpServletRequest httpRequest
    ) {
        if (result instanceof BusinessIdempotencyResult.Proceed(var resp)) {
            var builder = ResponseEntity.status(isCreate ? HttpStatus.CREATED : HttpStatus.OK)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_JSON);
            if (isCreate) {
                builder.location(URI.create("/api/v1/unavailabilities/" + resp.id()));
            }
            return builder.body(resp);
        } else if (result instanceof BusinessIdempotencyResult.Replay(var resp)) {
            var builder = ResponseEntity.status(isCreate ? HttpStatus.CREATED : HttpStatus.OK)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("Idempotency-Replayed", "true")
                    .contentType(MediaType.APPLICATION_JSON);
            if (isCreate) {
                builder.location(URI.create("/api/v1/unavailabilities/" + resp.id()));
            }
            return builder.body(resp);
        } else if (result instanceof BusinessIdempotencyResult.KeyReused) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(new ProblemDetailDto(
                            "/problems/idempotency-key-reused",
                            "Clave de idempotencia reutilizada",
                            HttpStatus.CONFLICT.value(),
                            "La clave de idempotencia ya fue utilizada con una petición diferente.",
                            httpRequest.getRequestURI(),
                            "IDEMPOTENCY_KEY_REUSED",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        } else if (result instanceof BusinessIdempotencyResult.Expired) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(PROBLEM_JSON_MEDIA_TYPE)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(new ProblemDetailDto(
                            "/problems/idempotency-key-expired",
                            "Clave de idempotencia expirada",
                            HttpStatus.CONFLICT.value(),
                            "La clave de idempotencia ha expirado.",
                            httpRequest.getRequestURI(),
                            "IDEMPOTENCY_KEY_EXPIRED",
                            UUID.randomUUID().toString(),
                            List.of()
                    ));
        }
        throw new IllegalStateException("Resultado de idempotencia no soportado: " + result);
    }

    private UnavailabilityReason parseReasonCode(String code) {
        if (code == null || code.isBlank()) return null;
        try {
            return UnavailabilityReason.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
```

- [ ] **Paso 3: Ejecutar la prueba para verificar que pasa**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityControllerTest" -B
```
Resultado esperado: PASS (15 pruebas exitosas).

---

### Tarea 9: Pruebas de Integración de Seguridad y Base de Datos (`UnavailabilityControllerIT`)

**Files:**
- Create: `backend/src/test/java/com/turnoshift/unavailability/infrastructure/web/UnavailabilityControllerIT.java`

**Interfaces:**
- Verifica sobre Testcontainers 2.0.2 (MSSQL Server 2022):
  - RBAC: `ROLE_ADMINISTRADOR` y `ROLE_SUPERVISOR` acceden a los 6 endpoints (200/201).
  - RBAC: `ROLE_COLABORADOR` y `ROLE_PLATFORM_OPERATOR` reciben 403 `ACCESS_DENIED`.
  - Sesión ausente: 401 `SESSION_REQUIRED`.
  - CSRF ausente o inválido en POST/PUT: 403 `CSRF_INVALID`.
  - Aislamiento multi-tenant: solicitar un empleado de otra organización devuelve 404 seguro.
  - Replay idempotente real grabado en la tabla `api_operations`.

- [ ] **Paso 1: Escribir la prueba de integración `UnavailabilityControllerIT.java`**

```java
package com.turnoshift.unavailability.infrastructure.web;

import com.turnoshift.identity.infrastructure.security.Argon2PasswordService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("IT: UnavailabilityController — Seguridad, RBAC, CSRF e Idempotencia")
class UnavailabilityControllerIT {

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
        registry.add("server.servlet.session.cookie.secure", () -> "false");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Argon2PasswordService passwordService;

    private record UserSession(
            UUID orgId,
            UUID userId,
            UUID employeeId,
            Cookie sessionCookie,
            String csrfToken
    ) {}

    private UserSession createOrgAndLogin(String role) throws Exception {
        UUID orgId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String orgCode = "ORG-" + suffix;

        jdbc.update("""
                INSERT INTO organizations (id, code, display_name, service_status, timezone_id, max_active_employees, max_active_sites)
                VALUES (?, ?, 'Org Test', 'ACTIVA', 'America/Bogota', 10, 1)
                """, orgId, orgCode);

        UUID siteId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sites (id, organization_id, name, is_active)
                VALUES (?, ?, 'Sede Principal', 1)
                """, siteId, orgId);

        UUID posId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO positions (id, organization_id, name, is_active)
                VALUES (?, ?, 'Puesto Uno', 1)
                """, posId, orgId);

        UUID empId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO employees (id, organization_id, code, display_name, position_id, site_id, is_active)
                VALUES (?, ?, 'EMP-' + ?, 'Empleado Uno', ?, ?, 1)
                """, empId, orgId, suffix, posId, siteId);

        UUID userId = UUID.randomUUID();
        String email = "user." + suffix.toLowerCase(Locale.ROOT) + "@empresa.example";
        String plainPassword = "PasswordSegura2026!";
        String passwordHash = passwordService.codificar(plainPassword);

        jdbc.update("""
                INSERT INTO app_users (id, organization_id, email, normalized_email, display_name, password_hash, role_code, is_active, auth_version)
                VALUES (?, ?, ?, ?, 'Usuario Test', ?, ?, 1, 1)
                """, userId, orgId, email, email.toLowerCase(Locale.ROOT), passwordHash, role);

        // 1. Obtener CSRF y cookie inicial
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie initialCookie = csrfResult.getResponse().getCookie("TSESSIONID");
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asString();

        // 2. Login
        String loginPayload = """
                {
                  "organizationCode": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(orgCode, email, plainPassword);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(initialCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("TSESSIONID");
        Cookie cookieToUse = sessionCookie != null ? sessionCookie : initialCookie;

        return new UserSession(orgId, userId, empId, cookieToUse, csrfToken);
    }

    @Test
    @DisplayName("ROLE_SUPERVISOR: Puede crear indisponibilidad para colaborador activo (201 Created)")
    void supervisorPuedeCrearIndisponibilidad() throws Exception {
        UserSession session = createOrgAndLogin("SUPERVISOR");
        UUID idemKey = UUID.randomUUID();

        Instant startsAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(4, ChronoUnit.HOURS);

        String payload = """
                {
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "reasonCode": "PERMISO_PERSONAL",
                  "restrictedNote": "Permiso especial de supervisor"
                }
                """.formatted(startsAt, endsAt);

        mockMvc.perform(post("/api/v1/employees/" + session.employeeId() + "/unavailabilities")
                        .cookie(session.sessionCookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.reasonCode").value("PERMISO_PERSONAL"));
    }

    @Test
    @DisplayName("ROLE_COLABORADOR: Rechazado con 403 Forbidden ACCESS_DENIED en creación")
    void colaboradorEsRechazadoCon403() throws Exception {
        UserSession session = createOrgAndLogin("COLABORADOR");
        UUID idemKey = UUID.randomUUID();

        Instant startsAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(4, ChronoUnit.HOURS);

        String payload = """
                {
                  "startsAt": "%s",
                  "endsAt": "%s"
                }
                """.formatted(startsAt, endsAt);

        mockMvc.perform(post("/api/v1/employees/" + session.employeeId() + "/unavailabilities")
                        .cookie(session.sessionCookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Sin sesión: Petición anónima responde 401 SESSION_REQUIRED")
    void peticionAnonimaResponde401() throws Exception {
        mockMvc.perform(get("/api/v1/unavailabilities")
                        .param("from", "2026-10-01T00:00:00Z")
                        .param("to", "2026-10-15T00:00:00Z"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("Sin token CSRF: Petición POST responde 403 CSRF_INVALID")
    void postSinCsrfResponde403() throws Exception {
        UserSession session = createOrgAndLogin("ADMINISTRADOR");

        mockMvc.perform(post("/api/v1/employees/" + session.employeeId() + "/unavailabilities")
                        .cookie(session.sessionCookie())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("Idempotencia real: Repetir POST con la misma clave devuelve replay 201 e Idempotency-Replayed")
    void idempotenciaRealReplay() throws Exception {
        UserSession session = createOrgAndLogin("ADMINISTRADOR");
        UUID idemKey = UUID.randomUUID();

        Instant startsAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(8, ChronoUnit.HOURS);

        String payload = """
                {
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "reasonCode": "VACACIONES"
                }
                """.formatted(startsAt, endsAt);

        // Primer envío: 201 Created
        mockMvc.perform(post("/api/v1/employees/" + session.employeeId() + "/unavailabilities")
                        .cookie(session.sessionCookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotency-Replayed"));

        // Segundo envío idéntico: 201 Created con Idempotency-Replayed: true
        mockMvc.perform(post("/api/v1/employees/" + session.employeeId() + "/unavailabilities")
                        .cookie(session.sessionCookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", idemKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        // Verificar que en base de datos solo existe 1 registro en employee_unavailabilities
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM employee_unavailabilities WHERE organization_id = ? AND employee_id = ?",
                Integer.class, session.orgId(), session.employeeId()
        );
        assertThat(count).isEqualTo(1);
    }
}
```

- [ ] **Paso 2: Ejecutar la prueba de integración**

Ejecutar:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityControllerIT" -B
```
Resultado esperado: PASS (5 pruebas de integración exitosas).

---

### Tarea 10: Verificación Final Focalizada

- [ ] **Paso 1: Ejecutar la suite completa y focalizada de I2.4-c**

Ejecutar el comando de verificación obligatorio:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=UnavailabilityTemporalParserTest,OrganizationTimezoneAdapterTest,UnavailabilityIdempotentCommandTest,UnavailabilityControllerTest,UnavailabilityControllerIT" -B
```

Resultado esperado:
- `UnavailabilityTemporalParserTest`: 10 tests passed
- `OrganizationTimezoneAdapterTest`: 3 tests passed
- `UnavailabilityIdempotentCommandTest`: 2 tests passed
- `UnavailabilityControllerTest`: 15 tests passed
- `UnavailabilityControllerIT`: 5 tests passed
- **Total:** 35 tests pasados, 0 fallos, 0 errores, BUILD SUCCESS.
