# 18 — Plan de Implementación I2.3-b: Dominio, Casos de Uso y Adaptador JDBC

> **Para desarrolladores / agentes:**  
> **Sub-habilidad requerida:** Utilizar desarrollo guiado por subagentes (`superpowers:subagent-driven-development`) o ejecución paso a paso (`superpowers:executing-plans`).  
> **Seguimiento:** Cada tarea y paso se controla mediante casillas de verificación (`- [ ]`).  
> **Regla de oro:** No escribir código sin prueba previa (TDD estricto). No modificar código de capas superiores (HTTP, DTOs, OpenAPI) ni migraciones previas.

**Meta:** Construir el núcleo de dominio inmutable, las reglas de validación de umbrales y motivo, el cálculo determinista y verificación estricta de la huella canónica (`validationFingerprint`), los casos de uso operativos (consulta de política actual, historial, previsualización sin persistencia y activación transaccional), la interfaz de persistencia `PolicyRepository` y su implementación `JdbcPolicyRepository` con timestamps autoritativos generados por SQL Server (`OUTPUT inserted.*`), serialización segura mediante `sp_getapplock` por organización con captura explícita de `@lockResult` vía `SELECT`, y actualización atómica del puntero en SQL Server.

**Arquitectura:** Arquitectura Hexagonal / Clean Architecture: Dominio puro desacoplado de frameworks en `com.turnoshift.policy.domain`, casos de uso orquestadores en `com.turnoshift.policy.application` y adaptador de persistencia en `com.turnoshift.policy.infrastructure.persistence`. Las transacciones se demarcan en los casos de uso (`@Transactional`) y el repositorio JDBC interactúa con SQL Server a través de `JdbcTemplate`, verificando la presencia obligatoria de una transacción activa para la adquisición del candado de aplicación y delegando la autoría temporal íntegramente a `SYSUTCDATETIME()` en base de datos.

**Tech Stack:** Java 21, Spring Boot 4.0.0 (Spring Transaction Management, Spring JDBC / `JdbcTemplate`, MSSQL JDBC Driver), Testcontainers 2.0.2 (SQL Server 2022 CU16), JUnit 5 (JUnit Jupiter 6.0.1), AssertJ, Mockito.

