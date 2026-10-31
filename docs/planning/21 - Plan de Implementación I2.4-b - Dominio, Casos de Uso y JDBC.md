# Implementación I2.4-b: Dominio, Casos de Uso y JDBC de Indisponibilidades

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar el modelo de dominio puro, los casos de uso operativos deterministas con `Clock`, el puerto de consulta de conflictos para I3 y el adaptador de persistencia JDBC para las indisponibilidades de personal de TurnoShift, garantizando aislamiento multi-tenant estricto por `organization_id`, control de concurrencia optimista (`lock_version`), timestamps autoritativos de base de datos (`SYSUTCDATETIME()`), validaciones temporales en UTC semiabierto y cumplimiento estricto de RN-013.

**Architecture:** Arquitectura hexagonal limpia bajo el paquete `com.turnoshift.unavailability`. El núcleo de dominio encapsula reglas temporales inmutables en instantes UTC $[startsAt, endsAt)$, catálogo de motivos cerrado (`UnavailabilityReason`), notas restringidas (`RestrictedNote` preservando formato exacto) y auditoría de cancelación atómica (`CancellationInfo`). Los casos de uso reciben un `Clock` inyectable para evaluar `now` de forma determinista y orquestan mutaciones desacopladas de la infraestructura HTTP (la modalidad `allDay` llega ya convertida a UTC). El adaptador JDBC utiliza `JdbcTemplate` contra SQL Server 2022 sin transacciones manuales, con timestamps autoritativos generados en base de datos (`SYSUTCDATETIME()`) y cláusulas `OUTPUT inserted.*` tanto en inserción como en actualización y cancelación.

**Tech Stack:** Java 21 (Temurin), Spring Boot 3.4.x, Spring Data JDBC / `JdbcTemplate`, Microsoft SQL Server 2022 (Linux container), Testcontainers, JUnit 5, AssertJ, Mockito.