**Especificación de Referencia:** [docs/planning/16 - Diseño Técnico I2.3 - Política Operativa Inicial.md](file:///d:/Proyectos/TurnoShift/docs/planning/16%20-%20Dise%C3%B1o%20T%C3%A9cnico%20I2.3%20-%20Pol%C3%ADtica%20Operativa%20Inicial.md).

---

## Restricciones Globales de I2.3-b

1. **Aislamiento de Capa:** Prohibido incorporar clases HTTP, controladores Spring Web, DTOs web, anotaciones CSRF, filtros de seguridad RBAC, lógica de idempotencia HTTP (`PolicyIdempotentCommand`) o modificaciones al contrato OpenAPI (`docs/api/openapi.yaml`). Todo esto pertenece exclusivamente a `I2.3-c` e `I2.3-d`.
2. **Inmutabilidad de Persistencia Relacional:** Prohibido modificar las migraciones `V1` a `V16` existentes.
3. **Manejo Estricto de Bloqueo de Aplicación:** `sp_getapplock` debe invocarse exclusivamente dentro de una transacción Spring activa (`TransactionSynchronizationManager.isActualTransactionActive() == true`), con timeout de 5000 ms y recurso `'TurnoShift:Policy:Org:' + organizationId`. Se recupera explícitamente el entero `@lockResult` retornado por `sp_getapplock` mediante `SELECT @res`. Si `@lockResult >= 0`, se continúa; si `@lockResult < 0`, se lanza `PolicyOperationBusyException` directamente sin usar `RAISERROR` ni parsear cadenas de texto. Cualquier otro error JDBC se traduce a `PolicyDataAccessException`.
4. **Regla de Motivo (`PolicyReason`):** Obligatorio (no nulo), longitud máxima 500 unidades UTF-16, y rechazo estricto si todos sus puntos de código cumplen `Character.isWhitespace(cp) || Character.isSpaceChar(cp)` (abarcando ASCII y espacios Unicode no separables como `\u00A0`).
5. **Huella Canónica Estricta (`ValidationFingerprint`):** 
   - El valor recibido debe cumplir exactamente la expresión regular `^[0-9a-f]{64}$`. Se rechazan mayúsculas, longitudes distintas de 64 y caracteres no hexadecimales lanzando `PolicyValidationException` con código `INVALID_VALIDATION_FINGERPRINT`.
   - La verificación compara con igualdad exacta binaria (`equals`), nunca con `equalsIgnoreCase`.
   - Construcción canónica UTF-8: `v1|{organizationId}|{expectedCurrentPolicyVersionIdOrNull}|{minimumRestMinutes}|{weeklyWarningMinutes}` y cálculo SHA-256 en hexadecimal minúsculo.
6. **Timestamps Autoritativos:**
   - La aplicación Java no genera `effectiveAt` ni `createdAt` en `ActivatePolicyUseCase` (se elimina dependencia de `Clock`).
   - El caso de uso crea un `NewPolicyVersion` que transporta únicamente los datos aportados por la petición y la secuencia (`organizationId`, `versionNumber`, `thresholds`, `createdByUserId`, `changeReason`).
   - `PolicyRepository.insertPolicyVersion(NewPolicyVersion)` retorna el `PolicyVersion` completo ya persistido.
   - `JdbcPolicyRepository` ejecuta el `INSERT` omitiendo las columnas temporales (permitiendo que actúe el default `SYSUTCDATETIME()`) y recupera `id`, `effective_at` y `created_at` autoritativos mediante la cláusula `OUTPUT inserted.*`.
7. **Estrategia de Pruebas:**
   - Pruebas unitarias de dominio y casos de uso: puras, ultrarrápidas, sin Spring context ni Testcontainers.
   - Pruebas de persistencia JDBC: una única clase de integración (`JdbcPolicyRepositoryIT`) que ejecute contra SQL Server real vía Testcontainers, incluyendo una prueba real de contención de bloqueo ocupado entre conexiones concurrentes.
8. **Comandos de Verificación:** Queda estrictamente prohibido ejecutar `mvn clean`, `mvn verify`, `mvn failsafe` o suites completas globales. Solo se permiten comandos focalizados sobre las clases de este subincremento.

---

## 1. Inventario Exacto de Archivos

### 1.1. Archivos a Crear en Código de Producción (`backend/src/main/java/com/turnoshift/policy/`)

- `domain/PolicyThresholds.java`: Value object inmutable con validación de límites (1..1440 min para descanso, 1..10080 min para aviso semanal).
- `domain/PolicyReason.java`: Value object inmutable con validación estricta Unicode (no nulo, máx 500 UTF-16, rechazo de solo espacios).
- `domain/PolicyDelta.java`: Value object con la diferencia aritmética respecto a la política previa.
- `domain/ValidationFingerprint.java`: Value object y lógica canónica v1 (validación de formato exacto `^[0-9a-f]{64}$`, cálculo SHA-256 minúsculo, verificación estricta por `equals`).
- `domain/NewPolicyVersion.java`: Objeto de entrada inmutable para la inserción en base de datos sin timestamps (`organizationId`, `versionNumber`, `thresholds`, `createdByUserId`, `changeReason`).
- `domain/PolicyVersion.java`: Modelo inmutable de la versión de política ya persistida con timestamps autoritativos (`id`, `organizationId`, `versionNumber`, `thresholds`, `effectiveAt`, `createdByUserId`, `changeReason`, `createdAt`).
- `domain/PolicyRepository.java`: Puerto de persistencia en el dominio.
- `domain/PolicyValidationException.java`: Excepción de dominio para fallos de validación, motivo y huella inconsistente.
- `domain/PolicyConcurrentModificationException.java`: Excepción para discrepancias en `expectedCurrentPolicyVersionId`.
- `domain/PolicyOperationBusyException.java`: Excepción de concurrencia cuando `sp_getapplock` retorna `@lockResult < 0`.
- `domain/PolicyDataAccessException.java`: Excepción de infraestructura de datos.
- `application/GetCurrentPolicyUseCase.java`: Caso de uso para obtener la política actual de una organización.
- `application/GetPolicyHistoryUseCase.java`: Caso de uso para obtener el historial ordenado por `version_number DESC`.
- `application/PreviewPolicyChangeUseCase.java`: Caso de uso para evaluar una propuesta (verificación de concurrencia, cálculo de delta y huella) sin persistencia.
- `application/PolicyPreviewResult.java`: Record de salida del caso de uso de previsualización.
- `application/ActivatePolicyUseCase.java`: Caso de uso transaccional para adquirir candado, verificar huella y versión previa, crear versión $N+1$ y actualizar el puntero atómicamente retornando la versión persistida.
- `application/ActivatePolicyCommand.java`: Record de entrada para el caso de uso de activación.
- `infrastructure/persistence/JdbcPolicyRepository.java`: Adaptador de persistencia JDBC sobre SQL Server con soporte de `sp_getapplock` mediante `SELECT @res` y captura de timestamps con `OUTPUT inserted.*`.

### 1.2. Archivos a Crear en Código de Pruebas (`backend/src/test/java/com/turnoshift/policy/`)

- `domain/PolicyThresholdsTest.java`: Pruebas unitarias de rangos y validación de umbrales.
- `domain/PolicyReasonTest.java`: Pruebas unitarias de longitud, espacios ASCII y espacios Unicode no separables.
- `domain/ValidationFingerprintTest.java`: Pruebas unitarias de formato estricto `[0-9a-f]{64}`, rechazo de mayúsculas, longitudes erróneas, caracteres no hexadecimales y comparación exacta.
- `domain/PolicyDeltaTest.java`: Pruebas unitarias de cálculo de deltas con y sin política previa.
- `application/GetCurrentPolicyUseCaseTest.java`: Pruebas unitarias con mocks de lectura actual.
- `application/GetPolicyHistoryUseCaseTest.java`: Pruebas unitarias con mocks de historial.
- `application/PreviewPolicyChangeUseCaseTest.java`: Pruebas unitarias de evaluación, deltas, huella y concurrencia sin persistencia.
- `application/ActivatePolicyUseCaseTest.java`: Pruebas unitarias de orquestación transaccional, bloqueo, huella inválida, modificación concurrente, avance de versión y retorno intacto de timestamps autoritativos provistos por el repositorio.
- `infrastructure/persistence/JdbcPolicyRepositoryIT.java`: Pruebas de integración sobre SQL Server real (Testcontainers), incluyendo verificación de timestamps autoritativos de SQL Server y prueba real de contención de bloqueo ocupado entre dos conexiones concurrentes.

---

## 2. Definición Técnica de Interfaces y Contratos

### 2.1. Value Objects y Modelos de Dominio

```java
package com.turnoshift.policy.domain;

public record PolicyThresholds(int minimumRestMinutes, int weeklyWarningMinutes) {
    public static final int MIN_REST_MIN = 1;
    public static final int MAX_REST_MIN = 1440; // 24 horas
    public static final int MIN_WEEKLY_WARNING_MIN = 1;
    public static final int MAX_WEEKLY_WARNING_MIN = 10080; // 7 días

    public PolicyThresholds {
        if (minimumRestMinutes < MIN_REST_MIN || minimumRestMinutes > MAX_REST_MIN) {
            throw new PolicyValidationException(
                "VALIDATION_FAILED",
                "El descanso mínimo debe estar entre " + MIN_REST_MIN + " y " + MAX_REST_MIN + " minutos"
            );
        }
        if (weeklyWarningMinutes < MIN_WEEKLY_WARNING_MIN || weeklyWarningMinutes > MAX_WEEKLY_WARNING_MIN) {
            throw new PolicyValidationException(
                "VALIDATION_FAILED",
                "El aviso semanal debe estar entre " + MIN_WEEKLY_WARNING_MIN + " y " + MAX_WEEKLY_WARNING_MIN + " minutos"
            );
        }
    }
}
```

```java
package com.turnoshift.policy.domain;

public record PolicyReason(String value) {
    public static final int MAX_LENGTH = 500;

    public PolicyReason {
        if (value == null) {
            throw new PolicyValidationException("REASON_REQUIRED", "El motivo de activación es obligatorio");
        }
        if (value.length() > MAX_LENGTH) {
            throw new PolicyValidationException("REASON_REQUIRED", "El motivo no puede exceder " + MAX_LENGTH + " caracteres");
        }
        boolean isAllWhitespace = true;
        int length = value.length();
        int offset = 0;
        while (offset < length) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                isAllWhitespace = false;
                break;
            }
            offset += Character.charCount(codePoint);
        }
        if (length == 0 || isAllWhitespace) {
            throw new PolicyValidationException("REASON_REQUIRED", "El motivo de activación no puede estar vacío ni compuesto solo por espacios");
        }
    }
}
```

```java
package com.turnoshift.policy.domain;

import java.util.Optional;

public record PolicyDelta(Integer minimumRestMinutesDelta, Integer weeklyWarningMinutesDelta) {
    public static PolicyDelta calculate(Optional<PolicyThresholds> currentThresholds, PolicyThresholds proposed) {
        if (currentThresholds.isEmpty()) {
            return null;
        }
        PolicyThresholds current = currentThresholds.get();
        return new PolicyDelta(
            proposed.minimumRestMinutes() - current.minimumRestMinutes(),
            proposed.weeklyWarningMinutes() - current.weeklyWarningMinutes()
        );
    }
}
```

```java
package com.turnoshift.policy.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

public record ValidationFingerprint(String value) {

    private static final Pattern HEX_LOWERCASE_64 = Pattern.compile("^[0-9a-f]{64}$");

    public ValidationFingerprint {
        if (value == null || !HEX_LOWERCASE_64.matcher(value).matches()) {
            throw new PolicyValidationException(
                "INVALID_VALIDATION_FINGERPRINT",
                "La huella de validación debe ser una cadena hexadecimal de 64 caracteres en minúsculas"
            );
        }
    }

    public static String computeCanonicalString(
            UUID organizationId,
            UUID expectedCurrentPolicyVersionId,
            PolicyThresholds thresholds
    ) {
        String expectedCurrentStr = expectedCurrentPolicyVersionId == null ? "null" : expectedCurrentPolicyVersionId.toString().toLowerCase();
        return "v1|" + organizationId.toString().toLowerCase() + "|" + expectedCurrentStr + "|"
                + thresholds.minimumRestMinutes() + "|" + thresholds.weeklyWarningMinutes();
    }

    public static ValidationFingerprint calculate(
            UUID organizationId,
            UUID expectedCurrentPolicyVersionId,
            PolicyThresholds thresholds
    ) {
        String canonical = computeCanonicalString(organizationId, expectedCurrentPolicyVersionId, thresholds);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return new ValidationFingerprint(HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }

    public void verify(
            UUID organizationId,
            UUID expectedCurrentPolicyVersionId,
            PolicyThresholds thresholds
    ) {
        ValidationFingerprint expected = calculate(organizationId, expectedCurrentPolicyVersionId, thresholds);
        if (!this.value.equals(expected.value())) { // Comparación exacta y estricta (no equalsIgnoreCase)
            throw new PolicyValidationException(
                "INVALID_VALIDATION_FINGERPRINT",
                "La huella de validación proporcionada no coincide con la propuesta calculada"
            );
        }
    }
}
```

```java
package com.turnoshift.policy.domain;

import java.util.Objects;
import java.util.UUID;

public record NewPolicyVersion(
        UUID organizationId,
        int versionNumber,
        PolicyThresholds thresholds,
        UUID createdByUserId,
        PolicyReason changeReason
) {
    public NewPolicyVersion {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        Objects.requireNonNull(thresholds, "thresholds no puede ser nulo");
        Objects.requireNonNull(createdByUserId, "createdByUserId no puede ser nulo");
        Objects.requireNonNull(changeReason, "changeReason no puede ser nulo");
        if (versionNumber <= 0) {
            throw new IllegalArgumentException("versionNumber debe ser estrictamente positivo");
        }
    }
}
```

```java
package com.turnoshift.policy.domain;

import java.time.Instant;
import java.util.UUID;

public record PolicyVersion(
        UUID id,
        UUID organizationId,
        int versionNumber,
        PolicyThresholds thresholds,
        Instant effectiveAt,
        UUID createdByUserId,
        PolicyReason changeReason,
        Instant createdAt
) {}
```

### 2.2. Puerto de Repositorio en Dominio

```java
package com.turnoshift.policy.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository {

    Optional<PolicyVersion> findCurrentPolicy(UUID organizationId);

    Optional<UUID> findCurrentPolicyVersionId(UUID organizationId);

    List<PolicyVersion> findHistory(UUID organizationId);

    int findNextVersionNumber(UUID organizationId);

    PolicyVersion insertPolicyVersion(NewPolicyVersion newVersion);

    void updateCurrentPolicyVersion(UUID organizationId, UUID newPolicyVersionId, UUID expectedCurrentPolicyVersionId);

    void acquirePolicyLock(UUID organizationId);
}
```

---

## 3. Plan de Tareas TDD Paso a Paso

### Tarea 1: Value Object `PolicyThresholds` y Validación de Límites

**Archivos:**
- Crear: `backend/src/main/java/com/turnoshift/policy/domain/PolicyValidationException.java`
- Crear: `backend/src/main/java/com/turnoshift/policy/domain/PolicyThresholds.java`
- Test: `backend/src/test/java/com/turnoshift/policy/domain/PolicyThresholdsTest.java`

**Interfaces:**
- Produce: `PolicyThresholds(int minimumRestMinutes, int weeklyWarningMinutes)`, `PolicyValidationException(String errorCode, String message)`.

- [ ] **Paso 1.1: Escribir la prueba unitaria que falla (Red)**
  Crear `PolicyThresholdsTest.java` verificando:
  - Creación válida con valores por defecto (660, 2400) y extremos (1, 1440, 10080).
  - Rechazo con `PolicyValidationException` y código `VALIDATION_FAILED` cuando `minimumRestMinutes` es 0, -1 o 1441.
  - Rechazo con `PolicyValidationException` y código `VALIDATION_FAILED` cuando `weeklyWarningMinutes` es 0, -1 o 10081.

- [ ] **Paso 1.2: Ejecutar la prueba y comprobar fallo**
  Comando:
  `powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test '-Dtest=PolicyThresholdsTest' -B"`
  Resultado esperado: Fallo de compilación / clases no encontradas.

- [ ] **Paso 1.3: Implementar `PolicyValidationException` y `PolicyThresholds` (Green)**
  Escribir `PolicyValidationException.java` heredando de `RuntimeException` con campos `errorCode` y `message`.
  Escribir `PolicyThresholds.java` como `record` con validación en el constructor compacto.

- [ ] **Paso 1.4: Ejecutar la prueba y comprobar éxito (Green)**
  Comando:
  `powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test '-Dtest=PolicyThresholdsTest' -B"`
  Resultado esperado: Tests run: 3+, Failures: 0, Errors: 0, BUILD SUCCESS.

---

### Tarea 2: Value Object `PolicyReason` y Reglas Unicode

**Archivos:**
- Crear: `backend/src/main/java/com/turnoshift/policy/domain/PolicyReason.java`
- Test: `backend/src/test/java/com/turnoshift/policy/domain/PolicyReasonTest.java`

**Interfaces:**
- Consume: `PolicyValidationException`
- Produce: `PolicyReason(String value)`

- [ ] **Paso 2.1: Escribir la prueba unitaria que falla (Red)**
  Crear `PolicyReasonTest.java` verificando:
  - Creación válida con texto estándar y texto con espacios internos y caracteres Unicode acentuados.
  - Rechazo por valor nulo (`REASON_REQUIRED`).
  - Rechazo por cadena vacía `""` (`REASON_REQUIRED`).
  - Rechazo por cadena con espacios ASCII `"   \t\n  "` (`REASON_REQUIRED`).
  - Rechazo por cadena con espacios Unicode no separables (`"\u00A0\u00A0"`, `"\u2009"`, `"\u3000"`) (`REASON_REQUIRED`).
  - Rechazo cuando la longitud excede 500 unidades de código UTF-16 (`REASON_REQUIRED`).
  - Aceptación cuando contiene caracteres no espaciales acompañados de espacios Unicode intermedios o extremos.

- [ ] **Paso 2.2: Ejecutar la prueba y comprobar fallo**
  Comando:
  `powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test '-Dtest=PolicyReasonTest' -B"`
  Resultado esperado: Fallo de compilación.

- [ ] **Paso 2.3: Implementar `PolicyReason` (Green)**
  Implementar la lógica con inspección exhaustiva de puntos de código:
  `Character.isWhitespace(cp) || Character.isSpaceChar(cp)`.

- [ ] **Paso 2.4: Ejecutar la prueba y comprobar éxito**
  Comando:
  `powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test '-Dtest=PolicyReasonTest' -B"`
  Resultado esperado: BUILD SUCCESS.

---

### Tarea 3: Value Object `PolicyDelta`

**Archivos:**
- Crear: `backend/src/main/java/com/turnoshift/policy/domain/PolicyDelta.java`
- Test: `backend/src/test/java/com/turnoshift/policy/domain/PolicyDeltaTest.java`

**Interfaces:**
- Consume: `PolicyThresholds`
- Produce: `PolicyDelta(Integer minimumRestMinutesDelta, Integer weeklyWarningMinutesDelta)`

- [ ] **Paso 3.1: Escribir la prueba unitaria que falla (Red)**
  Crear `PolicyDeltaTest.java` verificando:
  - Cuando no hay política previa (`Optional.empty()`), el método `calculate` retorna `null`.
  - Cuando existe política previa (ej. 660, 2400) y la propuesta es (720, 2400), retorna delta (+60, 0).
  - Deltas negativos y nulos calculados aritméticamente de manera exacta.

- [ ] **Paso 3.2: Implementar `PolicyDelta` (Green)**
  Escribir el record `PolicyDelta` y su método estático `calculate`.

- [ ] **Paso 3.3: Ejecutar la prueba y comprobar éxito**
  Comando:
  `powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test '-Dtest=PolicyDeltaTest' -B"`
  Resultado esperado: BUILD SUCCESS.

---

### Tarea 4: Generación y Verificación Estricta de `ValidationFingerprint`

**Archivos:**
- Crear: `backend/src/main/java/com/turnoshift/policy/domain/ValidationFingerprint.java`
- Test: `backend/src/test/java/com/turnoshift/policy/domain/ValidationFingerprintTest.java`

**Interfaces:**
- Consume: `PolicyThresholds`, `PolicyValidationException`
- Produce: `ValidationFingerprint` (formato estricto `^[0-9a-f]{64}$`, métodos `computeCanonicalString`, `calculate`, `verify`)

- [ ] **Paso 4.1: Escribir la prueba unitaria que falla (Red)**
  Crear `ValidationFingerprintTest.java` verificando:
  - Formato canónico exacto con `expectedCurrentPolicyVersionId == null` -> `v1|{orgId}|null|660|2400`.
  - Formato canónico exacto con UUID esperado -> `v1|{orgId}|{policyId}|660|2400`.
  - Formato de hash: exactamente 64 caracteres en minúsculas `[0-9a-f]{64}`.
  - Rechazo de valor nulo con `PolicyValidationException("INVALID_VALIDATION_FINGERPRINT", ...)`.
  - Rechazo de letras mayúsculas (ej. conteniendo `A-F`) con `INVALID_VALIDATION_FINGERPRINT`.
  - Rechazo de longitud incorrecta (63 caracteres o 65 caracteres) con `INVALID_VALIDATION_FINGERPRINT`.
  - Rechazo de caracteres no hexadecimales (ej. letras `g-z`) con `INVALID_VALIDATION_FINGERPRINT`.
  - Verificación exitosa cuando el hash coincide exactamente byte a byte.
  - Verificación fallida con `PolicyValidationException("INVALID_VALIDATION_FINGERPRINT", ...)` cuando difiere el valor o cuando se envía una versión en mayúsculas (rechazo estricto sin `equalsIgnoreCase`).

- [ ] **Paso 4.2: Implementar `ValidationFingerprint` (Green)**
  Implementar la clase con validación regex `^[0-9a-f]{64}$`, `MessageDigest` SHA-256, codificación UTF-8 y comparación estricta `.equals()`.

- [ ] **Paso 4.3: Ejecutar la prueba y comprobar éxito**
  Comando:
  `powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test '-Dtest=ValidationFingerprintTest' -B"`
  Resultado esperado: BUILD SUCCESS.

---

### Tarea 5: Entidades de Dominio (`NewPolicyVersion`, `PolicyVersion`) y Excepciones

**Archivos:**
- Crear: `backend/src/main/java/com/turnoshift/policy/domain/NewPolicyVersion.java`
- Crear: `backend/src/main/java/com/turnoshift/policy/domain/PolicyVersion.java`
- Crear: `backend/src/main/java/com/turnoshift/policy/domain/PolicyConcurrentModificationException.java`
- Crear: `backend/src/main/java/com/turnoshift/policy/domain/PolicyOperationBusyException.java`
- Crear: `backend/src/main/java/com/turnoshift/policy/domain/PolicyDataAccessException.java`

**Interfaces:**
- Consume: `PolicyThresholds`, `PolicyReason`
- Produce: `NewPolicyVersion`, `PolicyVersion` y las 3 excepciones.

- [ ] **Paso 5.1: Implementar las excepciones de dominio**
  - `PolicyConcurrentModificationException`: para discrepancias en `expectedCurrentPolicyVersionId`.
  - `PolicyOperationBusyException`: cuando `@lockResult < 0`.
  - `PolicyDataAccessException`: para fallos inesperados de persistencia o JDBC.

- [ ] **Paso 5.2: Implementar los records `NewPolicyVersion` y `PolicyVersion`**
  `NewPolicyVersion` para entrada de inserción sin timestamps Java.
  `PolicyVersion` para representación completa inmutable de la fila persistida.

---

### Tarea 6: Casos de Uso de Consulta (`GetCurrentPolicyUseCase`, `GetPolicyHistoryUseCase`)

**Archivos:**
- Crear: `backend/src/main/java/com/turnoshift/policy/domain/PolicyRepository.java`
- Crear: `backend/src/main/java/com/turnoshift/policy/application/GetCurrentPolicyUseCase.java`
- Crear: `backend/src/main/java/com/turnoshift/policy/application/GetPolicyHistoryUseCase.java`
- Test: `backend/src/test/java/com/turnoshift/policy/application/GetCurrentPolicyUseCaseTest.java`
- Test: `backend/src/test/java/com/turnoshift/policy/application/GetPolicyHistoryUseCaseTest.java`

**Interfaces:**
- Consume: `PolicyRepository`, `PolicyVersion`
- Produce: `GetCurrentPolicyUseCase.get(UUID orgId)`, `GetPolicyHistoryUseCase.get(UUID orgId)`

- [ ] **Paso 6.1: Escribir las pruebas unitarias con Mocks (Red)**
  - `GetCurrentPolicyUseCaseTest`: retorna `Optional.empty()` si no hay política; retorna `Optional.of(policy)` si existe.
  - `GetPolicyHistoryUseCaseTest`: retorna lista vacía si no hay versiones; retorna lista ordenada descendente delegada del repositorio.

- [ ] **Paso 6.2: Implementar la interfaz `PolicyRepository` y los casos de uso (Green)**
  Definir la interfaz `PolicyRepository` en `domain` con método `PolicyVersion insertPolicyVersion(NewPolicyVersion newVersion)`.
  Implementar `GetCurrentPolicyUseCase` y `GetPolicyHistoryUseCase` anotados con `@Service` y `@Transactional(readOnly = true)`.

- [ ] **Paso 6.3: Ejecutar las pruebas y comprobar éxito**
  Comando:
  `powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test '-Dtest=GetCurrentPolicyUseCaseTest,GetPolicyHistoryUseCaseTest' -B"`
  Resultado esperado: BUILD SUCCESS.

---

### Tarea 7: Caso de Uso de Evaluación (`PreviewPolicyChangeUseCase`)

**Archivos:**
- Crear: `backend/src/main/java/com/turnoshift/policy/application/PolicyPreviewResult.java`
- Crear: `backend/src/main/java/com/turnoshift/policy/application/PreviewPolicyChangeUseCase.java`
- Test: `backend/src/test/java/com/turnoshift/policy/application/PreviewPolicyChangeUseCaseTest.java`

**Interfaces:**
- Consume: `PolicyRepository`, `PolicyThresholds`, `PolicyDelta`, `ValidationFingerprint`, `PolicyConcurrentModificationException`
- Produce: `PolicyPreviewResult preview(UUID orgId, UUID expectedCurrentPolicyVersionId, PolicyThresholds proposed)`

- [ ] **Paso 7.1: Escribir la prueba unitaria con Mocks (Red)**
  Verificar:
  1. Organización sin política previa y `expectedCurrentPolicyVersionId == null`: genera preview con `currentPolicy = null`, `delta = null`, `canProceed = true`, `blockers = []`, `warnings = []`, `reasonRequired = true` y `validationFingerprint` coherente.
  2. Organización con política v1 existente y `expectedCurrentPolicyVersionId == v1.id()`: genera preview con `currentPolicy` poblada, `delta` calculado y huella canónica v1.
  3. Detección de modificación concurrente: si la base tiene v1 pero se envía `expectedCurrentPolicyVersionId = null`, o si se envía un ID diferente al de la versión vigente, lanza `PolicyConcurrentModificationException`.
  4. Ninguna mutación se realiza sobre el repositorio.

- [ ] **Paso 7.2: Implementar `PreviewPolicyChangeUseCase` y `PolicyPreviewResult` (Green)**
  Implementar el servicio de previsualización sin persistencia y de solo lectura.

- [ ] **Paso 7.3: Ejecutar la prueba y comprobar éxito**
  Comando:
  `powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test '-Dtest=PreviewPolicyChangeUseCaseTest' -B"`
  Resultado esperado: BUILD SUCCESS.

---

### Tarea 8: Caso de Uso de Activación Transaccional con Timestamps Autoritativos

**Archivos:**
- Crear: `backend/src/main/java/com/turnoshift/policy/application/ActivatePolicyCommand.java`
- Crear: `backend/src/main/java/com/turnoshift/policy/application/ActivatePolicyUseCase.java`
- Test: `backend/src/test/java/com/turnoshift/policy/application/ActivatePolicyUseCaseTest.java`

**Interfaces:**
- Consume: `PolicyRepository`, `NewPolicyVersion`, `ValidationFingerprint`, `PolicyThresholds`, `PolicyReason`, `PolicyVersion`
- Produce: `PolicyVersion activate(ActivatePolicyCommand command)`

- [ ] **Paso 8.1: Escribir la prueba unitaria con Mocks (Red)**
  Verificar el flujo secuencial estricto:
  1. Adquiere candado exclusivo llamando a `repository.acquirePolicyLock(orgId)`.
  2. Si la huella no coincide o tiene formato inválido, lanza `PolicyValidationException("INVALID_VALIDATION_FINGERPRINT", ...)`.
  3. Si la versión actual no coincide con `command.expectedCurrentPolicyVersionId()`, lanza `PolicyConcurrentModificationException`.
  4. Construye `NewPolicyVersion` sin timestamps Java y lo pasa a `repository.insertPolicyVersion(newVersionDraft)`.
  5. Pasa el `id` retornado por el repositorio a `repository.updateCurrentPolicyVersion(...)`.
  6. **Timestamps autoritativos:** Retorna exactamente la instancia de `PolicyVersion` entregada por el repositorio, garantizando que `effectiveAt` y `createdAt` provienen del motor de base de datos y no de un reloj Java.
  7. Si `acquirePolicyLock` falla por recurso ocupado (`PolicyOperationBusyException`), la ejecución se interrumpe de inmediato.

- [ ] **Paso 8.2: Implementar `ActivatePolicyUseCase` (Green)**
  ```java
  @Service
  @Transactional
  public class ActivatePolicyUseCase {
      private final PolicyRepository repository;

      public ActivatePolicyUseCase(PolicyRepository repository) {
          this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
      }

      public PolicyVersion activate(ActivatePolicyCommand command) {
          // 1. Candado exclusivo por organización
          repository.acquirePolicyLock(command.organizationId());

          // 2. Verificación estricta de fingerprint
          ValidationFingerprint fingerprint = new ValidationFingerprint(command.validationFingerprint());
          fingerprint.verify(command.organizationId(), command.expectedCurrentPolicyVersionId(), command.proposedThresholds());

          // 3. Verificación de versión actual
          Optional<UUID> currentIdOpt = repository.findCurrentPolicyVersionId(command.organizationId());
          UUID actualCurrentId = currentIdOpt.orElse(null);
          if (!Objects.equals(actualCurrentId, command.expectedCurrentPolicyVersionId())) {
              throw new PolicyConcurrentModificationException("La versión actual de la política ha cambiado concurrentemente");
          }

          // 4. Determinar siguiente número de versión
          int nextVersion = repository.findNextVersionNumber(command.organizationId());

          // 5. Crear NewPolicyVersion sin timestamps e insertar recuperando la versión persistida
          NewPolicyVersion draft = new NewPolicyVersion(
              command.organizationId(),
              nextVersion,
              command.proposedThresholds(),
              command.actorUserId(),
              command.changeReason()
          );
          PolicyVersion persistedVersion = repository.insertPolicyVersion(draft);

          // 6. Actualizar puntero en organizations atómicamente con el ID asignado por BD
          repository.updateCurrentPolicyVersion(command.organizationId(), persistedVersion.id(), command.expectedCurrentPolicyVersionId());

          // 7. Retornar versión con timestamps autoritativos generados por SQL Server
          return persistedVersion;
      }
  }
  ```

- [ ] **Paso 8.3: Ejecutar la prueba y comprobar éxito**
  Comando:
  `powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test '-Dtest=ActivatePolicyUseCaseTest' -B"`
  Resultado esperado: BUILD SUCCESS.

---

### Tarea 9: Adaptador `JdbcPolicyRepository` con `sp_getapplock` vía `SELECT` y Prueba Real de Concurrencia

**Archivos:**
- Crear: `backend/src/main/java/com/turnoshift/policy/infrastructure/persistence/JdbcPolicyRepository.java`
- Crear: `backend/src/test/java/com/turnoshift/policy/infrastructure/persistence/JdbcPolicyRepositoryIT.java`

**Interfaces:**
- Implementa: `PolicyRepository`
- Tecnologías: `JdbcTemplate`, `TransactionSynchronizationManager`, `sp_getapplock`, `OUTPUT inserted.*`

- [ ] **Paso 9.1: Escribir la suite de integración `JdbcPolicyRepositoryIT` sobre Testcontainers (Red)**
  Crear `JdbcPolicyRepositoryIT.java` probando contra SQL Server 2022 real:
  1. `findCurrentPolicy` retorna `empty()` cuando la organización no tiene política configurada.
  2. `acquirePolicyLock` fuera de transacción lanza `PolicyDataAccessException`.
  3. `acquirePolicyLock` dentro de transacción adquiere el candado exitosamente (`@lockResult >= 0`).
  4. **Prueba real de contención de bloqueo ocupado:**
     - Conexión A abre transacción y adquiere `acquirePolicyLock(orgId)`.
     - Conexión B en hilo separado abre transacción e intenta adquirir el mismo bloqueo sobre el mismo `orgId`.
     - Se comprueba que la llamada en Conexión B arroja `PolicyOperationBusyException` debido a `@lockResult < 0` (sin simulación ni mocks).
     - Conexión A hace rollback/commit liberando el bloqueo.
  5. **Timestamps autoritativos e inserción:**
     - Llama a `insertPolicyVersion(draft)` omitiendo timestamps.
     - Verifica que la instancia retornada tiene `id`, `effectiveAt` y `createdAt` no nulos.
     - Consulta la fila directamente con JDBC y verifica que los timestamps en Java coinciden exactamente con los de la base de datos (con tolerancia de milisegundos de `DATETIMEOFFSET(3)`).
  6. Actualización atómica de puntero:
     - `updateCurrentPolicyVersion` actualiza `current_policy_version_id`, incrementa `lock_version` y actualiza `updated_at`.
     - Si `expectedCurrentPolicyVersionId` no coincide, arroja `PolicyConcurrentModificationException`.
  7. `findNextVersionNumber`: retorna 1 cuando no hay registros, y 2 tras la versión 1.
  8. `findHistory`: retorna el listado ordenado por `version_number DESC`.

- [ ] **Paso 9.2: Implementar `JdbcPolicyRepository` (Green)**
  - **Bloqueo `acquirePolicyLock` con `SELECT @res`:**
    ```java
    @Override
    public void acquirePolicyLock(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId no puede ser nulo");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new PolicyDataAccessException("Se requiere una transacción activa para adquirir el bloqueo de política.");
        }
        String resourceName = "TurnoShift:Policy:Org:" + organizationId;
        String sql = """
            DECLARE @res INT;
            EXEC @res = sp_getapplock
                @Resource = ?,
                @LockMode = 'Exclusive',
                @LockOwner = 'Transaction',
                @LockTimeout = 5000;
            SELECT @res;
            """;
        try {
            Integer lockResult = jdbcTemplate.queryForObject(sql, Integer.class, resourceName);
            if (lockResult == null || lockResult < 0) {
                throw new PolicyOperationBusyException(
                    "El recurso de políticas de la organización está ocupado por otra operación concurrente."
                );
            }
        } catch (DataAccessException e) {
            throw new PolicyDataAccessException("Error de acceso a datos al intentar adquirir el bloqueo de aplicación", e);
        }
    }
    ```
  - **Inserción con `OUTPUT inserted.*`:**
    ```java
    @Override
    public PolicyVersion insertPolicyVersion(NewPolicyVersion newVersion) {
        String sql = """
            INSERT INTO planning_policy_versions (
                organization_id, version_number, minimum_rest_minutes, weekly_warning_minutes,
                created_by_user_id, change_reason
            )
            OUTPUT inserted.id, inserted.organization_id, inserted.version_number,
                   inserted.minimum_rest_minutes, inserted.weekly_warning_minutes,
                   inserted.effective_at, inserted.created_by_user_id,
                   inserted.change_reason, inserted.created_at
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try {
            return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new PolicyVersion(
                    rs.getObject("id", UUID.class),
                    rs.getObject("organization_id", UUID.class),
                    rs.getInt("version_number"),
                    new PolicyThresholds(rs.getInt("minimum_rest_minutes"), rs.getInt("weekly_warning_minutes")),
                    rs.getObject("effective_at", java.time.OffsetDateTime.class).toInstant(),
                    rs.getObject("created_by_user_id", UUID.class),
                    new PolicyReason(rs.getString("change_reason")),
                    rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant()
                ),
                newVersion.organizationId(),
                newVersion.versionNumber(),
                newVersion.thresholds().minimumRestMinutes(),
                newVersion.thresholds().weeklyWarningMinutes(),
                newVersion.createdByUserId(),
                newVersion.changeReason().value()
            );
        } catch (DataAccessException e) {
            throw new PolicyDataAccessException("Error al insertar la versión de política", e);
        }
    }
    ```
  - **Actualización atómica de puntero:**
    ```sql
    UPDATE organizations
    SET current_policy_version_id = ?,
        lock_version = lock_version + 1,
        updated_at = SYSUTCDATETIME()
    WHERE id = ? AND (current_policy_version_id = ? OR (current_policy_version_id IS NULL AND ? IS NULL))
    ```
    Si las filas actualizadas son 0, lanzar `PolicyConcurrentModificationException`.

- [ ] **Paso 9.3: Ejecutar la prueba y comprobar éxito**
  Comando:
  `powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME = 'C:\Users\juan9\.jdks\temurin-21.0.10'; .\mvnw.cmd test '-Dtest=JdbcPolicyRepositoryIT' -B"`
  Resultado esperado: BUILD SUCCESS.

---

## 4. Comandos Futuros Focalizados de Verificación

Durante la fase de implementación de `I2.3-b`, los únicos comandos de Maven autorizados son:

```powershell
# 1. Pruebas unitarias de dominio (rápidas, sin Docker)
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
cmd /c ".\mvnw.cmd test -Dtest=PolicyThresholdsTest,PolicyReasonTest,ValidationFingerprintTest,PolicyDeltaTest -B"

# 2. Pruebas unitarias de casos de uso (rápidas, con mocks)
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
cmd /c ".\mvnw.cmd test -Dtest=GetCurrentPolicyUseCaseTest,GetPolicyHistoryUseCaseTest,PreviewPolicyChangeUseCaseTest,ActivatePolicyUseCaseTest -B"

# 3. Prueba de integración JDBC (Testcontainers SQL Server real)
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
cmd /c ".\mvnw.cmd test -Dtest=JdbcPolicyRepositoryIT -B"

# 4. Verificación integral de cierre de I2.3-b (todas las pruebas del subincremento)
$env:JAVA_HOME = "C:\Users\juan9\.jdks\temurin-21.0.10"
cmd /c ".\mvnw.cmd test -Dtest=PolicyThresholdsTest,PolicyReasonTest,ValidationFingerprintTest,PolicyDeltaTest,GetCurrentPolicyUseCaseTest,GetPolicyHistoryUseCaseTest,PreviewPolicyChangeUseCaseTest,ActivatePolicyUseCaseTest,JdbcPolicyRepositoryIT -B"
```

---

## 5. Matriz de Trazabilidad y Auto-Revisión con Documento 16

| Requisito en Documento 16 | Sección en Doc 16 | Tarea en Doc 18 | Estado de Cobertura |
| :--- | :--- | :--- | :--- |
| **Límites `minimumRestMinutes` (1..1440) y `weeklyWarningMinutes` (1..10080)** | §6 | Tarea 1 (`PolicyThresholds`) | **100% cubierto**. Restricciones de rango idénticas a SQL CHECK. |
| **Validación de Motivo Unicode y longitud 500** | §6 | Tarea 2 (`PolicyReason`) | **100% cubierto**. Comprobación exhaustiva `isWhitespace || isSpaceChar`. |
| **Cálculo de delta respecto a versión previa** | §7.3 | Tarea 3 (`PolicyDelta`) | **100% cubierto**. Soporta estado inicial `null` y deltas aritméticos. |
| **Token canónico `validationFingerprint` estricto (`[0-9a-f]{64}`, `equals`)** | §9 | Tarea 4 (`ValidationFingerprint`) | **100% cubierto**. Formato exacto, rechazo de mayúsculas/longitud y verificación estricta. |
| **Consulta de política actual e historial determinista** | §7.1, §7.2 | Tarea 6 (`GetCurrentPolicyUseCase`, `GetPolicyHistoryUseCase`) | **100% cubierto**. Historial ordenado por `version_number DESC`. |
| **Previsualización sin persistencia y comprobación de estado** | §7.3 | Tarea 7 (`PreviewPolicyChangeUseCase`) | **100% cubierto**. Evalúa concurrencia y calcula delta/huella sin mutar BD. |
| **Activación transaccional con avance $N+1$** | §7.4, §10 | Tarea 8 (`ActivatePolicyUseCase`) | **100% cubierto**. Bloqueo previo, validación de huella, versión $N+1$ y puntero. |
| **Serialización vía `sp_getapplock` con `SELECT @res`** | §10, §10.1 | Tarea 8 y 9 (`JdbcPolicyRepository`) | **100% cubierto**. Candado con timeout 5s, captura de `@lockResult` y prueba de contención real. |
| **Timestamps autoritativos desde base de datos** | §3, §10 | Tarea 5, 8 y 9 (`NewPolicyVersion`, `OUTPUT inserted.*`) | **100% cubierto**. Sin `Clock` en Java; BD asigna `SYSUTCDATETIME()`. |
| **Actualización atómica de `organizations`** | §10 paso 7 | Tarea 9 (`JdbcPolicyRepository`) | **100% cubierto**. Actualiza `current_policy_version_id`, `lock_version++` y `updated_at`. |
| **Exclusiones de alcance (HTTP, DTOs, CSRF, OpenAPI)** | §12.2, §12.3 | Todo el plan | **100% respetado**. Cero acoplamiento HTTP o OpenAPI en este subincremento. |