**Spec:** [docs/planning/19 - Diseño Técnico I2.4 - Indisponibilidades.md](file:///d:/Proyectos/TurnoShift/docs/planning/19%20-%20Dise%C3%B1o%20T%C3%A9cnico%20I2.4%20-%20Indisponibilidades.md) y [V17__employee_unavailabilities.sql](file:///d:/Proyectos/TurnoShift/backend/src/main/resources/db/migration/V17__employee_unavailabilities.sql)

## Global Constraints

- **Sin lógica HTTP en esta fase:** Prohibido crear controladores, DTOs JSON, cabeceras `Idempotency-Key` o filtros de seguridad HTTP en I2.4-b (reservado para I2.4-c).
- **Semántica Temporal Única:** El dominio opera exclusivamente con `java.time.Instant` en UTC semiabierto $[startsAt, endsAt)$. No existen campos locales ni flags `allDay` en las entidades del dominio ni en el repositorio.
- **Aislamiento Multi-Tenant Estricto:** Toda consulta, inserción, actualización o cancelación JDBC exige obligatoriamente `organization_id` como primer parámetro de partición.
- **Transaccionalidad y Concurrencia:** Prohibido el uso de sentencias manuales `BEGIN TRANSACTION`, `COMMIT` o `ROLLBACK`. La concurrencia optimista se valida condicionalmente por `lock_version` con incremento atómico `lock_version = lock_version + 1`.
- **Regla RN-013 Estricta (Protección de Periodos Iniciados o Pasados):** `update` y `cancel` deben rechazar de manera inmediata cualquier indisponibilidad cuyo `startsAt <= now` lanzando `PastIntervalModificationProhibitedException`. No basta verificar `endsAt > now`: un intervalo que ya inició o finalizó no se puede modificar ni cancelar bajo ninguna circunstancia.
- **Tiempo Determinista en Casos de Uso:** Los casos de uso reciben un `Clock` inyectable (en producción `Clock.systemUTC()`, en pruebas `Clock.fixed(...)`).
- **Timestamps Autoritativos de Servidor:** `created_at`, `updated_at` y `cancelled_at` se generan exclusivamente en SQL Server mediante `SYSUTCDATETIME()`. La actualización y cancelación JDBC deben retornar la fila persistida mediante `OUTPUT inserted.*`; no generar marcas de tiempo en Java para la persistencia.
- **Separación de Capas:** El dominio puro no debe importar Spring, JDBC, SQL ni `DataAccessException`. `UnavailabilityDataAccessException` pertenece a infraestructura/persistencia.
- **Preservación y Validación de RestrictedNote:** No alterar, normalizar ni recortar texto (`trim`). La nota se rechaza si es `null`, vacía, si supera 500 unidades UTF-16 (`value.length() > 500`), o si todos sus puntos de código cumplen `Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)`. Esta regla incluye el espacio no separable NBSP (`\u00A0`) y otros espacios Unicode. Si es válida, se preserva el contenido textual exacto recibido.
- **Permisividad de Solapamientos:** El caso de uso permite solapamientos entre indisponibilidades del mismo colaborador (evaluadas como unión matemática por el motor I3).
- **Confidencialidad de la Nota Restringida (RN-014):** La nota textual solo se expone en agregados y consultas de uso interno administrativo.
- **Verificación Focalizada:** No ejecutar `mvn clean`, `mvn verify`, plugins `failsafe` ni suites globales de prueba.

---

## 1. Mapeo de Componentes y Paquetes

```
backend/src/main/java/com/turnoshift/unavailability/
├── domain/
│   ├── EmployeeUnavailability.java
│   ├── TimeInterval.java
│   ├── UnavailabilityReason.java
│   ├── RestrictedNote.java
│   ├── CancellationInfo.java
│   ├── EmployeeUnavailabilityRepository.java
│   ├── EmployeeValidationPort.java
│   ├── UnavailabilityConflictQueryPort.java
│   └── exceptions/
│       ├── InvalidUnavailabilityDataException.java
│       ├── PastIntervalModificationProhibitedException.java
│       ├── UnavailabilityNotFoundException.java
│       ├── UnavailabilityAlreadyCancelledException.java
│       ├── UnavailabilityConcurrentModificationException.java
│       └── EmployeeNotFoundException.java
├── application/
│   ├── CreateUnavailabilityUseCase.java
│   ├── GetUnavailabilityUseCase.java
│   ├── ListEmployeeUnavailabilitiesUseCase.java
│   ├── ListOrganizationUnavailabilitiesUseCase.java
│   ├── UpdateUnavailabilityUseCase.java
│   ├── CancelUnavailabilityUseCase.java
│   └── UnavailabilityConflictQueryService.java
└── infrastructure/
    ├── persistence/
    │   ├── JdbcEmployeeUnavailabilityRepository.java
    │   └── UnavailabilityDataAccessException.java
    └── adapter/
        └── TeamEmployeeValidationAdapter.java
```

---

## 2. Definición Detallada de Tareas

### Task 1: [Domain] Modelo de Dominio Puro, Value Objects y Excepciones

**Files:**
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/TimeInterval.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/UnavailabilityReason.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/RestrictedNote.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/CancellationInfo.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/exceptions/InvalidUnavailabilityDataException.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/exceptions/PastIntervalModificationProhibitedException.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/exceptions/UnavailabilityNotFoundException.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/exceptions/UnavailabilityAlreadyCancelledException.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/exceptions/UnavailabilityConcurrentModificationException.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/exceptions/EmployeeNotFoundException.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/EmployeeUnavailability.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/domain/TimeIntervalTest.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/domain/RestrictedNoteTest.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/domain/EmployeeUnavailabilityTest.java`

**Interfaces:**
- Produces: Agregado inmutable `EmployeeUnavailability` y Value Objects `TimeInterval`, `RestrictedNote`, `CancellationInfo` y catálogo `UnavailabilityReason`.

- [ ] **Step 1: Escribir pruebas unitarias de `TimeIntervalTest`**

Crear `backend/src/test/java/com/turnoshift/unavailability/domain/TimeIntervalTest.java`:
```java
package com.turnoshift.unavailability.domain;

import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeIntervalTest {

    @Test
    @DisplayName("Crea un intervalo semiabierto válido cuando startsAt < endsAt")
    void creaIntervaloValido() {
        Instant start = Instant.parse("2026-10-15T08:00:00Z");
        Instant end = Instant.parse("2026-10-15T16:00:00Z");

        TimeInterval interval = TimeInterval.of(start, end);

        assertThat(interval.startsAt()).isEqualTo(start);
        assertThat(interval.endsAt()).isEqualTo(end);
    }

    @Test
    @DisplayName("Rechaza instantes nulos")
    void rechazaInstantesNulos() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> TimeInterval.of(null, now))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("startsAt");

        assertThatThrownBy(() -> TimeInterval.of(now, null))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("endsAt");
    }

    @Test
    @DisplayName("Rechaza startsAt igual o posterior a endsAt")
    void rechazaCronologiaInvalida() {
        Instant t = Instant.parse("2026-10-15T08:00:00Z");

        assertThatThrownBy(() -> TimeInterval.of(t, t))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("debe ser anterior");

        assertThatThrownBy(() -> TimeInterval.of(t, t.minusSeconds(60)))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("debe ser anterior");
    }

    @Test
    @DisplayName("Evalúa solapamiento semiabierto estricto (RN-017)")
    void evaluaSolapamientoSemiabierto() {
        TimeInterval base = TimeInterval.of(
                Instant.parse("2026-10-15T10:00:00Z"),
                Instant.parse("2026-10-15T14:00:00Z")
        );

        // Contacto contiguo previo (no solapa)
        assertThat(base.overlaps(TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T10:00:00Z")
        ))).isFalse();

        // Contacto contiguo posterior (no solapa)
        assertThat(base.overlaps(TimeInterval.of(
                Instant.parse("2026-10-15T14:00:00Z"),
                Instant.parse("2026-10-15T18:00:00Z")
        ))).isFalse();

        // Intersección parcial
        assertThat(base.overlaps(TimeInterval.of(
                Instant.parse("2026-10-15T12:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        ))).isTrue();

        // Contención total
        assertThat(base.overlaps(TimeInterval.of(
                Instant.parse("2026-10-15T11:00:00Z"),
                Instant.parse("2026-10-15T13:00:00Z")
        ))).isTrue();
    }
}
```

- [ ] **Step 2: Escribir pruebas unitarias de `RestrictedNoteTest` (Preservación exacta sin recortar y validación Unicode)**

Crear `backend/src/test/java/com/turnoshift/unavailability/domain/RestrictedNoteTest.java`:
```java
package com.turnoshift.unavailability.domain;

import com.turnoshift.unavailability.domain.exceptions.InvalidUnavailabilityDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestrictedNoteTest {

    @Test
    @DisplayName("Preserva contenido válido exactamente sin transformación ni recortar espacios")
    void preservaContenidoValidoSinTransformacion() {
        String textoOriginal = "  Cita con especialista médico  ";
        RestrictedNote note = RestrictedNote.of(textoOriginal);
        assertThat(note.value()).isEqualTo(textoOriginal);
    }

    @Test
    @DisplayName("Rechaza valor nulo")
    void rechazaNull() {
        assertThatThrownBy(() -> RestrictedNote.of(null))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("no puede ser nula");
    }

    @Test
    @DisplayName("Rechaza cadena vacía")
    void rechazaCadenaVacia() {
        assertThatThrownBy(() -> RestrictedNote.of(""))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("no puede estar vacía");
    }

    @Test
    @DisplayName("Rechaza cadena con solo espacios normales")
    void rechazaEspaciosNormales() {
        assertThatThrownBy(() -> RestrictedNote.of("   \t\n  "))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("no puede estar vacía");
    }

    @Test
    @DisplayName("Rechaza cadena que contiene únicamente NBSP (\\u00A0)")
    void rechazaSoloNbsp() {
        assertThatThrownBy(() -> RestrictedNote.of("\u00A0\u00A0"))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("no puede estar vacía");
    }

    @Test
    @DisplayName("Rechaza mezcla de espacios Unicode")
    void rechazaMezclaEspaciosUnicode() {
        // Mezcla de NBSP (\u00A0), espacio em (\u2003), espacio en (\u2002), tabulaciones y saltos de línea
        assertThatThrownBy(() -> RestrictedNote.of("\u00A0 \t\u2003\n\u2002"))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("no puede estar vacía");
    }

    @Test
    @DisplayName("Rechaza nota que supera el límite de 500 unidades UTF-16")
    void rechazaSupera500UnidadesUtf16() {
        String textoLargo = "a".repeat(501);
        assertThatThrownBy(() -> RestrictedNote.of(textoLargo))
                .isInstanceOf(InvalidUnavailabilityDataException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("Acepta nota en el límite exacto de 500 unidades UTF-16")
    void aceptaLimite500UnidadesUtf16() {
        String textoLimite = "b".repeat(500);
        RestrictedNote note = RestrictedNote.of(textoLimite);
        assertThat(note.value()).hasSize(500);
    }
}
```

- [ ] **Step 3: Escribir pruebas unitarias de `EmployeeUnavailabilityTest` (RN-013 estricto con `startsAt <= now`)**

Crear `backend/src/test/java/com/turnoshift/unavailability/domain/EmployeeUnavailabilityTest.java`:
```java
package com.turnoshift.unavailability.domain;

import com.turnoshift.unavailability.domain.exceptions.PastIntervalModificationProhibitedException;
import com.turnoshift.unavailability.domain.exceptions.UnavailabilityAlreadyCancelledException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeUnavailabilityTest {

    private final UUID orgId = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("Crea una indisponibilidad activa inicial con lock_version 0")
    void creaIndisponibilidadInicial() {
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );

        EmployeeUnavailability unav = EmployeeUnavailability.create(
                orgId, empId, interval, UnavailabilityReason.MEDICO,
                RestrictedNote.of("Consulta"), userId, now
        );

        assertThat(unav.isActive()).isTrue();
        assertThat(unav.isCancelled()).isFalse();
        assertThat(unav.lockVersion()).isEqualTo(0L);
        assertThat(unav.createdAt()).isEqualTo(now);
        assertThat(unav.updatedAt()).isEqualTo(now);
        assertThat(unav.cancellationInfo()).isEmpty();
    }

    @Test
    @DisplayName("Cancela lógicamente una indisponibilidad activa futura (startsAt > now)")
    void cancelaIndisponibilidadFutura() {
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );
        EmployeeUnavailability unav = EmployeeUnavailability.create(
                orgId, empId, interval, UnavailabilityReason.VACACIONES, null, userId, now
        );

        Instant cancelTime = Instant.parse("2026-10-05T10:00:00Z");
        UUID cancelUser = UUID.randomUUID();

        EmployeeUnavailability cancelled = unav.cancel(cancelUser, cancelTime);

        assertThat(cancelled.isCancelled()).isTrue();
        assertThat(cancelled.isActive()).isFalse();
        assertThat(cancelled.cancellationInfo()).isPresent();
        assertThat(cancelled.cancellationInfo().get().cancelledByUserId()).isEqualTo(cancelUser);
        assertThat(cancelled.cancellationInfo().get().cancelledAt()).isEqualTo(cancelTime);
    }

    @Test
    @DisplayName("RN-013: Rechaza cancelar si el intervalo ya inició (startsAt == now)")
    void rechazaCancelarSiIntervaloIniciaExactamenteAhora() {
        Instant t = Instant.parse("2026-10-15T08:00:00Z");
        TimeInterval interval = TimeInterval.of(t, t.plusSeconds(28800));
        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                UUID.randomUUID(), orgId, empId, interval, UnavailabilityReason.MEDICO,
                null, userId, t.minusSeconds(86400), t.minusSeconds(86400), 0L, null
        );

        assertThatThrownBy(() -> unav.cancel(userId, t))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("ya inició o finalizó");
    }

    @Test
    @DisplayName("RN-013: Rechaza cancelar si el intervalo ya inició en el pasado (startsAt < now)")
    void rechazaCancelarSiIntervaloYaInicio() {
        Instant start = Instant.parse("2026-10-15T08:00:00Z");
        Instant end = Instant.parse("2026-10-15T16:00:00Z");
        Instant now = Instant.parse("2026-10-15T09:00:00Z"); // Dentro del intervalo
        TimeInterval interval = TimeInterval.of(start, end);
        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                UUID.randomUUID(), orgId, empId, interval, UnavailabilityReason.MEDICO,
                null, userId, start.minusSeconds(86400), start.minusSeconds(86400), 0L, null
        );

        assertThatThrownBy(() -> unav.cancel(userId, now))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("ya inició o finalizó");
    }

    @Test
    @DisplayName("RN-013: Rechaza modificar si el intervalo ya inició o finalizó (startsAt <= now)")
    void rechazaModificarSiIntervaloYaInicio() {
        Instant start = Instant.parse("2026-10-15T08:00:00Z");
        Instant end = Instant.parse("2026-10-15T16:00:00Z");
        Instant now = Instant.parse("2026-10-15T08:00:00Z");
        TimeInterval interval = TimeInterval.of(start, end);
        EmployeeUnavailability unav = EmployeeUnavailability.reconstruct(
                UUID.randomUUID(), orgId, empId, interval, UnavailabilityReason.MEDICO,
                null, userId, start.minusSeconds(86400), start.minusSeconds(86400), 0L, null
        );

        TimeInterval nuevoIntervalo = TimeInterval.of(start.plusSeconds(3600), end.plusSeconds(3600));

        assertThatThrownBy(() -> unav.update(nuevoIntervalo, UnavailabilityReason.OTRO, null, now))
                .isInstanceOf(PastIntervalModificationProhibitedException.class)
                .hasMessageContaining("ya inició o finalizó");
    }

    @Test
    @DisplayName("Rechaza cancelar una indisponibilidad ya cancelada")
    void rechazaCancelarYaCancelada() {
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        TimeInterval interval = TimeInterval.of(
                Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T16:00:00Z")
        );
        EmployeeUnavailability unav = EmployeeUnavailability.create(
                orgId, empId, interval, UnavailabilityReason.VACACIONES, null, userId, now
        );
        EmployeeUnavailability cancelled = unav.cancel(userId, now.plusSeconds(3600));

        assertThatThrownBy(() -> cancelled.cancel(userId, now.plusSeconds(7200)))
                .isInstanceOf(UnavailabilityAlreadyCancelledException.class);
    }
}
```

- [ ] **Step 4: Implementar las excepciones puras de dominio con códigos estables**

Crear en `backend/src/main/java/com/turnoshift/unavailability/domain/exceptions/`:
1. `InvalidUnavailabilityDataException` (código `"VALIDATION_FAILED"`, extiende `RuntimeException`).
2. `PastIntervalModificationProhibitedException` (código `"PAST_INTERVAL_MODIFICATION_PROHIBITED"`, extiende `RuntimeException`).
3. `UnavailabilityNotFoundException` (código `"UNAVAILABILITY_NOT_FOUND"`, extiende `RuntimeException`).
4. `UnavailabilityAlreadyCancelledException` (código `"UNAVAILABILITY_ALREADY_CANCELLED"`, extiende `RuntimeException`).
5. `UnavailabilityConcurrentModificationException` (código `"CONCURRENT_MODIFICATION"`, extiende `RuntimeException`).
6. `EmployeeNotFoundException` (código `"EMPLOYEE_NOT_FOUND"`, extiende `RuntimeException`).

*Nota:* Ninguna de estas excepciones importa dependencias de Spring, JDBC, SQL ni `DataAccessException`.

- [ ] **Step 5: Implementar los Value Objects y el Agregado de Dominio**

Crear:
1. `TimeInterval.java`: Value object inmutable (`startsAt`, `endsAt`).
2. `UnavailabilityReason.java`: Enumeración con `VACACIONES`, `PERMISO_PERSONAL`, `MEDICO`, `FORMACION`, `OTRO`.
3. `RestrictedNote.java`: Value object que almacena `String value` exacto sin aplicar `trim()`. Se rechaza si es `null`, vacía, si supera 500 unidades UTF-16 (`value.length() > 500`), o si todos sus puntos de código cumplen `Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)` (lo cual cubre el espacio no separable NBSP `\u00A0` y otros espacios Unicode).
4. `CancellationInfo.java`: Record con `Instant cancelledAt` y `UUID cancelledByUserId`.
5. `EmployeeUnavailability.java`: Agregado inmutable con métodos factoría `create` y `reconstruct`, y métodos de mutación `update` y `cancel` que hacen cumplir **RN-013** (`if (!interval.startsAt().isAfter(now)) throw new PastIntervalModificationProhibitedException(...)`).

---

### Task 2: [Ports] Puertos de Dominio y Adaptador de Validación

**Files:**
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/EmployeeUnavailabilityRepository.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/EmployeeValidationPort.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/domain/UnavailabilityConflictQueryPort.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/adapter/TeamEmployeeValidationAdapter.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/infrastructure/adapter/TeamEmployeeValidationAdapterTest.java`

**Interfaces:**
- Produces: Contratos de persistencia, validación de empleado y consulta de conflictos para I3.

- [ ] **Step 1: Definir los contratos de los puertos**

1. `EmployeeUnavailabilityRepository`:
```java
package com.turnoshift.unavailability.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeUnavailabilityRepository {
    EmployeeUnavailability insert(EmployeeUnavailability unavailability);
    Optional<EmployeeUnavailability> findById(UUID organizationId, UUID id);
    List<EmployeeUnavailability> findByEmployee(UUID organizationId, UUID employeeId, boolean includeCancelled);
    List<EmployeeUnavailability> findByEmployeeAndInterval(UUID organizationId, UUID employeeId, Instant from, Instant to, boolean includeCancelled);
    List<EmployeeUnavailability> findByOrganizationAndInterval(UUID organizationId, Instant from, Instant to, boolean includeCancelled);
    Optional<EmployeeUnavailability> update(EmployeeUnavailability unavailability, long expectedLockVersion);
    Optional<EmployeeUnavailability> cancel(UUID organizationId, UUID id, UUID actorUserId, long expectedLockVersion);
}
```

2. `EmployeeValidationPort`:
```java
package com.turnoshift.unavailability.domain;

import java.util.UUID;

public interface EmployeeValidationPort {
    boolean isEmployeeActive(UUID organizationId, UUID employeeId);
}
```

3. `UnavailabilityConflictQueryPort` (para consumo futuro por I3):
```java
package com.turnoshift.unavailability.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface UnavailabilityConflictQueryPort {
    boolean hasConflict(UUID organizationId, UUID employeeId, Instant shiftStart, Instant shiftEnd);
    List<TimeInterval> findConflictingIntervals(UUID organizationId, UUID employeeId, Instant shiftStart, Instant shiftEnd);
    Map<UUID, List<TimeInterval>> findActiveIntervalsByEmployees(UUID organizationId, Set<UUID> employeeIds, Instant rangeStart, Instant rangeEnd);
}
```

- [ ] **Step 2: Implementar y probar `TeamEmployeeValidationAdapter`**

Crear `backend/src/test/java/com/turnoshift/unavailability/infrastructure/adapter/TeamEmployeeValidationAdapterTest.java` y `TeamEmployeeValidationAdapter.java` en `com.turnoshift.unavailability.infrastructure.adapter`:
- Delega en `com.turnoshift.team.domain.EmployeeRepository.findById(organizationId, employeeId)`.
- Retorna `true` si el empleado existe y `employee.isActive()` es `true`.
- Retorna `false` si el empleado no existe o está inactivo (`isActive() == false`).

---

### Task 3: [Application] Casos de Uso Deterministas con `Clock` y Servicio de Conflictos

**Files:**
- Create: `backend/src/main/java/com/turnoshift/unavailability/application/CreateUnavailabilityUseCase.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/application/GetUnavailabilityUseCase.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/application/ListEmployeeUnavailabilitiesUseCase.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/application/ListOrganizationUnavailabilitiesUseCase.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/application/UpdateUnavailabilityUseCase.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/application/CancelUnavailabilityUseCase.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/application/UnavailabilityConflictQueryService.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/application/CreateUnavailabilityUseCaseTest.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/application/GetUnavailabilityUseCaseTest.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/application/ListEmployeeUnavailabilitiesUseCaseTest.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/application/ListOrganizationUnavailabilitiesUseCaseTest.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/application/UpdateUnavailabilityUseCaseTest.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/application/CancelUnavailabilityUseCaseTest.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/application/UnavailabilityConflictQueryServiceTest.java`

- [ ] **Step 1: Escribir pruebas unitarias para `CreateUnavailabilityUseCaseTest`**

Verifica:
- Inyección de `Clock.fixed(now, ZoneOffset.UTC)`.
- Éxito al crear para colaborador activo con instantes UTC válidos.
- Error `EmployeeNotFoundException` si el colaborador no existe o está inactivo.
- Permisividad de solapamiento: registro exitoso aunque coincida con otra indisponibilidad del mismo colaborador.
- Error `PastIntervalModificationProhibitedException` si el intervalo completo ya finalizó en el pasado (`endsAt <= now`).

- [ ] **Step 2: Implementar `CreateUnavailabilityUseCase`**
Recibe en constructor: `EmployeeUnavailabilityRepository`, `EmployeeValidationPort`, `Clock`.
Método `execute(UUID organizationId, UUID employeeId, Instant startsAt, Instant endsAt, UnavailabilityReason reason, String rawNote, UUID actorUserId)`:
- Evalúa `Instant now = clock.instant()`.
- Valida con `employeeValidationPort.isEmployeeActive(organizationId, employeeId)`. Si es falso -> lanza `EmployeeNotFoundException`.
- Valida que `endsAt.isAfter(now)`. Si no -> lanza `PastIntervalModificationProhibitedException`.
- Construye `TimeInterval` y `RestrictedNote` (si `rawNote != null`).
- Construye `EmployeeUnavailability.create(..., now)` y persiste vía `repository.insert(...)`.

- [ ] **Step 3: Escribir pruebas unitarias e implementar `GetUnavailabilityUseCase`**

Crear `GetUnavailabilityUseCaseTest.java`:
- Verifica retorno exitoso si existe para `organizationId` y `id`.
- Lanza `UnavailabilityNotFoundException` si no existe o pertenece a otra organización.

Implementar `GetUnavailabilityUseCase.java`:
- Invoca `repository.findById(organizationId, id)`. Si está vacío -> lanza `UnavailabilityNotFoundException`.

- [ ] **Step 4: Escribir pruebas unitarias e implementar `ListEmployeeUnavailabilitiesUseCase` y `ListOrganizationUnavailabilitiesUseCase`**

Crear `ListEmployeeUnavailabilitiesUseCaseTest.java`:
- Verifica pertenencia con `EmployeeValidationPort`.
- Consulta con y sin canceladas (`includeCancelled`).

Crear `ListOrganizationUnavailabilitiesUseCaseTest.java`:
- Valida que `from.isBefore(to)` (si no, lanza `InvalidUnavailabilityDataException`).
- Consulta `repository.findByOrganizationAndInterval(organizationId, from, to, includeCancelled)`.

Implementar ambas clases en `com.turnoshift.unavailability.application`.

- [ ] **Step 5: Escribir pruebas unitarias e implementar `UpdateUnavailabilityUseCase` (RN-013 con `Clock`)**

Crear `UpdateUnavailabilityUseCaseTest.java`:
- Utiliza `Clock.fixed(...)`.
- Rechaza con `UnavailabilityNotFoundException` si no existe.
- Rechaza con `UnavailabilityAlreadyCancelledException` si ya está cancelada.
- **RN-013 estricto:** Rechaza con `PastIntervalModificationProhibitedException` si `existing.interval().startsAt() <= now`.
- Éxito si `startsAt > now`, invocando `repository.update(updated, expectedLockVersion)`.
- Si `repository.update` retorna vacío -> lanza `UnavailabilityConcurrentModificationException`.

Implementar `UpdateUnavailabilityUseCase.java` aplicando estas reglas.

- [ ] **Step 6: Escribir pruebas unitarias e implementar `CancelUnavailabilityUseCase` (RN-013 con `Clock`)**

Crear `CancelUnavailabilityUseCaseTest.java`:
- Utiliza `Clock.fixed(...)`.
- Rechaza con `UnavailabilityNotFoundException` si no existe.
- Rechaza con `UnavailabilityAlreadyCancelledException` si ya está cancelada.
- **RN-013 estricto:** Rechaza con `PastIntervalModificationProhibitedException` si `existing.interval().startsAt() <= now`.
- Éxito si `startsAt > now`, invocando `repository.cancel(organizationId, id, actorUserId, expectedLockVersion)`.
- Si `repository.cancel` retorna vacío -> lanza `UnavailabilityConcurrentModificationException`.

Implementar `CancelUnavailabilityUseCase.java` aplicando estas reglas.

- [ ] **Step 7: Escribir pruebas unitarias e implementar `UnavailabilityConflictQueryService` (Puerto para I3)**

Crear `UnavailabilityConflictQueryServiceTest.java` e implementar `UnavailabilityConflictQueryService.java`:
- Implementa `UnavailabilityConflictQueryPort`.
- `hasConflict`: consulta `findByEmployeeAndInterval(organizationId, employeeId, shiftStart, shiftEnd, false)` y evalúa `overlaps(shiftStart, shiftEnd)`.
- `findConflictingIntervals`: mapea las coincidentes a `List<TimeInterval>`.
- `findActiveIntervalsByEmployees`: carga por ventana organizacional y agrupa en memoria en `Map<UUID, List<TimeInterval>>`.

---

### Task 4: [Persistence] Adaptador JDBC para SQL Server

**Files:**
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/persistence/UnavailabilityDataAccessException.java`
- Create: `backend/src/main/java/com/turnoshift/unavailability/infrastructure/persistence/JdbcEmployeeUnavailabilityRepository.java`
- Test: `backend/src/test/java/com/turnoshift/unavailability/infrastructure/persistence/JdbcEmployeeUnavailabilityRepositoryIT.java`

**Interfaces:**
- Consumes: `JdbcTemplate`, tabla `employee_unavailabilities` (V17).
- Produces: Implementación de `EmployeeUnavailabilityRepository` en SQL Server.

- [ ] **Step 1: Escribir la prueba de integración `JdbcEmployeeUnavailabilityRepositoryIT`**

Crear `backend/src/test/java/com/turnoshift/unavailability/infrastructure/persistence/JdbcEmployeeUnavailabilityRepositoryIT.java` con Testcontainers SQL Server 2022:
1. Inserción con `insert(...)`: comprueba que `created_at` y `updated_at` son generados por `SYSUTCDATETIME()` en SQL Server y recuperados vía `OUTPUT inserted.*`.
2. Aislamiento multi-tenant: `findById(orgB, unavA)` retorna `Optional.empty()`.
3. Actualización condicional por `lock_version`:
   - `update(..., expectedLockVersion = 0)` retorna la entidad actualizada con `lock_version = 1`, nuevo intervalo y `updated_at` renovado por el servidor.
   - `update(..., expectedLockVersion = 0)` posterior falla retornando `Optional.empty()`.
4. Cancelación condicional por `lock_version` y comprobación de `cancelled_at`:
   - `cancel(orgId, id, actorUserId, expectedLockVersion)` ejecuta `UPDATE ... SET cancelled_at = SYSUTCDATETIME(), cancelled_by_user_id = ?, lock_version = lock_version + 1 OUTPUT inserted.*`.
   - Comprueba que `cancelled_at` no es nulo y corresponde a la marca de tiempo de SQL Server.
   - Cancelar de nuevo retorna `Optional.empty()` (ya cancelada).
5. Consultas de rango:
   - `findByEmployeeAndInterval` y `findByOrganizationAndInterval` con `includeCancelled = false` excluyen las canceladas.
   - Con `includeCancelled = true` incluyen las canceladas.

- [ ] **Step 2: Crear `UnavailabilityDataAccessException` en infraestructura**

Crear `backend/src/main/java/com/turnoshift/unavailability/infrastructure/persistence/UnavailabilityDataAccessException.java`:
```java
package com.turnoshift.unavailability.infrastructure.persistence;

import org.springframework.dao.DataAccessException;

public class UnavailabilityDataAccessException extends RuntimeException {
    public UnavailabilityDataAccessException(DataAccessException cause) {
        super("Error de acceso a datos en indisponibilidades", cause);
    }
}
```

- [ ] **Step 3: Implementar `JdbcEmployeeUnavailabilityRepository`**

Crear `backend/src/main/java/com/turnoshift/unavailability/infrastructure/persistence/JdbcEmployeeUnavailabilityRepository.java`:
- `RowMapper<EmployeeUnavailability>`:
  - Mapea columnas SQL a Value Objects del dominio.
  - Convierte `OffsetDateTime` a `Instant` en UTC.
  - Mapea `cancelled_at` y `cancelled_by_user_id` a `Optional<CancellationInfo>`.
- `insert(...)`:
  ```sql
  INSERT INTO employee_unavailabilities (
      id, organization_id, employee_id, starts_at, ends_at,
      reason_code, restricted_note, created_by_user_id,
      created_at, updated_at, lock_version
  )
  OUTPUT inserted.id, inserted.organization_id, inserted.employee_id,
         inserted.starts_at, inserted.ends_at, inserted.reason_code,
         inserted.restricted_note, inserted.created_by_user_id,
         inserted.created_at, inserted.updated_at, inserted.lock_version,
         inserted.cancelled_at, inserted.cancelled_by_user_id
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME(), SYSUTCDATETIME(), 0);
  ```
- `update(...)`:
  ```sql
  SET NOCOUNT ON;
  UPDATE employee_unavailabilities
  SET starts_at = ?,
      ends_at = ?,
      reason_code = ?,
      restricted_note = ?,
      lock_version = lock_version + 1,
      updated_at = SYSUTCDATETIME()
  OUTPUT inserted.id, inserted.organization_id, inserted.employee_id,
         inserted.starts_at, inserted.ends_at, inserted.reason_code,
         inserted.restricted_note, inserted.created_by_user_id,
         inserted.created_at, inserted.updated_at, inserted.lock_version,
         inserted.cancelled_at, inserted.cancelled_by_user_id
  WHERE organization_id = ?
    AND id = ?
    AND lock_version = ?
    AND cancelled_at IS NULL;
  ```
- `cancel(...)`:
  ```sql
  SET NOCOUNT ON;
  UPDATE employee_unavailabilities
  SET cancelled_at = SYSUTCDATETIME(),
      cancelled_by_user_id = ?,
      lock_version = lock_version + 1,
      updated_at = SYSUTCDATETIME()
  OUTPUT inserted.id, inserted.organization_id, inserted.employee_id,
         inserted.starts_at, inserted.ends_at, inserted.reason_code,
         inserted.restricted_note, inserted.created_by_user_id,
         inserted.created_at, inserted.updated_at, inserted.lock_version,
         inserted.cancelled_at, inserted.cancelled_by_user_id
  WHERE organization_id = ?
    AND id = ?
    AND lock_version = ?
    AND cancelled_at IS NULL;
  ```
- `findById`, `findByEmployee`, `findByEmployeeAndInterval`, `findByOrganizationAndInterval`:
  - Todas exigen `organization_id = ?`.
  - Respetan el flag `includeCancelled`.
- Manejo de excepciones: encapsula `DataAccessException` en `UnavailabilityDataAccessException`.

---

### Task 5: [Verification] Verificación Focalizada Final de I2.4-b

**Files:** Ninguno (ejecución de pruebas).

- [ ] **Step 1: Ejecutar la suite focalizada completa de I2.4-b**

Ejecutar exactamente:
```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=TimeIntervalTest,RestrictedNoteTest,EmployeeUnavailabilityTest,TeamEmployeeValidationAdapterTest,CreateUnavailabilityUseCaseTest,GetUnavailabilityUseCaseTest,ListEmployeeUnavailabilitiesUseCaseTest,ListOrganizationUnavailabilitiesUseCaseTest,UpdateUnavailabilityUseCaseTest,CancelUnavailabilityUseCaseTest,UnavailabilityConflictQueryServiceTest,JdbcEmployeeUnavailabilityRepositoryIT" -B
```

Resultado esperado: **BUILD SUCCESS** con todas las pruebas unitarias y de integración de persistencia aprobadas.

---

## 3. Comando Único de Verificación Focalizado para I2.4-b

Para verificar de forma estricta y completa el entregable de I2.4-b:

```powershell
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
.\mvnw.cmd test "-Dtest=TimeIntervalTest,RestrictedNoteTest,EmployeeUnavailabilityTest,TeamEmployeeValidationAdapterTest,CreateUnavailabilityUseCaseTest,GetUnavailabilityUseCaseTest,ListEmployeeUnavailabilitiesUseCaseTest,ListOrganizationUnavailabilitiesUseCaseTest,UpdateUnavailabilityUseCaseTest,CancelUnavailabilityUseCaseTest,UnavailabilityConflictQueryServiceTest,JdbcEmployeeUnavailabilityRepositoryIT" -B
```

No se deben ejecutar `mvn clean`, `mvn verify`, plugins `failsafe` ni suites globales en esta fase.
